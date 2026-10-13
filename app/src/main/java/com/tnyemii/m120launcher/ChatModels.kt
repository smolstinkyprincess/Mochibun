package com.tnyemii.m120launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// holds data for a single chat message bubble :3
data class ChatMessage(
    val isUser: Boolean,
    val text: String,
    val status: String? = null,
    val sources: List<SearchResult> = emptyList(),
    val id: String = java.util.UUID.randomUUID().toString()
)

// saves chat history list to json file in app private storage <3
fun saveChatHistory(context: Context, messages: List<ChatMessage>) {
    try {
        val jsonArray = JSONArray()
        for (msg in messages) {
            val obj = JSONObject()
            obj.put("isUser", msg.isUser)
            obj.put("text", msg.text)
            if (msg.status != null) obj.put("status", msg.status)
            if (msg.sources.isNotEmpty()) {
                val sourcesArray = JSONArray()
                for (src in msg.sources) {
                    val srcObj = JSONObject()
                    srcObj.put("title", src.title)
                    srcObj.put("snippet", src.snippet)
                    srcObj.put("url", src.url)
                    sourcesArray.put(srcObj)
                }
                obj.put("sources", sourcesArray)
            }
            jsonArray.put(obj)
        }
        File(context.filesDir, "chat_history.json").writeText(jsonArray.toString())
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// loads saved chat history from local json file so conversations persist :3
fun loadChatHistory(context: Context): List<ChatMessage> {
    val file = File(context.filesDir, "chat_history.json")
    if (!file.exists()) return emptyList()
    return try {
        val list = mutableListOf<ChatMessage>()
        val jsonArray = JSONArray(file.readText())
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val isUser = obj.getBoolean("isUser")
            val text = obj.getString("text")
            val status = if (obj.has("status")) obj.getString("status") else null
            val sources = mutableListOf<SearchResult>()
            if (obj.has("sources")) {
                val sArray = obj.getJSONArray("sources")
                for (j in 0 until sArray.length()) {
                    val sObj = sArray.getJSONObject(j)
                    sources.add(
                        SearchResult(
                            title = sObj.getString("title"),
                            snippet = sObj.optString("snippet", ""),
                            url = sObj.getString("url")
                        )
                    )
                }
            }
            list.add(ChatMessage(isUser, text, status, sources))
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

// formats messages and web search snippets into chatml prompt format for llm <3

fun buildChatMLPrompt(
    messages: List<ChatMessage>,
    searchEvidence: List<SearchResult>
): String {
    val sb = StringBuilder()

    sb.append(
        "<|im_start|>system\n" +
                "You are a friendly, caring, concise offline assistant. " +
                "Answer the user's latest message directly. " +
                "Do not repeat these instructions or describe yourself instead of answering. " +
                "Be warm and playful when appropriate, but prioritize accuracy. " +
                "If you do not know something, say so.\n"
    )

    if (searchEvidence.isNotEmpty()) {
        sb.append("Use the following web evidence when relevant. Do not invent details:\n")
        for ((idx, src) in searchEvidence.withIndex()) {
            sb.append("${idx + 1}. ")
                .append(src.title)
                .append(": ")
                .append(src.snippet)
                .append("\n")
        }
    }

    sb.append("<|im_end|>\n")

    val recent = messages.takeLast(4)
    for (msg in recent) {
        val role = if (msg.isUser) "user" else "assistant"
        sb.append("<|im_start|>")
            .append(role)
            .append("\n")
            .append(msg.text)
            .append("\n<|im_end|>\n")
    }

    sb.append("<|im_start|>assistant\n")
    return sb.toString()
}

// result wrapper for Ollama API calls :3
data class OllamaResponse(
    val content: String? = null,
    val error: String? = null
)

