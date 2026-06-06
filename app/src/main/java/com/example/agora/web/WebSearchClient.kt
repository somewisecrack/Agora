package com.example.agora.web

import com.example.agora.data.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

class WebSearchClient(private val apiKey: String) {

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://api.search.brave.com/res/v1/web/search?q=$encoded&count=5")
        val connection = url.openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Subscription-Token", apiKey)
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000

            if (connection.responseCode != 200) return@withContext emptyList()

            val body = connection.inputStream.bufferedReader().readText()
            val results = JSONObject(body)
                .optJSONObject("web")
                ?.optJSONArray("results")
                ?: return@withContext emptyList()

            (0 until minOf(results.length(), 5)).map { i ->
                val r = results.getJSONObject(i)
                SearchResult(
                    title = r.optString("title"),
                    snippet = r.optString("description"),
                    url = r.optString("url")
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}
