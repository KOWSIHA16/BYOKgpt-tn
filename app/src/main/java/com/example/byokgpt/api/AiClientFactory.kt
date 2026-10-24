package com.example.byokgpt.api

import com.example.byokgpt.model.AiProvider

object AiClientFactory {
    private val geminiClient = GeminiClient()
    private val claudeClient = ClaudeClient()
    private val openAiClient = OpenAiClient()

    fun getClient(provider: AiProvider): AiClient {
        return when (provider) {
            AiProvider.GEMINI -> geminiClient
            AiProvider.CLAUDE -> claudeClient
            AiProvider.OPENAI -> openAiClient
        }
    }
}
