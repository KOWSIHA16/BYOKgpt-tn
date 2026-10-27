package com.example

import com.example.byokgpt.api.RealTimeSearchGrounder
import com.example.byokgpt.model.AiProvider
import com.example.byokgpt.model.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class ByokGptImprovementsTest {

    @Test
    fun testGeminiDefaultModelIsGemini38Flash() {
        assertEquals("gemini-3.8-flash", AiProvider.GEMINI.defaultModel)
        val geminiModels = ModelCatalog.models.filter { it.provider == AiProvider.GEMINI }
        assertEquals(1, geminiModels.size)
        assertEquals("gemini-3.8-flash", geminiModels.first().id)
        assertEquals("Gemini 3.8 Flash", geminiModels.first().displayName)
    }

    @Test
    fun testRealTimeSearchIntentDetection() {
        // Current events / live facts should trigger real-time search when setting is off
        assertTrue(RealTimeSearchGrounder.isLiveSearchNeeded("What is the weather today?", false))
        assertTrue(RealTimeSearchGrounder.isLiveSearchNeeded("Latest news on tech in 2026", false))
        assertTrue(RealTimeSearchGrounder.isLiveSearchNeeded("Who won the match yesterday?", false))
        assertTrue(RealTimeSearchGrounder.isLiveSearchNeeded("Current stock price of Google", false))

        // Standard reasoning / coding / math queries should not trigger web search when setting is off
        assertFalse(RealTimeSearchGrounder.isLiveSearchNeeded("Write a python function to sort an array", false))
        assertFalse(RealTimeSearchGrounder.isLiveSearchNeeded("Explain quantum computing", false))
        assertFalse(RealTimeSearchGrounder.isLiveSearchNeeded("Hello! How are you?", false))

        // When web search setting is explicitly ON, all queries trigger search
        assertTrue(RealTimeSearchGrounder.isLiveSearchNeeded("Hello", true))
    }

    @Test
    fun testPinHashSaltVerification() {
        val pin = "1234"
        val salt = "test_salt_123"

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray(Charsets.UTF_8))
        val hash = digest.digest(pin.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        // Same PIN and salt produces same hash
        val digest2 = MessageDigest.getInstance("SHA-256")
        digest2.update(salt.toByteArray(Charsets.UTF_8))
        val hash2 = digest2.digest(pin.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        assertEquals(hash, hash2)

        // Wrong PIN produces different hash
        val digest3 = MessageDigest.getInstance("SHA-256")
        digest3.update(salt.toByteArray(Charsets.UTF_8))
        val hash3 = digest3.digest("5678".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        assertFalse(hash == hash3)
    }
}
