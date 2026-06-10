package com.example.agora.llm

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object WebSearchService {

    data class SearchResult(val title: String, val snippet: String)

    // Must be called from IO dispatcher
    fun search(query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val encoded = URLEncoder.encode(query, "UTF-8")

        // 1. DuckDuckGo Instant Answers — fast direct facts
        try {
            val conn = URL(
                "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            ).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14) Agora/1.0")
            if (conn.responseCode == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                conn.disconnect()
                val abstract_ = json.optString("Abstract").trim()
                val answer = json.optString("Answer").trim()
                val heading = json.optString("Heading").trim()
                if (abstract_.isNotEmpty()) results.add(SearchResult(heading.ifEmpty { "Overview" }, abstract_))
                if (answer.isNotEmpty()) results.add(SearchResult("Direct answer", answer))
            } else conn.disconnect()
        } catch (_: Exception) {}

        // 2. Wikipedia — full article introductions via extract API (much richer than snippets)
        try {
            val conn = URL(
                "https://en.wikipedia.org/w/api.php?action=query" +
                "&generator=search&gsrsearch=$encoded&gsrlimit=3" +
                "&prop=extracts&exintro=true&exlimit=3&explaintext=true" +
                "&format=json"
            ).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Agora/1.0 (Android; contact: agora-app)")
            if (conn.responseCode == 200) {
                val pages = JSONObject(conn.inputStream.bufferedReader().readText())
                    .getJSONObject("query")
                    .getJSONObject("pages")
                conn.disconnect()
                pages.keys().forEach { key ->
                    val page = pages.getJSONObject(key)
                    val title = page.optString("title").trim()
                    // Take first 500 chars of the article intro — enough to capture key facts
                    val extract = page.optString("extract").trim().take(500)
                    if (extract.isNotEmpty()) results.add(SearchResult(title, extract))
                }
            } else conn.disconnect()
        } catch (_: Exception) {}

        return results
    }
}
