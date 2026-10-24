package com.example

import com.example.byokgpt.security.LogSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityTest {

    @Test
    fun testUrlSecretSanitization() {
        val rawUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:streamGenerateContent?alt=sse&key=AIzaSySecretApiKey12345"
        val sanitized = LogSanitizer.sanitizeUrl(rawUrl)

        assertFalse(sanitized.contains("AIzaSySecretApiKey12345"))
        assertTrue(sanitized.contains("key=[REDACTED]"))
        assertTrue(sanitized.contains("alt=sse"))
    }

    @Test
    fun testHeaderSecretSanitization() {
        assertEquals("[REDACTED]", LogSanitizer.sanitizeHeader("Authorization", "Bearer sk-proj-12345"))
        assertEquals("[REDACTED]", LogSanitizer.sanitizeHeader("x-api-key", "sk-ant-12345"))
        assertEquals("[REDACTED]", LogSanitizer.sanitizeHeader("x-goog-api-key", "AQ.SecretGeminiToken"))
        assertEquals("application/json", LogSanitizer.sanitizeHeader("Content-Type", "application/json"))
    }

    @Test
    fun testLogMessageSecretSanitization() {
        val rawLog = "Failed request to https://api.com?key=AQ.GeminiKey9876 with Authorization: Bearer MY_SECRET_TOKEN and x-goog-api-key: AQ.AnotherKey"
        val sanitized = LogSanitizer.sanitizeLog(rawLog)

        assertFalse(sanitized.contains("AQ.GeminiKey9876"))
        assertFalse(sanitized.contains("MY_SECRET_TOKEN"))
        assertFalse(sanitized.contains("AQ.AnotherKey"))
        assertTrue(sanitized.contains("[REDACTED]"))
    }
}
