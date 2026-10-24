package com.example.byokgpt.ui.chat

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.byokgpt.model.AiProvider
import com.example.byokgpt.model.ModelCatalog
import com.example.byokgpt.ui.components.ApiKeySetupDialog
import com.example.byokgpt.ui.components.ConversationDrawerContent
import com.example.byokgpt.ui.components.ModelPickerSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val conversations by viewModel.conversations.collectAsState()
    val currentConvId by viewModel.currentConversationId.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val bannerDismissed by viewModel.bannerDismissed.collectAsState()
    val appSettings by viewModel.settingsManager.settings.collectAsState()
    val pendingAttachments by viewModel.pendingAttachments.collectAsState()
    val pendingAttachment by viewModel.pendingAttachment.collectAsState()
    val attachmentError by viewModel.attachmentError.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var browserUrl by remember { mutableStateOf<String?>(null) }
    var providerToConfigure by remember { mutableStateOf(selectedProvider) }
    var showAttachmentMenu by remember { mutableStateOf(false) }

    val hasKeyForSelected = viewModel.secureKeyStorage.hasApiKey(selectedProvider)

    // Multi-Image Picker Launcher (up to 5 images)
    val multiImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        uris.forEach { uri -> viewModel.addPendingAttachment(uri) }
    }

    // PDF / Document Picker Launcher
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri -> viewModel.addPendingAttachment(uri) }
    }

    // Show error toast if attachment validation fails
    LaunchedEffect(attachmentError) {
        attachmentError?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            viewModel.clearAttachmentError()
        }
    }

    // Auto-scroll when messages change
    LaunchedEffect(messages.size, isStreaming) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawerContent(
                conversations = conversations,
                currentConversationId = currentConvId,
                onConversationSelected = { id ->
                    viewModel.selectConversation(id)
                    scope.launch { drawerState.close() }
                },
                onNewChatClicked = {
                    viewModel.startNewChat()
                    scope.launch { drawerState.close() }
                },
                onDeleteConversationClicked = { id ->
                    viewModel.deleteConversation(id)
                },
                onOpenSettingsClicked = {
                    scope.launch { drawerState.close() }
                    onNavigateToSettings()
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val modelObj = ModelCatalog.models.find {
                            it.id == selectedModel && it.provider == selectedProvider
                        }
                        val modelName = modelObj?.displayName ?: selectedModel

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { showModelPicker = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(id = selectedProvider.iconResId),
                                    contentDescription = selectedProvider.displayName,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = modelName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Select Model",
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Quick Language Chip
                            Box {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .clickable { showLanguageMenu = true }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = "Language",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when (appSettings.responseLanguage) {
                                            com.example.byokgpt.model.ResponseLanguage.AUTOMATIC -> "Auto"
                                            com.example.byokgpt.model.ResponseLanguage.TANGLISH -> "Tanglish"
                                            com.example.byokgpt.model.ResponseLanguage.TAMIL -> "தமிழ்"
                                            com.example.byokgpt.model.ResponseLanguage.ENGLISH -> "EN"
                                            com.example.byokgpt.model.ResponseLanguage.HINDI -> "हिन्दी"
                                            com.example.byokgpt.model.ResponseLanguage.MALAYALAM -> "മലയാളം"
                                            com.example.byokgpt.model.ResponseLanguage.KANNADA -> "ಕನ್ನಡ"
                                            com.example.byokgpt.model.ResponseLanguage.TELUGU -> "తెలుగు"
                                            com.example.byokgpt.model.ResponseLanguage.BENGALI -> "বাংলা"
                                            com.example.byokgpt.model.ResponseLanguage.MARATHI -> "मराठी"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                DropdownMenu(
                                    expanded = showLanguageMenu,
                                    onDismissRequest = { showLanguageMenu = false }
                                ) {
                                    com.example.byokgpt.model.ResponseLanguage.entries.forEach { lang ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = lang.displayName,
                                                    fontWeight = if (appSettings.responseLanguage == lang) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (appSettings.responseLanguage == lang) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            leadingIcon = {
                                                if (appSettings.responseLanguage == lang) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                } else {
                                                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            },
                                            onClick = {
                                                viewModel.settingsManager.updateResponseLanguage(lang)
                                                showLanguageMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Chat History",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { browserUrl = "https://www.google.com" }) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Google Web Search",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { viewModel.startNewChat() }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.new_chat),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                // Bottom Composer
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Multi-Attachment Carousel (Claude style)
                        if (pendingAttachments.isNotEmpty()) {
                            AttachmentCarousel(
                                attachments = pendingAttachments,
                                onRemoveAttachment = { viewModel.removePendingAttachment(it) },
                                onClearAll = { viewModel.clearPendingAttachments() }
                            )
                        } else if (pendingAttachment != null) {
                            AttachmentCarousel(
                                attachments = listOf(pendingAttachment!!),
                                onRemoveAttachment = { viewModel.removePendingAttachment() },
                                onClearAll = { viewModel.removePendingAttachment() }
                            )
                        }

                        // Input Field Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Attachment Picker Anchor
                            Box {
                                IconButton(
                                    onClick = { showAttachmentMenu = true },
                                    enabled = !isStreaming
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = "Attach files (Max 10MB)",
                                        tint = if (pendingAttachments.isNotEmpty() || pendingAttachment != null) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                DropdownMenu(
                                    expanded = showAttachmentMenu,
                                    onDismissRequest = { showAttachmentMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Photos & Images (Max 5, <= 10MB)") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Image, contentDescription = null)
                                        },
                                        onClick = {
                                            showAttachmentMenu = false
                                            multiImagePickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("PDF Documents (<= 10MB)") },
                                        leadingIcon = {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                        },
                                        onClick = {
                                            showAttachmentMenu = false
                                            documentPickerLauncher.launch("application/pdf")
                                        }
                                    )
                                }
                            }

                            // Google Web Search Grounding Toggle Button
                            IconButton(
                                onClick = {
                                    val newSearch = !appSettings.webSearch
                                    viewModel.settingsManager.updateWebSearch(newSearch)
                                    Toast.makeText(
                                        context,
                                        if (newSearch) "Web Search Grounding ON" else "Web Search OFF",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (appSettings.webSearch) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = "Google Web Search Toggle",
                                    tint = if (appSettings.webSearch) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = {
                                    Text(
                                        text = if (appSettings.webSearch) "Search or ask anything..." else stringResource(R.string.chat_input_hint),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("message_input"),
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        val hasAttachments = pendingAttachments.isNotEmpty() || pendingAttachment != null
                                        if ((inputText.isNotBlank() || hasAttachments) && !isStreaming) {
                                            if (!hasKeyForSelected) {
                                                providerToConfigure = selectedProvider
                                                showApiKeyDialog = true
                                                Toast.makeText(
                                                    context,
                                                    "Configure your ${selectedProvider.displayName} API Key to send messages.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@KeyboardActions
                                            }
                                            val textToSend = inputText
                                            inputText = ""
                                            viewModel.sendMessage(textToSend)
                                        }
                                    }
                                )
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            val canSend = (inputText.isNotBlank() || pendingAttachments.isNotEmpty() || pendingAttachment != null) && !isStreaming

                            // Send / Stop Toggle Button
                            IconButton(
                                onClick = {
                                    if (isStreaming) {
                                        viewModel.stopGeneration()
                                    } else if (canSend) {
                                        if (!hasKeyForSelected) {
                                            providerToConfigure = selectedProvider
                                            showApiKeyDialog = true
                                            Toast.makeText(
                                                context,
                                                "Configure your ${selectedProvider.displayName} API Key to send messages.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@IconButton
                                        }
                                        val textToSend = inputText
                                        inputText = ""
                                        viewModel.sendMessage(textToSend)
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isStreaming) MaterialTheme.colorScheme.error
                                        else if (canSend) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .testTag("send_button")
                            ) {
                                Icon(
                                    imageVector = if (isStreaming) Icons.Default.Stop
                                    else Icons.AutoMirrored.Filled.Send,
                                    contentDescription = if (isStreaming) stringResource(R.string.stop) else stringResource(R.string.send),
                                    tint = if (isStreaming || canSend) Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Setup API Key Callout Banner if no key configured
                AnimatedVisibility(
                    visible = !hasKeyForSelected && !bannerDismissed,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Setup ${selectedProvider.displayName} Key",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(R.string.setup_key_banner_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            TextButton(
                                onClick = {
                                    providerToConfigure = selectedProvider
                                    showApiKeyDialog = true
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.setup_key_button),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.dismissBanner() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Chat Messages List or Empty State
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.app_logo),
                                    contentDescription = "BYOKGPT Logo",
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(CircleShape)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(R.string.welcome_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.welcome_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            val suggestions = listOf(
                                "Explain quantum computing in simple terms",
                                "Write a clean Kotlin coroutine function",
                                "Help me draft a concise professional email"
                            )

                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                suggestions.forEach { suggestion ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant,
                                                RoundedCornerShape(16.dp)
                                            )
                                            .clickable {
                                                inputText = suggestion
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = suggestion,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            ChatMessageBubble(
                                message = message,
                                isStreaming = isStreaming,
                                onRetryClicked = { viewModel.retryLastMessage() },
                                onSwitchProviderClicked = { showModelPicker = true },
                                onDeleteClicked = {
                                    scope.launch { viewModel.repository.deleteMessage(message.id) }
                                },
                                onUrlClicked = { url -> browserUrl = url },
                                onConfigureKeyClicked = { provider ->
                                    providerToConfigure = provider
                                    showApiKeyDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Model Picker Bottom Sheet
    if (showModelPicker) {
        ModelPickerSheet(
            currentProvider = selectedProvider,
            currentModel = selectedModel,
            hasKeyForProvider = { viewModel.secureKeyStorage.hasApiKey(it) },
            onModelSelected = { provider, model ->
                viewModel.setProvider(provider)
                viewModel.setModel(model)
            },
            onConfigureKeyRequested = { provider ->
                providerToConfigure = provider
                showApiKeyDialog = true
            },
            onDismiss = { showModelPicker = false }
        )
    }

    // API Key Setup Dialog
    if (showApiKeyDialog) {
        ApiKeySetupDialog(
            initialProvider = providerToConfigure,
            secureKeyStorage = viewModel.secureKeyStorage,
            onDismiss = { showApiKeyDialog = false },
            onSaved = { provider ->
                viewModel.setProvider(provider)
            }
        )
    }

    // In-App Browser Sheet
    if (browserUrl != null) {
        SimpleBrowserSheet(
            initialUrl = browserUrl!!,
            onDismiss = { browserUrl = null }
        )
    }
}
