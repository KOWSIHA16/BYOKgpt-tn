package com.example.byokgpt.api

import com.example.byokgpt.data.MessageEntity
import com.example.byokgpt.model.ProviderConfig
import com.example.byokgpt.model.StreamChunk
import com.example.byokgpt.security.LogSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class ClaudeClient(
    private val httpClient: OkHttpClient = NetworkClient.sharedHttpClient
) : AiClient {

    override fun streamChat(
        messages: List<MessageEntity>,
        systemPrompt: String?,
        config: ProviderConfig,
        model: String,
        onCallCreated: ((Call) -> Unit)?
    ): Flow<StreamChunk> = flow {
        if (config.apiKey.isBlank()) {
            emit(StreamChunk.Error("No API key configured for Anthropic Claude.", isAuthError = true))
            return@flow
        }

        val baseUrl = if (config.customEndpoint.isNotBlank()) {
            config.customEndpoint.trimEnd('/')
        } else {
            "https://api.anthropic.com/v1"
        }
        val url = if (baseUrl.endsWith("/messages")) baseUrl else "$baseUrl/messages"

        val hasPdf = messages.any { it.attachmentMime == "application/pdf" }

        val requestJson = JSONObject().apply {
            put("model", model)
            put("max_tokens", 4096)
            put("stream", true)

            if (!systemPrompt.isNullOrBlank()) {
                put("system", systemPrompt)
            }

            val messagesArray = JSONArray()
            val contextMessages = if (messages.size > 20) messages.takeLast(20) else messages
            for (msg in contextMessages) {
                val role = if (msg.role.equals("user", ignoreCase = true)) "user" else "assistant"

                if (!msg.attachmentBase64.isNullOrEmpty() && !msg.attachmentMime.isNullOrEmpty()) {
                    val contentBlocks = JSONArray()

                    if (msg.attachmentMime == "application/pdf") {
                        val docObj = JSONObject().apply {
                            put("type", "document")
                            put("source", JSONObject().apply {
                                put("type", "base64")
                                put("media_type", "application/pdf")
                                put("data", msg.attachmentBase64)
                            })
                        }
                        contentBlocks.put(docObj)
                    } else if (msg.attachmentMime.startsWith("image/")) {
                        val imgObj = JSONObject().apply {
                            put("type", "image")
                            put("source", JSONObject().apply {
                                put("type", "base64")
                                put("media_type", msg.attachmentMime)
                                put("data", msg.attachmentBase64)
                            })
                        }
                        contentBlocks.put(imgObj)
                    }

                    if (msg.content.isNotEmpty()) {
                        contentBlocks.put(JSONObject().apply {
                            put("type", "text")
                            put("text", msg.content)
                        })
                    }

                    messagesArray.put(JSONObject().apply {
                        put("role", role)
                        put("content", contentBlocks)
                    })
                } else {
                    messagesArray.put(JSONObject().apply {
                        put("role", role)
                        put("content", msg.content)
                    })
                }
            }
            put("messages", messagesArray)
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val reqBuilder = Request.Builder()
            .url(url)
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")

        if (hasPdf) {
            reqBuilder.addHeader("anthropic-beta", "pdfs-2024-09-25")
        }

        val request = reqBuilder.post(requestBody).build()
        val call = httpClient.newCall(request)
        onCallCreated?.invoke(call)

        val fullTextBuilder = StringBuilder()

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                var errorMessage = "Claude API HTTP ${response.code}: ${response.message}"
                var isAuth = response.code == 401 || response.code == 403
                var isRateLimit = response.code == 429

                try {
                    val errJson = JSONObject(errorBody).getJSONObject("error")
                    errorMessage = errJson.optString("message", errorMessage)
                    val errorType = errJson.optString("type", "")
                    if (errorType == "rate_limit_error" || isRateLimit) {
                        isRateLimit = true
                        errorMessage = "Claude rate limit exceeded. Please try again shortly or switch provider."
                    } else if (errorType == "authentication_error" || isAuth) {
                        isAuth = true
                    }
                } catch (_: Exception) {
                    // Fallback
                }

                emit(StreamChunk.Error(LogSanitizer.sanitizeLog(errorMessage), isRateLimit = isRateLimit, isAuthError = isAuth))
                return@flow
            }

            emit(StreamChunk.FirstBytes())

            val inputStream = response.body?.byteStream()
            if (inputStream == null) {
                emit(StreamChunk.Error("Empty response stream from Anthropic Claude."))
                return@flow
            }

            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line?.trim() ?: continue
                    if (l.startsWith("data:")) {
                        val dataJsonStr = l.substring(5).trim()
                        if (dataJsonStr.isEmpty() || dataJsonStr == "[DONE]") continue

                        try {
                            val dataJson = JSONObject(dataJsonStr)
                            val type = dataJson.optString("type")
                            if (type == "content_block_delta") {
                                val delta = dataJson.optJSONObject("delta")
                                val text = delta?.optString("text", "") ?: ""
                                if (text.isNotEmpty()) {
                                    fullTextBuilder.append(text)
                                    emit(StreamChunk.Delta(text))
                                }
                            }
                        } catch (_: Exception) {
                            // Non-JSON boundary chunk
                        }
                    }
                }
            }

            val finalText = fullTextBuilder.toString()
            if (finalText.isNotEmpty()) {
                emit(StreamChunk.Done(finalText))
            } else {
                emit(StreamChunk.Error("No response text received from Claude. Please check connection or try again."))
            }
        } catch (e: Exception) {
            if (call.isCanceled()) {
                return@flow
            }
            emit(StreamChunk.Error(LogSanitizer.sanitizeLog(e.localizedMessage ?: "Network error connecting to Claude API.")))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun testConnection(config: ProviderConfig, model: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (config.apiKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("API key cannot be empty"))
            }

            val baseUrl = if (config.customEndpoint.isNotBlank()) {
                config.customEndpoint.trimEnd('/')
            } else {
                "https://api.anthropic.com/v1"
            }
            val url = if (baseUrl.endsWith("/messages")) baseUrl else "$baseUrl/messages"

            val requestJson = JSONObject().apply {
                put("model", model)
                put("max_tokens", 10)
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Hello")
                    })
                }
                put("messages", messages)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("x-api-key", config.apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    Result.success("Connection to Anthropic Claude ($model) verified successfully!")
                } else {
                    val errorBody = response.body?.string() ?: ""
                    val msg = try {
                        JSONObject(errorBody).getJSONObject("error").getString("message")
                    } catch (_: Exception) {
                        "HTTP ${response.code}: ${response.message}"
                    }
                    Result.failure(Exception(LogSanitizer.sanitizeLog(msg)))
                }
            } catch (e: Exception) {
                Result.failure(Exception(LogSanitizer.sanitizeLog(e.message ?: "Connection error")))
            }
        }
}
