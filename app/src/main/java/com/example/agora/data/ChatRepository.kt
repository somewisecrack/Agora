package com.example.agora.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ChatRepository(context: Context) {

    private val file = File(context.filesDir, "chat_history.json")

    fun load(): List<Conversation> {
        if (!file.exists()) return emptyList()
        val text = file.readText()
        return try {
            // New format: {"conversations": [...]}
            val root = JSONObject(text)
            val arr = root.getJSONArray("conversations")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Conversation(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    messages = parseMessages(obj.getJSONArray("messages")),
                    createdAt = obj.getLong("createdAt")
                )
            }
        } catch (_: Exception) {
            // Migrate old flat format: JSON array of messages → single conversation
            try {
                val messages = parseMessages(JSONArray(text))
                if (messages.isEmpty()) return emptyList()
                val title = messages.firstOrNull { it.role == ChatRole.User }?.text?.take(50) ?: "Chat"
                listOf(
                    Conversation(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        messages = messages,
                        createdAt = messages.first().timestampMillis
                    )
                )
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun save(conversations: List<Conversation>) {
        val root = JSONObject()
        val arr = JSONArray()
        conversations.forEach { conv ->
            arr.put(JSONObject().apply {
                put("id", conv.id)
                put("title", conv.title)
                put("createdAt", conv.createdAt)
                put("messages", serializeMessages(conv.messages))
            })
        }
        root.put("conversations", arr)
        file.writeText(root.toString())
    }

    fun clear() { file.delete() }

    private fun parseMessages(arr: JSONArray): List<ChatMessage> =
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            ChatMessage(
                id = obj.getString("id"),
                role = ChatRole.valueOf(obj.getString("role")),
                text = obj.getString("text"),
                transcript = if (obj.has("transcript")) obj.getString("transcript") else null,
                attachments = if (obj.has("attachments")) {
                    val a = obj.getJSONArray("attachments")
                    (0 until a.length()).map { j ->
                        val at = a.getJSONObject(j)
                        Attachment(AttachmentType.valueOf(at.getString("type")), at.getString("filePath"))
                    }
                } else emptyList(),
                timestampMillis = obj.getLong("timestampMillis")
            )
        }

    private fun serializeMessages(messages: List<ChatMessage>): JSONArray {
        val arr = JSONArray()
        messages.forEach { msg ->
            arr.put(JSONObject().apply {
                put("id", msg.id)
                put("role", msg.role.name)
                put("text", msg.text)
                msg.transcript?.let { put("transcript", it) }
                if (msg.attachments.isNotEmpty()) {
                    val a = JSONArray()
                    msg.attachments.forEach { at ->
                        a.put(JSONObject().apply {
                            put("type", at.type.name)
                            put("filePath", at.filePath)
                        })
                    }
                    put("attachments", a)
                }
                put("timestampMillis", msg.timestampMillis)
            })
        }
        return arr
    }
}
