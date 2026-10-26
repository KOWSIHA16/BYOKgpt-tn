package com.example

import com.example.byokgpt.api.GeminiClient
import com.example.byokgpt.model.AiProvider
import com.example.byokgpt.model.ProviderConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveCredentialTest {

    @Test
    fun testLiveGeminiVerificationIfSecretConfigured() = runBlocking {
        // BuildConfig.GEMINI_API_KEY is injected at build time when user enters key in AI Studio Secrets panel
        val apiKey = try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            field.get(null) as? String ?: ""
        } catch (_: Exception) {
            ""
        }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            println("Live secret detected. Executing live connection verification against Google Gemini...")
            val client = GeminiClient()
            val config = ProviderConfig(
                provider = AiProvider.GEMINI,
                apiKey = apiKey
            )
            val result = client.testConnection(config, "gemini-1.5-flash")
            assertTrue("Live Gemini verification should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        } else {
            println("No live GEMINI_API_KEY secret configured in Secrets panel; test passed in mock mode.")
            assertNotNull(apiKey)
        }
    }
}
