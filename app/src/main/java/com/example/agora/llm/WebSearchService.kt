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

        // 1. DuckDuckGo Instant Answers — fast, returns direct factual answers
        try {
            val conn = URL("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                .openConnection() as HttpURLConnection
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

        // 2. Wikipedia Search API — reliable encyclopedic context
        try {
            val conn = URL(
                "https://en.wikipedia.org/w/api.php?action=query&list=search" +
                "&srsearch=$encoded&srlimit=3&format=json&srprop=snippet"
            ).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Agora/1.0 (Android; contact: agora-app)")
            if (conn.responseCode == 200) {
                val arr = JSONObject(conn.inputStream.bufferedReader().readText())
                    .getJSONObject("query").getJSONArray("search")
                conn.disconnect()
                for (i in 0 until minOf(3, arr.length())) {
                    val obj = arr.getJSONObject(i)
                    val snippet = obj.optString("snippet")
                        .replace(Regex("<[^>]+>"), "")   // strip HTML tags
                        .trim()
                    val title = obj.optString("title").trim()
                    if (snippet.isNotEmpty()) results.add(SearchResult(title, snippet))
                }
            } else conn.disconnect()
        } catch (_: Exception) {}

        return results
    }
}
