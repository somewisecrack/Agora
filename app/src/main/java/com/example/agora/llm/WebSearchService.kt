package com.example.agora.llm

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object WebSearchService {

    // Public SearXNG instances — tried in order, first successful one wins
    private val instances = listOf(
        "https://searx.be",
        "https://paulgo.io",
        "https://search.bus-hit.me",
        "https://searxng.site"
    )

    data class SearchResult(val title: String, val snippet: String)

    // Must be called from an IO dispatcher
    fun search(query: String, maxResults: Int = 4): List<SearchResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        for (base in instances) {
            try {
                val url = URL("$base/search?q=$encoded&format=json&language=en-US")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Agora/1.0")
                val code = conn.responseCode
                if (code != 200) { conn.disconnect(); continue }
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val arr = JSONObject(body).optJSONArray("results") ?: continue
                val results = mutableListOf<SearchResult>()
                for (i in 0 until minOf(maxResults, arr.length())) {
                    val obj = arr.getJSONObject(i)
                    val snippet = obj.optString("content").trim()
                    val title = obj.optString("title").trim()
                    if (snippet.isNotEmpty()) results.add(SearchResult(title, snippet))
                }
                if (results.isNotEmpty()) return results
            } catch (_: Exception) {
                continue // try next instance
            }
        }
        return emptyList()
    }
}
