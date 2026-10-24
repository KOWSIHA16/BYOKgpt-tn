package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.byokgpt.model.AiProvider
import com.example.byokgpt.model.MessageRole
import com.example.byokgpt.model.MessageStatus
import com.example.byokgpt.model.TurnTimings
import com.example.byokgpt.ui.chat.ChatViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class LatencyAndResponsivenessTest {

    private lateinit var application: Application
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        viewModel = ChatViewModel(application)
    }

    @Test
    fun testUserMessageAppearsImmediatelyOnSend() = runTest {
        val prompt = "Hello, what is the speed of light?"

        // T0 -> T1: Sending message should immediately update in-memory currentMessages
        val beforeSend = System.currentTimeMillis()
        viewModel.sendMessage(prompt)
        val afterSend = System.currentTimeMillis()

        val messages = viewModel.currentMessages.value
        assertTrue("Messages list must not be empty after send", messages.isNotEmpty())

        val userMessage = messages.find { it.role == MessageRole.USER.name }
        assertNotNull("User message must be immediately visible in UI state", userMessage)
        assertEquals(prompt, userMessage?.content)
        assertEquals(MessageStatus.SUCCESS.name, userMessage?.status)

        val assistantMessage = messages.find { it.role == MessageRole.ASSISTANT.name }
        assertNotNull("Assistant placeholder must be immediately visible in UI state", assistantMessage)
        assertEquals(MessageStatus.STREAMING.name, assistantMessage?.status)

        // Latency timing verification
        val timings = viewModel.latestTimings.value
        assertNotNull("TurnTimings must be recorded", timings)
        timings?.let {
            assertTrue("T1 must be >= T0", it.t1UserVisibleMs >= it.t0SendTappedMs)
            assertTrue("T0->T1 user visible latency must be under 50ms in-memory", it.t0ToT1Ms < 50L)
        }
    }

    @Test
    fun testUserMessagePreservedOnError() = runTest {
        val prompt = "Message before error"
        viewModel.sendMessage(prompt)

        val messages = viewModel.currentMessages.value
        val userMsg = messages.find { it.role == MessageRole.USER.name && it.content == prompt }
        assertNotNull("User message must be preserved even if request has not finished or failed", userMsg)
    }

    @Test
    fun testTurnTimingsCalculation() {
        val timings = TurnTimings(
            turnId = "turn-1",
            provider = "Google Gemini",
            model = "gemini-3.5-flash",
            t0SendTappedMs = 1000L,
            t1UserVisibleMs = 1005L,
            t2NetworkStartMs = 1020L,
            t3FirstBytesMs = 1450L,
            t4FirstTextMs = 1470L,
            t5CompletedMs = 2100L
        )

        assertEquals(5L, timings.t0ToT1Ms)
        assertEquals(15L, timings.t1ToT2Ms)
        assertEquals(430L, timings.t2ToT3TtfbMs)
        assertEquals(20L, timings.t3ToT4Ms)
        assertEquals(630L, timings.t4ToT5Ms)
        assertEquals(1100L, timings.totalLatencyMs)

        val logStr = timings.toLogString()
        assertTrue(logStr.contains("T0->T1 (User Visible): 5ms"))
        assertTrue(logStr.contains("T2->T3 (TTFB): 430ms"))
        assertTrue(logStr.contains("Total: 1100ms"))
    }
}
