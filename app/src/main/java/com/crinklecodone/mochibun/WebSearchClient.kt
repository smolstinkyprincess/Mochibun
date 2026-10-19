package com.crinklecodone.mochibun

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

// data class for individual search result items :3
data class SearchResult(
    val title: String,
    val snippet: String,
    val url: String
)

// lightweight http search client fetching duckduckgo html results <3
object WebSearchClient {

    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000
    private const val MAX_RESULTS = 3
    private const val MAX_SNIPPET_LENGTH = 140

    // precompiled regex patterns to avoid repeated compilation overhead on low-end hardware <3
    private val LINK_PATTERN = Pattern.compile(
        "<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
        Pattern.DOTALL or Pattern.CASE_INSENSITIVE
    )

    private val SNIPPET_PATTERN = Pattern.compile(
        "<(a|div|span)[^>]*class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</\\1>",
        Pattern.DOTALL or Pattern.CASE_INSENSITIVE
    )

    private val HTML_TAG_PATTERN = Pattern.compile("<[^>]*>")
    private val WHITESPACE_PATTERN = Pattern.compile("\\s+")

    // checks if device has active internet connection :3
    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // performs web search and returns top search results <3
    fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        var connection: HttpURLConnection? = null
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://html.duckduckgo.com/html/?q=$encodedQuery"
            val url = URL(urlString)

            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return emptyList()
            }

            val html = connection.inputStream.bufferedReader().use { it.readText() }
            parseDuckDuckGoHtml(html)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    // parses duckduckgo html response into clean search results :3
    private fun parseDuckDuckGoHtml(html: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        val linkMatcher = LINK_PATTERN.matcher(html)
        val snippetMatcher = SNIPPET_PATTERN.matcher(html)

        while (linkMatcher.find() && results.size < MAX_RESULTS) {
            var rawUrl = linkMatcher.group(1) ?: ""
            val title = cleanText(linkMatcher.group(2) ?: "")

            var snippet = ""
            if (snippetMatcher.find()) {
                snippet = cleanText(snippetMatcher.group(2) ?: "")
            }

            if (rawUrl.contains("uddg=")) {
                val uddgIndex = rawUrl.indexOf("uddg=") + 5
                val ampIndex = rawUrl.indexOf("&", uddgIndex)
                val encodedRealUrl = if (ampIndex != -1) rawUrl.substring(uddgIndex, ampIndex) else rawUrl.substring(uddgIndex)
                try {
                    rawUrl = java.net.URLDecoder.decode(encodedRealUrl, "UTF-8")
                } catch (_: Exception) {}
            }

            if (title.isNotBlank() && rawUrl.startsWith("http")) {
                val sanitizedSnippet = sanitizeSnippet(snippet)
                results.add(
                    SearchResult(
                        title = title.take(60),
                        snippet = sanitizedSnippet,
                        url = rawUrl
                    )
                )
            }
        }

        return results
    }

    // strips injection tokens and truncates snippet length <3
    fun sanitizeSnippet(raw: String): String {
        var text = cleanText(raw)

        text = text.replace("<|im_start|>", "")
            .replace("<|im_end|>", "")
            .replace("ignore previous instructions", "")
            .replace("ignore all previous instructions", "")
            .replace("system prompt", "")

        return text.take(MAX_SNIPPET_LENGTH).trim()
    }

    // removes html tags and decodes entities using precompiled patterns and fast literal replacement :3
    private fun cleanText(htmlText: String): String {
        val strippedTags = HTML_TAG_PATTERN.matcher(htmlText).replaceAll(" ")
        val unescaped = strippedTags
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")

        return WHITESPACE_PATTERN.matcher(unescaped).replaceAll(" ").trim()
    }
}
