package com.example.byokgpt.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.regex.Pattern

data class SearchResultItem(
    val title: String,
    val snippet: String,
    val url: String
)

object RealTimeSearchGrounder {

    private val httpClient: OkHttpClient = NetworkClient.sharedHttpClient

    // Keywords that indicate temporal or live real-world information is requested
    private val LIVE_QUERY_PATTERNS = listOf(
        "today", "yesterday", "tomorrow", "tonight", "this week", "this month", "this year",
        "current", "latest", "recent", "now", "news", "price", "stock", "weather",
        "score", "match", "live", "winner", "update", "released", "premiered",
        "2026", "2025", "election", "standings", "standing", "schedule"
    )

    fun isLiveSearchNeeded(prompt: String, isWebSearchSettingEnabled: Boolean): Boolean {
        if (isWebSearchSettingEnabled) return true
        val lower = prompt.trim().lowercase()
        return LIVE_QUERY_PATTERNS.any { keyword ->
            lower.contains(keyword)
        }
    }

    suspend fun searchWeb(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResultItem>()
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        try {
            // 1. DuckDuckGo Instant Answer API (Zero-click knowledge, official Wikipedia & topic definitions)
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val apiUrl = "https://api.duckduckgo.com/?q=$encoded&format=json&no_redirect=1&no_html=1"
            val apiRequest = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "BYOKGPT/1.0 (Android)")
                .get()
                .build()

            val apiResponse = httpClient.newCall(apiRequest).execute()
            if (apiResponse.isSuccessful) {
                val body = apiResponse.body?.string() ?: ""
                val json = JSONObject(body)
                val heading = json.optString("Heading", "")
                val abstractText = json.optString("AbstractText", "")
                val abstractUrl = json.optString("AbstractURL", "")

                if (abstractText.isNotBlank()) {
                    results.add(
                        SearchResultItem(
                            title = heading.ifBlank { "Instant Summary" },
                            snippet = abstractText,
                            url = abstractUrl.ifBlank { "https://duckduckgo.com/?q=$encoded" }
                        )
                    )
                }

                val related = json.optJSONArray("RelatedTopics")
                if (related != null) {
                    for (i in 0 until minOf(related.length(), 4)) {
                        val item = related.optJSONObject(i) ?: continue
                        val text = item.optString("Text", "")
                        val firstUrl = item.optString("FirstURL", "")
                        if (text.isNotBlank()) {
                            results.add(
                                SearchResultItem(
                                    title = text.take(60),
                                    snippet = text,
                                    url = firstUrl.ifBlank { "https://duckduckgo.com/?q=$encoded" }
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("RealTimeSearchGrounder", "DuckDuckGo API search failed", e)
        }

        // 2. If API returned fewer than 2 results, perform HTML search parsing
        if (results.size < 2) {
            try {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val htmlUrl = "https://html.duckduckgo.com/html/?q=$encoded"
                val htmlRequest = Request.Builder()
                    .url(htmlUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                    .get()
                    .build()

                val htmlResponse = httpClient.newCall(htmlRequest).execute()
                if (htmlResponse.isSuccessful) {
                    val html = htmlResponse.body?.string() ?: ""
                    val parsed = parseDuckDuckGoHtml(html)
                    for (item in parsed) {
                        if (results.none { it.url == item.url || it.title == item.title }) {
                            results.add(item)
                        }
                        if (results.size >= 5) break
                    }
                }
            } catch (e: Exception) {
                Log.w("RealTimeSearchGrounder", "DuckDuckGo HTML search fallback failed", e)
            }
        }

        return@withContext results.take(5)
    }

    private fun parseDuckDuckGoHtml(html: String): List<SearchResultItem> {
        val items = mutableListOf<SearchResultItem>()
        try {
            // Regex match for class="result__body" or links with class="result__url"
            val resultBlockPattern = Pattern.compile(
                """<a[^>]*class="[^"]*result__snippet[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            )
            val titlePattern = Pattern.compile(
                """<a[^>]*class="[^"]*result__url[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            )

            val matcher = resultBlockPattern.matcher(html)
            while (matcher.find() && items.size < 5) {
                val rawUrl = matcher.group(1)?.trim() ?: ""
                val rawSnippet = matcher.group(2)?.trim() ?: ""
                val cleanSnippet = rawSnippet.replace(Regex("<[^>]*>"), "").replace("&quot;", "\"").replace("&amp;", "&")
                val cleanUrl = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl

                if (cleanSnippet.isNotBlank() && cleanUrl.startsWith("http")) {
                    items.add(
                        SearchResultItem(
                            title = "Web Search Result",
                            snippet = cleanSnippet,
                            url = cleanUrl
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return items
    }

    fun buildGroundingPrompt(query: String, results: List<SearchResultItem>): String {
        if (results.isEmpty()) return ""
        return buildString {
            append("\n\n--- VERIFIED LIVE WEB SEARCH RESULTS ---\n")
            append("Query: \"$query\"\n\n")
            results.forEachIndexed { index, item ->
                append("${index + 1}. Title: ${item.title}\n")
                append("   URL: ${item.url}\n")
                append("   Snippet: ${item.snippet}\n\n")
            }
            append("INSTRUCTION: Use the above verified live web search results to answer accurately. Cite the source URLs as clickable markdown links [Title](URL) in your response. Never invent facts or dates.\n")
            append("--- END SEARCH RESULTS ---\n")
        }
    }
}
