package com.example.byokgpt.data

import com.example.byokgpt.model.AiProvider
import com.example.byokgpt.model.Attachment
import com.example.byokgpt.model.MessageRole
import com.example.byokgpt.model.MessageStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChatRepository(private val chatDao: ChatDao) {

    val conversations: Flow<List<ConversationEntity>> = chatDao.getAllConversations()
    val totalMessageCount: Flow<Int> = chatDao.getTotalMessageCount()

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> =
        chatDao.getMessagesForConversation(conversationId)

    suspend fun getMessagesSnapshot(conversationId: String): List<MessageEntity> =
        chatDao.getMessagesSnapshot(conversationId)

    suspend fun createNewConversation(
        title: String = "New Chat",
        provider: AiProvider = AiProvider.GEMINI,
        modelId: String = provider.defaultModel
    ): ConversationEntity {
        val newConv = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            provider = provider.name,
            modelId = modelId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        chatDao.insertConversation(newConv)
        return newConv
    }

    suspend fun saveMessage(
        conversationId: String,
        role: MessageRole,
        content: String,
        provider: AiProvider,
        modelId: String,
        status: MessageStatus = MessageStatus.SUCCESS,
        errorMessage: String? = null,
        messageId: String = UUID.randomUUID().toString(),
        attachment: Attachment? = null
    ): MessageEntity {
        val message = MessageEntity(
            id = messageId,
            conversationId = conversationId,
            role = role.name,
            content = content,
            timestamp = System.currentTimeMillis(),
            status = status.name,
            errorMessage = errorMessage,
            provider = provider.name,
            modelId = modelId,
            attachmentUri = attachment?.uriString,
            attachmentName = attachment?.fileName,
            attachmentMime = attachment?.mimeType,
            attachmentBase64 = attachment?.base64Data
        )
        chatDao.insertMessage(message)

        // Update conversation timestamp and title if it's the first message
        val conv = chatDao.getConversationById(conversationId)
        if (conv != null) {
            val updatedTitle = if (conv.title == "New Chat" && role == MessageRole.USER) {
                if (content.isNotBlank()) {
                    if (content.length > 32) content.take(32) + "…" else content
                } else attachment?.fileName ?: "New Chat"
            } else conv.title

            chatDao.updateConversation(
                conv.copy(
                    title = updatedTitle,
                    updatedAt = System.currentTimeMillis(),
                    provider = provider.name,
                    modelId = modelId
                )
            )
        }
        return message
    }

    suspend fun insertMessageDirect(message: MessageEntity) {
        chatDao.insertMessage(message)
        val conv = chatDao.getConversationById(message.conversationId)
        if (conv != null) {
            val updatedTitle = if (conv.title == "New Chat" && message.role == MessageRole.USER.name) {
                if (message.content.isNotBlank()) {
                    if (message.content.length > 32) message.content.take(32) + "…" else message.content
                } else message.attachmentName ?: "New Chat"
            } else conv.title

            chatDao.updateConversation(
                conv.copy(
                    title = updatedTitle,
                    updatedAt = System.currentTimeMillis(),
                    provider = message.provider,
                    modelId = message.modelId
                )
            )
        }
    }

    suspend fun updateMessage(message: MessageEntity) {
        chatDao.updateMessage(message)
    }

    suspend fun deleteConversation(conversationId: String) {
        chatDao.deleteConversation(conversationId)
    }

    suspend fun deleteMessage(messageId: String) {
        chatDao.deleteMessage(messageId)
    }

    suspend fun clearAll() {
        chatDao.clearAllConversations()
    }
}
