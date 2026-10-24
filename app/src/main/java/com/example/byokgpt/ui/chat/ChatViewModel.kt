package com.example.byokgpt.ui.chat

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.byokgpt.api.AiClientFactory
import com.example.byokgpt.data.AppDatabase
import com.example.byokgpt.data.ChatRepository
import com.example.byokgpt.data.ConversationEntity
import com.example.byokgpt.data.MessageEntity
import com.example.byokgpt.data.SettingsManager
import com.example.byokgpt.model.AiProvider
import com.example.byokgpt.model.Attachment
import com.example.byokgpt.model.AttachmentValidator
import com.example.byokgpt.model.MessageRole
import com.example.byokgpt.model.MessageStatus
import com.example.byokgpt.model.ProviderConfig
import com.example.byokgpt.model.StreamChunk
import com.example.byokgpt.model.TurnTimings
import com.example.byokgpt.security.LogSanitizer
import com.example.byokgpt.security.SecureKeyStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Call
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    val secureKeyStorage = SecureKeyStorage(application)
    val settingsManager = SettingsManager(application)
    private val database = AppDatabase.getDatabase(application)
    val repository = ChatRepository(database.chatDao())

    val conversations: StateFlow<List<ConversationEntity>> = repository.conversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val _currentMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val currentMessages: StateFlow<List<MessageEntity>> = _currentMessages.asStateFlow()

    private val _selectedProvider = MutableStateFlow(secureKeyStorage.getCurrentProvider())
    val selectedProvider: StateFlow<AiProvider> = _selectedProvider.asStateFlow()

    private val _selectedModel = MutableStateFlow(
        secureKeyStorage.getSelectedModel(secureKeyStorage.getCurrentProvider())
    )
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _streamingMessageId = MutableStateFlow<String?>(null)
    val streamingMessageId: StateFlow<String?> = _streamingMessageId.asStateFlow()

    private val _bannerDismissed = MutableStateFlow(false)
    val bannerDismissed: StateFlow<Boolean> = _bannerDismissed.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val pendingAttachments: StateFlow<List<Attachment>> = _pendingAttachments.asStateFlow()

    private val _pendingAttachment = MutableStateFlow<Attachment?>(null)
    val pendingAttachment: StateFlow<Attachment?> = _pendingAttachment.asStateFlow()

    private val _attachmentError = MutableStateFlow<String?>(null)
    val attachmentError: StateFlow<String?> = _attachmentError.asStateFlow()

    // Latency instrumentation per turn (T0..T5)
    private val _latestTimings = MutableStateFlow<TurnTimings?>(null)
    val latestTimings: StateFlow<TurnTimings?> = _latestTimings.asStateFlow()

    private var activeStreamJob: Job? = null
    private var activeCall: Call? = null
    private var currentTurnId: String? = null
    private var messageCollectionJob: Job? = null

    init {
        viewModelScope.launch {
            conversations.collect { convList ->
                if (_currentConversationId.value == null && convList.isNotEmpty()) {
                    selectConversation(convList.first().id)
                }
            }
        }
    }

    fun dismissBanner() {
        _bannerDismissed.value = true
    }

    fun clearAttachmentError() {
        _attachmentError.value = null
    }

    fun addPendingAttachment(uri: Uri) {
        val current = _pendingAttachments.value
        val result = AttachmentValidator.loadAttachmentFromUri(getApplication(), uri)
        if (result.isSuccess) {
            val loaded = result.getOrNull()!!
            val validation = AttachmentValidator.validateCumulative(current, loaded)
            when (validation) {
                is com.example.byokgpt.model.AttachmentValidationResult.Valid -> {
                    _pendingAttachments.update { it + loaded }
                    _pendingAttachment.value = _pendingAttachments.value.firstOrNull()
                    _attachmentError.value = null
                }
                is com.example.byokgpt.model.AttachmentValidationResult.FileTooLarge -> {
                    _attachmentError.value = "Cumulative attachment size exceeds 10MB limit."
                }
                is com.example.byokgpt.model.AttachmentValidationResult.UnsupportedType -> {
                    _attachmentError.value = "Unsupported format: ${validation.mimeType}."
                }
                is com.example.byokgpt.model.AttachmentValidationResult.Error -> {
                    _attachmentError.value = validation.message
                }
            }
        } else {
            _attachmentError.value = result.exceptionOrNull()?.message ?: "Failed to read attachment."
        }
    }

    fun removePendingAttachment(attachment: Attachment) {
        _pendingAttachments.update { list -> list.filter { it.uriString != attachment.uriString } }
        _pendingAttachment.value = _pendingAttachments.value.firstOrNull()
    }

    fun clearPendingAttachments() {
        _pendingAttachments.value = emptyList()
        _pendingAttachment.value = null
        _attachmentError.value = null
    }

    fun setPendingAttachment(uri: Uri) {
        addPendingAttachment(uri)
    }

    fun removePendingAttachment() {
        clearPendingAttachments()
    }

    fun selectConversation(id: String) {
        if (_currentConversationId.value == id) return
        stopGeneration()
        _currentConversationId.value = id

        messageCollectionJob?.cancel()
        messageCollectionJob = viewModelScope.launch {
            repository.getMessages(id).collect { roomMessages ->
                val streamingId = _streamingMessageId.value
                if (streamingId != null) {
                    val inFlight = _currentMessages.value.find { it.id == streamingId }
                    if (inFlight != null) {
                        val merged = roomMessages.filter { it.id != streamingId } + inFlight
                        _currentMessages.value = merged.sortedBy { it.timestamp }
                        return@collect
                    }
                }
                _currentMessages.value = roomMessages
            }
        }
    }

    fun startNewChat(provider: AiProvider = _selectedProvider.value, model: String = _selectedModel.value) {
        stopGeneration()
        viewModelScope.launch {
            val newConv = repository.createNewConversation("New Chat", provider, model)
            selectConversation(newConv.id)
        }
    }

    fun setProvider(provider: AiProvider) {
        _selectedProvider.value = provider
        secureKeyStorage.saveCurrentProvider(provider)
        val model = secureKeyStorage.getSelectedModel(provider)
        _selectedModel.value = model
    }

    fun setModel(modelId: String) {
        _selectedModel.value = modelId
        secureKeyStorage.saveSelectedModel(_selectedProvider.value, modelId)
    }

    /**
     * PRIORITY 1: Display user message immediately on tap.
     * Appends user message and assistant placeholder directly to UI state in-memory,
     * clearing the input and rendering instantly (<10ms) without waiting for DB or network.
     */
    fun sendMessage(promptText: String) {
        val t0 = System.currentTimeMillis()
        if (_isStreaming.value) return
        val trimmed = promptText.trim()
        val attachments = _pendingAttachments.value
        val primaryAttachment = _pendingAttachment.value ?: attachments.firstOrNull()
        if (trimmed.isEmpty() && attachments.isEmpty() && primaryAttachment == null) return

        // 1. Clear input attachments immediately
        clearPendingAttachments()

        val provider = _selectedProvider.value
        val model = _selectedModel.value
        val existingConvId = _currentConversationId.value
        val userMsgId = UUID.randomUUID().toString()
        val assistantMsgId = UUID.randomUUID().toString()
        val turnId = UUID.randomUUID().toString()
        currentTurnId = turnId

        val displayAttachmentName = if (attachments.size > 1) {
            "${attachments.size} files: " + attachments.joinToString(", ") { it.fileName }
        } else {
            primaryAttachment?.fileName
        }

        // 2. Build user message entity
        val userMessage = MessageEntity(
            id = userMsgId,
            conversationId = existingConvId ?: "temp_conv",
            role = MessageRole.USER.name,
            content = trimmed,
            timestamp = t0,
            status = MessageStatus.SUCCESS.name,
            provider = provider.name,
            modelId = model,
            attachmentUri = primaryAttachment?.uriString,
            attachmentName = displayAttachmentName,
            attachmentMime = primaryAttachment?.mimeType,
            attachmentBase64 = primaryAttachment?.base64Data
        )

        // 3. Build assistant placeholder entity
        val assistantMessage = MessageEntity(
            id = assistantMsgId,
            conversationId = existingConvId ?: "temp_conv",
            role = MessageRole.ASSISTANT.name,
            content = "",
            timestamp = t0 + 1,
            status = MessageStatus.STREAMING.name,
            provider = provider.name,
            modelId = model
        )

        // 4. IMMEDIATELY update in-memory state: user message and streaming indicator are visible NOW!
        _currentMessages.update { it + userMessage + assistantMessage }
        _isStreaming.value = true
        _streamingMessageId.value = assistantMsgId

        val t1 = System.currentTimeMillis()
        val timings = TurnTimings(
            turnId = turnId,
            provider = provider.displayName,
            model = model,
            t0SendTappedMs = t0,
            t1UserVisibleMs = t1
        )
        _latestTimings.value = timings
        Log.i("BYOKGPT_LATENCY", "T0->T1 User visible in ${timings.t0ToT1Ms}ms")

        // 5. Asynchronously execute database storage and network pipeline on background thread
        activeStreamJob = viewModelScope.launch(Dispatchers.IO) {
            val effectiveConvId = if (existingConvId == null) {
                val newConv = repository.createNewConversation("New Chat", provider, model)
                _currentConversationId.value = newConv.id
                // Start Room collection for the new conversation in background
                viewModelScope.launch {
                    repository.getMessages(newConv.id).collect { roomMessages ->
                        val streamingId = _streamingMessageId.value
                        if (streamingId != null) {
                            val inFlight = _currentMessages.value.find { it.id == streamingId }
                            if (inFlight != null) {
                                val merged = roomMessages.filter { it.id != streamingId } + inFlight
                                _currentMessages.value = merged.sortedBy { it.timestamp }
                                return@collect
                            }
                        }
                        _currentMessages.value = roomMessages
                    }
                }
                newConv.id
            } else {
                existingConvId
            }

            // Save user message to database so it is NEVER lost, even if network fails
            val finalUserMsg = userMessage.copy(conversationId = effectiveConvId)
            repository.insertMessageDirect(finalUserMsg)

            // Validate API key
            val apiKey = secureKeyStorage.getApiKey(provider)
            if (apiKey.isBlank()) {
                val errorMsg = "No API key configured for ${provider.displayName}. Tap Configure Key or Switch Provider below."
                timings.errorMessage = errorMsg
                timings.t5CompletedMs = System.currentTimeMillis()
                _latestTimings.value = timings
                Log.e("BYOKGPT_LATENCY", timings.toLogString())

                val errorEntity = assistantMessage.copy(
                    conversationId = effectiveConvId,
                    status = MessageStatus.ERROR.name,
                    errorMessage = errorMsg
                )
                _currentMessages.update { list ->
                    list.map { if (it.id == assistantMsgId) errorEntity else it }
                }
                repository.insertMessageDirect(errorEntity)
                _isStreaming.value = false
                _streamingMessageId.value = null
                return@launch
            }

            // T2: Network request begins
            val t2 = System.currentTimeMillis()
            timings.t2NetworkStartMs = t2

            // Build clean history: take last 20 messages for context, excluding in-flight assistant
            val rawHistory = repository.getMessagesSnapshot(effectiveConvId)
            val cleanHistory = rawHistory.filter { msg ->
                msg.id != assistantMsgId &&
                (msg.content.isNotBlank() || !msg.attachmentBase64.isNullOrEmpty()) &&
                msg.status != MessageStatus.ERROR.name
            }

            val client = AiClientFactory.getClient(provider)
            val config = ProviderConfig(
                provider = provider,
                apiKey = apiKey,
                customEndpoint = secureKeyStorage.getCustomEndpoint(provider),
                selectedModel = model
            )
            val systemPrompt = buildSystemPrompt()
            val inFlightContent = StringBuilder()

            try {
                client.streamChat(
                    messages = cleanHistory,
                    systemPrompt = systemPrompt,
                    config = config,
                    model = model,
                    onCallCreated = { call -> activeCall = call }
                ).collect { chunk ->
                    if (currentTurnId != turnId) return@collect

                    when (chunk) {
                        is StreamChunk.FirstBytes -> {
                            if (timings.t3FirstBytesMs == 0L) {
                                timings.t3FirstBytesMs = System.currentTimeMillis()
                            }
                        }
                        is StreamChunk.Delta -> {
                            if (timings.t4FirstTextMs == 0L) {
                                timings.t4FirstTextMs = System.currentTimeMillis()
                            }
                            inFlightContent.append(chunk.text)
                            val currentText = inFlightContent.toString()

                            // PRIORITY 3: In-memory streaming update. Zero Room SQLite disk transactions during token deltas!
                            _currentMessages.update { list ->
                                list.map { msg ->
                                    if (msg.id == assistantMsgId) {
                                        msg.copy(content = currentText, status = MessageStatus.STREAMING.name)
                                    } else msg
                                }
                            }
                        }
                        is StreamChunk.Done -> {
                            val t5 = System.currentTimeMillis()
                            timings.t5CompletedMs = t5
                            _latestTimings.value = timings
                            Log.i("BYOKGPT_LATENCY", timings.toLogString())

                            val finalText = chunk.fullText.ifEmpty { inFlightContent.toString() }
                            val successEntity = assistantMessage.copy(
                                conversationId = effectiveConvId,
                                content = finalText,
                                status = MessageStatus.SUCCESS.name
                            )
                            _currentMessages.update { list ->
                                list.map { msg ->
                                    if (msg.id == assistantMsgId) successEntity else msg
                                }
                            }
                            // Persist to Room SQLite ONCE when stream finishes
                            repository.insertMessageDirect(successEntity)
                        }
                        is StreamChunk.Error -> {
                            val t5 = System.currentTimeMillis()
                            timings.t5CompletedMs = t5
                            timings.errorMessage = chunk.message
                            _latestTimings.value = timings
                            Log.e("BYOKGPT_LATENCY", timings.toLogString())

                            val errorEntity = assistantMessage.copy(
                                conversationId = effectiveConvId,
                                content = inFlightContent.toString(),
                                status = MessageStatus.ERROR.name,
                                errorMessage = chunk.message
                            )
                            _currentMessages.update { list ->
                                list.map { msg ->
                                    if (msg.id == assistantMsgId) errorEntity else msg
                                }
                            }
                            repository.insertMessageDirect(errorEntity)
                        }
                    }
                }
            } catch (e: Exception) {
                if (currentTurnId == turnId && !activeCall?.isCanceled().let { it == true }) {
                    val errorMsg = LogSanitizer.sanitizeLog(e.localizedMessage ?: "Network connection error.")
                    timings.t5CompletedMs = System.currentTimeMillis()
                    timings.errorMessage = errorMsg
                    _latestTimings.value = timings
                    Log.e("BYOKGPT_LATENCY", timings.toLogString(), e)

                    val errorEntity = assistantMessage.copy(
                        conversationId = effectiveConvId,
                        content = inFlightContent.toString(),
                        status = MessageStatus.ERROR.name,
                        errorMessage = errorMsg
                    )
                    _currentMessages.update { list ->
                        list.map { msg ->
                            if (msg.id == assistantMsgId) errorEntity else msg
                        }
                    }
                    repository.insertMessageDirect(errorEntity)
                }
            } finally {
                if (currentTurnId == turnId) {
                    _isStreaming.value = false
                    _streamingMessageId.value = null
                    activeCall = null
                }
            }
        }
    }

    fun stopGeneration() {
        val currentId = _streamingMessageId.value
        val convId = _currentConversationId.value

        currentTurnId = null
        activeCall?.cancel()
        activeCall = null
        activeStreamJob?.cancel()
        _isStreaming.value = false
        _streamingMessageId.value = null

        if (currentId != null && convId != null) {
            _currentMessages.update { list ->
                list.map { msg ->
                    if (msg.id == currentId && msg.status == MessageStatus.STREAMING.name) {
                        msg.copy(status = MessageStatus.CANCELLED.name)
                    } else msg
                }
            }
            viewModelScope.launch(Dispatchers.IO) {
                val currentList = repository.getMessagesSnapshot(convId)
                val targetMsg = currentList.find { it.id == currentId }
                if (targetMsg != null && targetMsg.status == MessageStatus.STREAMING.name) {
                    repository.updateMessage(targetMsg.copy(status = MessageStatus.CANCELLED.name))
                }
            }
        }
    }

    fun retryLastMessage() {
        if (_isStreaming.value) return
        val convId = _currentConversationId.value ?: return

        val t0 = System.currentTimeMillis()
        val provider = _selectedProvider.value
        val model = _selectedModel.value

        // Remove last failed assistant message from in-memory and database
        val currentList = _currentMessages.value
        val lastMsg = currentList.lastOrNull()
        if (lastMsg != null && lastMsg.role == MessageRole.ASSISTANT.name && lastMsg.status == MessageStatus.ERROR.name) {
            _currentMessages.update { it.filter { m -> m.id != lastMsg.id } }
            viewModelScope.launch(Dispatchers.IO) {
                repository.deleteMessage(lastMsg.id)
            }
        }

        val assistantMsgId = UUID.randomUUID().toString()
        val assistantMessage = MessageEntity(
            id = assistantMsgId,
            conversationId = convId,
            role = MessageRole.ASSISTANT.name,
            content = "",
            timestamp = t0 + 1,
            status = MessageStatus.STREAMING.name,
            provider = provider.name,
            modelId = model
        )

        _currentMessages.update { it + assistantMessage }
        _isStreaming.value = true
        _streamingMessageId.value = assistantMsgId

        val t1 = System.currentTimeMillis()
        val turnId = UUID.randomUUID().toString()
        currentTurnId = turnId

        val timings = TurnTimings(
            turnId = turnId,
            provider = provider.displayName,
            model = model,
            t0SendTappedMs = t0,
            t1UserVisibleMs = t1
        )
        _latestTimings.value = timings

        activeStreamJob = viewModelScope.launch(Dispatchers.IO) {
            val apiKey = secureKeyStorage.getApiKey(provider)
            if (apiKey.isBlank()) {
                val errorMsg = "No API key configured for ${provider.displayName}."
                val errorEntity = assistantMessage.copy(
                    status = MessageStatus.ERROR.name,
                    errorMessage = errorMsg
                )
                _currentMessages.update { list -> list.map { if (it.id == assistantMsgId) errorEntity else it } }
                repository.insertMessageDirect(errorEntity)
                _isStreaming.value = false
                _streamingMessageId.value = null
                return@launch
            }

            timings.t2NetworkStartMs = System.currentTimeMillis()
            val rawHistory = repository.getMessagesSnapshot(convId)
            val cleanHistory = rawHistory.filter { msg ->
                msg.id != assistantMsgId &&
                (msg.content.isNotBlank() || !msg.attachmentBase64.isNullOrEmpty()) &&
                msg.status != MessageStatus.ERROR.name
            }

            val client = AiClientFactory.getClient(provider)
            val config = ProviderConfig(
                provider = provider,
                apiKey = apiKey,
                customEndpoint = secureKeyStorage.getCustomEndpoint(provider),
                selectedModel = model
            )
            val systemPrompt = buildSystemPrompt()
            val inFlightContent = StringBuilder()

            try {
                client.streamChat(
                    messages = cleanHistory,
                    systemPrompt = systemPrompt,
                    config = config,
                    model = model,
                    onCallCreated = { call -> activeCall = call }
                ).collect { chunk ->
                    if (currentTurnId != turnId) return@collect

                    when (chunk) {
                        is StreamChunk.FirstBytes -> {
                            if (timings.t3FirstBytesMs == 0L) timings.t3FirstBytesMs = System.currentTimeMillis()
                        }
                        is StreamChunk.Delta -> {
                            if (timings.t4FirstTextMs == 0L) timings.t4FirstTextMs = System.currentTimeMillis()
                            inFlightContent.append(chunk.text)
                            val currentText = inFlightContent.toString()
                            _currentMessages.update { list ->
                                list.map { if (it.id == assistantMsgId) it.copy(content = currentText) else it }
                            }
                        }
                        is StreamChunk.Done -> {
                            timings.t5CompletedMs = System.currentTimeMillis()
                            _latestTimings.value = timings
                            Log.i("BYOKGPT_LATENCY", timings.toLogString())

                            val finalText = chunk.fullText.ifEmpty { inFlightContent.toString() }
                            val successEntity = assistantMessage.copy(
                                content = finalText,
                                status = MessageStatus.SUCCESS.name
                            )
                            _currentMessages.update { list ->
                                list.map { if (it.id == assistantMsgId) successEntity else it }
                            }
                            repository.insertMessageDirect(successEntity)
                        }
                        is StreamChunk.Error -> {
                            timings.t5CompletedMs = System.currentTimeMillis()
                            timings.errorMessage = chunk.message
                            _latestTimings.value = timings
                            Log.e("BYOKGPT_LATENCY", timings.toLogString())

                            val errorEntity = assistantMessage.copy(
                                content = inFlightContent.toString(),
                                status = MessageStatus.ERROR.name,
                                errorMessage = chunk.message
                            )
                            _currentMessages.update { list ->
                                list.map { if (it.id == assistantMsgId) errorEntity else it }
                            }
                            repository.insertMessageDirect(errorEntity)
                        }
                    }
                }
            } catch (e: Exception) {
                if (currentTurnId == turnId && !activeCall?.isCanceled().let { it == true }) {
                    val errorMsg = LogSanitizer.sanitizeLog(e.localizedMessage ?: "Network connection error.")
                    timings.t5CompletedMs = System.currentTimeMillis()
                    timings.errorMessage = errorMsg
                    _latestTimings.value = timings
                    val errorEntity = assistantMessage.copy(
                        status = MessageStatus.ERROR.name,
                        errorMessage = errorMsg
                    )
                    _currentMessages.update { list -> list.map { if (it.id == assistantMsgId) errorEntity else it } }
                    repository.insertMessageDirect(errorEntity)
                }
            } finally {
                if (currentTurnId == turnId) {
                    _isStreaming.value = false
                    _streamingMessageId.value = null
                    activeCall = null
                }
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_currentConversationId.value == id) {
                val remaining = conversations.value.filter { it.id != id }
                if (remaining.isNotEmpty()) {
                    selectConversation(remaining.first().id)
                } else {
                    _currentConversationId.value = null
                    _currentMessages.value = emptyList()
                }
            }
        }
    }

    private fun buildSystemPrompt(): String? {
        val customInstructions = settingsManager.settings.value.customInstructions.trim()
        val userName = settingsManager.settings.value.userName.trim()
        val responseLanguage = settingsManager.settings.value.responseLanguage
        val parts = mutableListOf<String>()

        if (userName.isNotEmpty() && userName != "User") {
            parts.add("You are addressing the user named $userName.")
        }
        if (customInstructions.isNotEmpty()) {
            parts.add("Custom user instructions:\n$customInstructions")
        }
        if (responseLanguage != com.example.byokgpt.model.ResponseLanguage.AUTOMATIC) {
            parts.add("CRITICAL LANGUAGE INSTRUCTION: ${responseLanguage.promptInstruction}")
        }
        if (settingsManager.settings.value.fastAnswers) {
            parts.add("Provide direct, concise, and structured answers.")
        }
        if (settingsManager.settings.value.webSearch) {
            parts.add("Web Search Mode: Ground answers with verified facts, accurate dates, authoritative source references, and recommended search queries where applicable.")
        }
        return if (parts.isNotEmpty()) parts.joinToString("\n\n") else null
    }

    suspend fun testAndSaveProviderStatus(provider: AiProvider): Result<String> {
        val apiKey = secureKeyStorage.getApiKey(provider)
        if (apiKey.isBlank()) {
            secureKeyStorage.saveProviderStatus(
                provider,
                com.example.byokgpt.model.ProviderConnectionStatus.NOT_CONFIGURED,
                "No API key entered"
            )
            return Result.failure(IllegalArgumentException("No API key configured for ${provider.displayName}"))
        }
        secureKeyStorage.saveProviderStatus(
            provider,
            com.example.byokgpt.model.ProviderConnectionStatus.CHECKING,
            "Testing credentials..."
        )
        val client = AiClientFactory.getClient(provider)
        val config = ProviderConfig(
            provider = provider,
            apiKey = apiKey,
            customEndpoint = secureKeyStorage.getCustomEndpoint(provider),
            selectedModel = secureKeyStorage.getSelectedModel(provider)
        )
        val result = client.testConnection(config, config.selectedModel)
        if (result.isSuccess) {
            val msg = result.getOrNull() ?: "Connected and verified"
            secureKeyStorage.saveProviderStatus(
                provider,
                com.example.byokgpt.model.ProviderConnectionStatus.CONNECTED,
                msg
            )
        } else {
            val err = result.exceptionOrNull()?.message ?: "Connection failed"
            secureKeyStorage.saveProviderStatus(
                provider,
                com.example.byokgpt.model.ProviderConnectionStatus.FAILED,
                err
            )
        }
        return result
    }
}
