package com.example

import androidx.compose.ui.graphics.Color
import com.example.byokgpt.model.AppMode
import com.example.byokgpt.ui.chat.buildAnnotatedStringWithFormatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class ByokBrowserModeTest {

    private fun resolveUrlOrSearch(input: String): String {
        val trimmed = input.trim()
        val isExplicitUrl = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)

        if (isExplicitUrl) return trimmed

        val domainRegex = Regex("""^(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}(?:/[^\s]*)?$""")
        if (!trimmed.contains(" ") && domainRegex.matches(trimmed)) {
            return "https://$trimmed"
        }

        return "https://www.google.com/search?q=" + URLEncoder.encode(trimmed, "UTF-8")
    }

    @Test
    fun testDirectUrlHandling() {
        assertEquals("https://example.com", resolveUrlOrSearch("https://example.com"))
        assertEquals("http://test.org/page", resolveUrlOrSearch("http://test.org/page"))
    }

    @Test
    fun testDomainWithoutSchemeHandling() {
        assertEquals("https://example.com", resolveUrlOrSearch("example.com"))
        assertEquals("https://github.com/android", resolveUrlOrSearch("github.com/android"))
        assertEquals("https://en.wikipedia.org/wiki/Kotlin", resolveUrlOrSearch("en.wikipedia.org/wiki/Kotlin"))
    }

    @Test
    fun testPlainTextSearchQueryHandling() {
        val songsSearch = resolveUrlOrSearch("YouTube songs")
        assertTrue(songsSearch.startsWith("https://www.google.com/search?q="))
        assertTrue(songsSearch.contains("YouTube") && songsSearch.contains("songs"))

        val codingSearch = resolveUrlOrSearch("best Android coding websites")
        assertTrue(codingSearch.startsWith("https://www.google.com/search?q="))
        assertTrue(codingSearch.contains("best") && codingSearch.contains("Android"))

        val googleSearch = resolveUrlOrSearch("google")
        assertTrue(googleSearch.startsWith("https://www.google.com/search?q="))
    }

    @Test
    fun testAppModes() {
        assertEquals(2, AppMode.entries.size)
        assertTrue(AppMode.entries.contains(AppMode.AI))
        assertTrue(AppMode.entries.contains(AppMode.BROWSER))
    }

    @Test
    fun testMarkdownLinkParsing() {
        val text = "Check out [Google](https://www.google.com) for answers."
        val annotated = buildAnnotatedStringWithFormatting(text, Color.Black, Color.Blue)

        val annotations = annotated.getStringAnnotations("URL", 0, annotated.length)
        assertEquals(1, annotations.size)
        assertEquals("https://www.google.com", annotations[0].item)
        assertTrue(annotated.text.contains("Google"))
    }

    @Test
    fun testPlainUrlParsing() {
        val text = "Visit https://developer.android.com/compose for docs."
        val annotated = buildAnnotatedStringWithFormatting(text, Color.Black, Color.Blue)

        val annotations = annotated.getStringAnnotations("URL", 0, annotated.length)
        assertEquals(1, annotations.size)
        assertEquals("https://developer.android.com/compose", annotations[0].item)
    }
}
