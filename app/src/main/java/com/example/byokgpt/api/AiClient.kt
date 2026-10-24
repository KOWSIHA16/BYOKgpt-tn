package com.example.byokgpt.api

import com.example.byokgpt.data.MessageEntity
import com.example.byokgpt.model.ProviderConfig
import com.example.byokgpt.model.StreamChunk
import kotlinx.coroutines.flow.Flow
import okhttp3.Call

interface AiClient {
    fun streamChat(
        messages: List<MessageEntity>,
        systemPrompt: String?,
        config: ProviderConfig,
        model: String,
        onCallCreated: ((Call) -> Unit)? = null
    ): Flow<StreamChunk>

    suspend fun testConnection(config: ProviderConfig, model: String): Result<String>
}
