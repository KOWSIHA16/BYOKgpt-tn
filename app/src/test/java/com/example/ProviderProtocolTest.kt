package com.example

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProviderProtocolTest {

    @Test
    fun testOpenAiSseChunkParsing() {
        val sseChunkJson = """
            {
              "id": "chatcmpl-123",
              "choices": [
                {
                  "delta": {
                    "content": "Hello from OpenAI!"
                  },
                  "index": 0
                }
              ]
            }
        """.trimIndent()

        val json = JSONObject(sseChunkJson)
        val choices = json.getJSONArray("choices")
        val firstChoice = choices.getJSONObject(0)
        val delta = firstChoice.getJSONObject("delta")
        val content = delta.getString("content")

        assertEquals("Hello from OpenAI!", content)
    }

    @Test
    fun testClaudeSseChunkParsing() {
        val sseChunkJson = """
            {
              "type": "content_block_delta",
              "index": 0,
              "delta": {
                "type": "text_delta",
                "text": "Hello from Claude!"
              }
            }
        """.trimIndent()

        val json = JSONObject(sseChunkJson)
        assertEquals("content_block_delta", json.getString("type"))
        val delta = json.getJSONObject("delta")
        assertEquals("text_delta", delta.getString("type"))
        assertEquals("Hello from Claude!", delta.getString("text"))
    }

    @Test
    fun testGeminiSseChunkParsing() {
        val sseChunkJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "Hello from Gemini!"
                      }
                    ],
                    "role": "model"
                  }
                }
              ]
            }
        """.trimIndent()

        val json = JSONObject(sseChunkJson)
        val candidates = json.getJSONArray("candidates")
        val firstCand = candidates.getJSONObject(0)
        val parts = firstCand.getJSONObject("content").getJSONArray("parts")
        val text = parts.getJSONObject(0).getString("text")

        assertEquals("Hello from Gemini!", text)
    }

    @Test
    fun testGeminiMultimodalPayloadConstruction() {
        val payload = JSONObject()
        val contents = JSONArray()
        val userContent = JSONObject().apply {
            put("role", "user")
            val parts = JSONArray().apply {
                put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/png")
                        put("data", "iVBORw0KGgoAAAANSUhEUg==")
                    })
                })
                put(JSONObject().put("text", "What is in this image?"))
            }
            put("parts", parts)
        }
        contents.put(userContent)
        payload.put("contents", contents)

        val retrievedParts = payload.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals(2, retrievedParts.length())
        assertTrue(retrievedParts.getJSONObject(0).has("inlineData"))
        assertEquals("image/png", retrievedParts.getJSONObject(0).getJSONObject("inlineData").getString("mimeType"))
    }

    @Test
    fun testErrorPayloadParsing() {
        // OpenAI error format
        val openAiErrorJson = """{"error":{"message":"Incorrect API key provided","type":"invalid_request_error","code":"invalid_api_key"}}"""
        val openAiErr = JSONObject(openAiErrorJson).getJSONObject("error")
        assertEquals("invalid_api_key", openAiErr.getString("code"))

        // Claude error format
        val claudeErrorJson = """{"type":"error","error":{"type":"rate_limit_error","message":"Number of request tokens has exceeded your rate limit"}}"""
        val claudeErr = JSONObject(claudeErrorJson).getJSONObject("error")
        assertEquals("rate_limit_error", claudeErr.getString("type"))

        // Gemini error format
        val geminiErrorJson = """{"error":{"code":429,"message":"Resource has been exhausted","status":"RESOURCE_EXHAUSTED"}}"""
        val geminiErr = JSONObject(geminiErrorJson).getJSONObject("error")
        assertEquals(429, geminiErr.getInt("code"))
        assertEquals("RESOURCE_EXHAUSTED", geminiErr.getString("status"))
    }
}
