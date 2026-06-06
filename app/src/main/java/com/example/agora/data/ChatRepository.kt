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
                    attachments = if (obj.has("attachments")) {
                        val arr = obj.getJSONArray("attachments")
                        (0 until arr.length()).map { j ->
                            val a = arr.getJSONObject(j)
                            Attachment(AttachmentType.valueOf(a.getString("type")), a.getString("filePath"))
                        }
                    } else emptyList(),
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
                if (msg.attachments.isNotEmpty()) {
                    val arr = JSONArray()
                    msg.attachments.forEach { a ->
                        arr.put(JSONObject().apply {
                            put("type", a.type.name)
                            put("filePath", a.filePath)
                        })
                    }
                    put("attachments", arr)
                }
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
