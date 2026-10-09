package com.tnyemii.m120launcher

import java.util.Locale

// determines whether user query requires live web search or offline llm :3
object WebSearchClassifier {

    private val EXPLICIT_SEARCH_TERMS = setOf(
        "search", "google", "look up", "find online", "search web", "web search", "browse",
        "search the web", "look this up", "find info on"
    )

    private val RECENCY_KEYWORDS = setOf(
        "latest", "recent", "today", "current", "now", "weather", "news", "price", "stock",
        "score", "version", "release", "released", "update", "schedule", "who is the current",
        "what is the current", "2025", "2026", "who won", "what happened today", "whats happening"
    )

    private val CHIT_CHAT_PREFIXES = listOf(
        "hi", "hello", "hey", "good morning", "good evening", "good night", "how are you",
        "whats up", "what's up", "who are you", "what is your name", "tell me a joke",
        "write a poem", "tell a story", "explain recursion", "what is 2+2", "what is 2 + 2"
    )

    // checks if user message needs web search based on keywords <3
    fun shouldSearchWeb(userMessage: String): Boolean {
        val clean = userMessage.trim().lowercase(Locale.ROOT)
        if (clean.isBlank()) return false

        if (CHIT_CHAT_PREFIXES.any { clean == it || clean.startsWith("$it ") }) {
            if (!EXPLICIT_SEARCH_TERMS.any { clean.contains(it) }) {
                return false
            }
        }

        if (EXPLICIT_SEARCH_TERMS.any { clean.contains(it) }) {
            return true
        }

        if (RECENCY_KEYWORDS.any { clean.contains(it) }) {
            return true
        }

        return false
    }

    // strips search prefixes to get clean search query string :3
    fun extractSearchQuery(userMessage: String): String {
        var query = userMessage.trim()
        val lower = query.lowercase(Locale.ROOT)

        val prefixesToRemove = listOf(
            "can you search for", "please search for", "search for", "search web for",
            "look up", "google", "find online", "search", "browse"
        )

        for (prefix in prefixesToRemove) {
            if (lower.startsWith(prefix)) {
                query = query.substring(prefix.length).trim()
                break
            }
        }

        return query.ifBlank { userMessage.trim() }
    }
}
