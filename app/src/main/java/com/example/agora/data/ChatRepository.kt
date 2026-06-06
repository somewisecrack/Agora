package com.example.agora.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChatRepository(context: Context) {

    private val file = File(context.filesDir, "chat_history.json")

    fun load(): List<ChatMessage> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ChatMessage(
                    id = obj.getString("id"),
                    role = ChatRole.valueOf(obj.getString("role")),
                    text = obj.getString("text"),
                    transcript = if (obj.has("transcript")) obj.getString("transcript") else null,
                    timestampMillis = obj.getLong("timestampMillis")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.forEach { msg ->
            val obj = JSONObject().apply {
                put("id", msg.id)
                put("role", msg.role.name)
                put("text", msg.text)
                msg.transcript?.let { put("transcript", it) }
                put("timestampMillis", msg.timestampMillis)
            }
            array.put(obj)
        }
        file.writeText(array.toString())
    }

    fun clear() {
        file.delete()
    }
}
