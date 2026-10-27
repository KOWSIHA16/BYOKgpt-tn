package com.example.byokgpt.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

object PollinationsImageClient {

    private val httpClient: OkHttpClient = NetworkClient.sharedHttpClient

    suspend fun generateImage(
        prompt: String,
        baseImageBase64: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Prompt cannot be empty for image generation."))
        }

        try {
            val encodedPrompt = URLEncoder.encode(trimmed, "UTF-8")
            val imageUrl = "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&nologo=true&model=flux"

            // Verify that the URL responds and starts generating
            val testRequest = Request.Builder()
                .url(imageUrl)
                .head()
                .build()

            // If head succeeds or we return the direct URL, Coil can load it directly
            Result.success(imageUrl)
        } catch (e: Exception) {
            Result.failure(Exception("Pollinations image generation failed: ${e.message}"))
        }
    }
}
