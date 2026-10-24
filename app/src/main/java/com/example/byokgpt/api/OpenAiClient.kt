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

class OpenAiClient(
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
            emit(StreamChunk.Error("No API key configured for OpenAI ChatGPT.", isAuthError = true))
            return@flow
        }

        val baseUrl = if (config.customEndpoint.isNotBlank()) {
            config.customEndpoint.trimEnd('/')
        } else {
            "https://api.openai.com/v1"
        }
        val url = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"

        val requestJson = JSONObject().apply {
            put("model", model)
            put("stream", true)

            val messagesArray = JSONArray()
            if (!systemPrompt.isNullOrBlank()) {
                messagesArray.put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
            }

            val contextMessages = if (messages.size > 20) messages.takeLast(20) else messages
            for (msg in contextMessages) {
                val role = if (msg.role.equals("user", ignoreCase = true)) "user" else "assistant"

                if (!msg.attachmentBase64.isNullOrEmpty() && !msg.attachmentMime.isNullOrEmpty() && msg.attachmentMime.startsWith("image/")) {
                    val contentParts = JSONArray()
                    if (msg.content.isNotEmpty()) {
                        contentParts.put(JSONObject().apply {
                            put("type", "text")
                            put("text", msg.content)
                        })
                    }

                    val imageUrlObj = JSONObject().apply {
                        put("url", "data:${msg.attachmentMime};base64,${msg.attachmentBase64}")
                    }
                    contentParts.put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", imageUrlObj)
                    })

                    messagesArray.put(JSONObject().apply {
                        put("role", role)
                        put("content", contentParts)
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
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val call = httpClient.newCall(request)
        onCallCreated?.invoke(call)

        val fullTextBuilder = StringBuilder()

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                var errorMessage = "OpenAI API HTTP ${response.code}: ${response.message}"
                var isAuth = response.code == 401 || response.code == 403
                var isRateLimit = response.code == 429

                try {
                    val errJson = JSONObject(errorBody).getJSONObject("error")
                    errorMessage = errJson.optString("message", errorMessage)
                    val code = errJson.optString("code", "")
                    if (code == "insufficient_quota" || isRateLimit) {
                        isRateLimit = true
                        errorMessage = "OpenAI quota or rate limit exceeded. Please check your billing or switch provider."
                    } else if (code == "invalid_api_key" || isAuth) {
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
                emit(StreamChunk.Error("Empty response stream from OpenAI."))
                return@flow
            }

            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line?.trim() ?: continue
                    if (l.startsWith("data:")) {
                        val dataJsonStr = l.substring(5).trim()
                        if (dataJsonStr == "[DONE]") break
                        if (dataJsonStr.isEmpty()) continue

                        try {
                            val dataJson = JSONObject(dataJsonStr)
                            val choices = dataJson.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val firstChoice = choices.getJSONObject(0)
                                val delta = firstChoice.optJSONObject("delta")
                                val text = delta?.optString("content", "") ?: ""
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
                emit(StreamChunk.Error("No response text received from OpenAI. Please check connection or try again."))
            }
        } catch (e: Exception) {
            if (call.isCanceled()) {
                return@flow
            }
            emit(StreamChunk.Error(LogSanitizer.sanitizeLog(e.localizedMessage ?: "Network error connecting to OpenAI API.")))
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
                "https://api.openai.com/v1"
            }
            val url = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"

            val requestJson = JSONObject().apply {
                put("model", model)
                put("max_tokens", 5)
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Hi")
                    })
                }
                put("messages", messages)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    Result.success("Connection to OpenAI ($model) verified successfully!")
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
