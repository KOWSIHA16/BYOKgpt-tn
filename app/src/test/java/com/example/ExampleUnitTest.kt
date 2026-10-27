package com.example

import com.example.byokgpt.model.AiProvider
import com.example.byokgpt.model.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun verifyProvidersAndDefaultModels() {
        assertEquals("gemini-3.8-flash", AiProvider.GEMINI.defaultModel)
        assertEquals("claude-3-5-sonnet-20241022", AiProvider.CLAUDE.defaultModel)
        assertEquals("gpt-4o-mini", AiProvider.OPENAI.defaultModel)
    }

    @Test
    fun verifyModelCatalogContainsAllProviders() {
        val geminiModels = ModelCatalog.getModelsFor(AiProvider.GEMINI)
        val claudeModels = ModelCatalog.getModelsFor(AiProvider.CLAUDE)
        val openAiModels = ModelCatalog.getModelsFor(AiProvider.OPENAI)

        assertTrue(geminiModels.isNotEmpty())
        assertTrue(claudeModels.isNotEmpty())
        assertTrue(openAiModels.isNotEmpty())

        assertNotNull(geminiModels.find { it.id == "gemini-3.8-flash" })
        assertNotNull(claudeModels.find { it.id == "claude-3-5-sonnet-20241022" })
        assertNotNull(openAiModels.find { it.id == "gpt-4o-mini" })
    }
}
