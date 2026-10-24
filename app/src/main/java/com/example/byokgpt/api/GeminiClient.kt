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

class GeminiClient(
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
            emit(StreamChunk.Error("No API key configured for Google Gemini.", isAuthError = true))
            return@flow
        }

        val baseUrl = if (config.customEndpoint.isNotBlank()) {
            config.customEndpoint.trimEnd('/')
        } else {
            "https://generativelanguage.googleapis.com"
        }

        val cleanKey = config.apiKey.trim()
        val encodedKey = java.net.URLEncoder.encode(cleanKey, "UTF-8")
        val url = "$baseUrl/v1beta/models/$model:streamGenerateContent?alt=sse&key=$encodedKey"

        val requestJson = JSONObject()

        if (!systemPrompt.isNullOrBlank()) {
            val systemObj = JSONObject()
            val sysParts = JSONArray().apply {
                put(JSONObject().put("text", systemPrompt))
            }
            systemObj.put("parts", sysParts)
            requestJson.put("systemInstruction", systemObj)
        }

        val contentsArray = JSONArray()
        val contextMessages = if (messages.size > 20) messages.takeLast(20) else messages
        for (msg in contextMessages) {
            val role = if (msg.role.equals("user", ignoreCase = true)) "user" else "model"
            val contentObj = JSONObject()
            contentObj.put("role", role)
            val partsArray = JSONArray()

            // Multimodal attachment support (images, pdfs)
            if (!msg.attachmentBase64.isNullOrEmpty() && !msg.attachmentMime.isNullOrEmpty()) {
                val inlineDataObj = JSONObject().apply {
                    put("mimeType", msg.attachmentMime)
                    put("data", msg.attachmentBase64)
                }
                partsArray.put(JSONObject().put("inlineData", inlineDataObj))
            }

            if (msg.content.isNotEmpty()) {
                partsArray.put(JSONObject().put("text", msg.content))
            }

            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
        }
        requestJson.put("contents", contentsArray)

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", cleanKey)
            .post(requestBody)
            .build()

        val call = httpClient.newCall(request)
        onCallCreated?.invoke(call)

        val fullTextBuilder = StringBuilder()

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                var errorMessage = "Google Gemini HTTP ${response.code}: ${response.message}"
                var isRateLimit = response.code == 429
                var isAuth = response.code == 400 || response.code == 401 || response.code == 403

                try {
                    val errJson = JSONObject(errorBody).getJSONObject("error")
                    errorMessage = errJson.optString("message", errorMessage)
                    val status = errJson.optString("status", "")
                    if (response.code == 404 || status.contains("NOT_FOUND", ignoreCase = true)) {
                        errorMessage = "Model '$model' is not available. Please switch to Gemini 3.5 Flash in the model picker."
                    } else if (status.equals("RESOURCE_EXHAUSTED", ignoreCase = true) || isRateLimit) {
                        isRateLimit = true
                        errorMessage = "Gemini quota or rate limit exceeded. Please check your account usage or try another provider."
                    } else if (status.contains("PERMISSION_DENIED", ignoreCase = true) ||
                        status.contains("INVALID_ARGUMENT", ignoreCase = true) || isAuth) {
                        isAuth = true
                    }
                } catch (_: Exception) {
                    // Fallback to sanitized message
                }

                emit(StreamChunk.Error(LogSanitizer.sanitizeLog(errorMessage), isRateLimit = isRateLimit, isAuthError = isAuth))
                return@flow
            }

            emit(StreamChunk.FirstBytes())

            val inputStream = response.body?.byteStream()
            if (inputStream == null) {
                emit(StreamChunk.Error("Empty response stream from Google Gemini."))
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
                            val candidates = dataJson.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val firstCand = candidates.getJSONObject(0)
                                val content = firstCand.optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                if (parts != null) {
                                    for (i in 0 until parts.length()) {
                                        val text = parts.getJSONObject(i).optString("text", "")
                                        if (text.isNotEmpty()) {
                                            fullTextBuilder.append(text)
                                            emit(StreamChunk.Delta(text))
                                        }
                                    }
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
                emit(StreamChunk.Error("No response text received from model. Please check connection or try again."))
            }
        } catch (e: Exception) {
            if (call.isCanceled()) {
                // User intentionally cancelled stream
                return@flow
            }
            emit(StreamChunk.Error(LogSanitizer.sanitizeLog(e.localizedMessage ?: "Network error connecting to Gemini API.")))
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
                "https://generativelanguage.googleapis.com"
            }
            val cleanKey = config.apiKey.trim()
            val encodedKey = java.net.URLEncoder.encode(cleanKey, "UTF-8")
            val url = "$baseUrl/v1beta/models/$model:generateContent?key=$encodedKey"

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", "Hello, please reply with OK."))
                        })
                    })
                }
                put("contents", contents)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", cleanKey)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    Result.success("Connection to Google Gemini ($model) verified successfully!")
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
