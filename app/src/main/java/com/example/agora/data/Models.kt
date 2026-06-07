package com.example.agora.data

data class DebateTurn(
    val round: Int,
    val socrates: String,
    val plato: String? = null
)

data class AgoraDebateResult(
    val question: String,
    val turns: List<DebateTurn>,
    val advisory: String,
    val consensusReached: Boolean
)

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,                        // advisory for Agora messages, question text for User
    val transcript: String? = null,          // full debate exchange, Agora messages only
    val attachments: List<Attachment> = emptyList(),  // media attached to User messages
    val timestampMillis: Long
)

data class Conversation(
    val id: String,
    val title: String,       // first user message, truncated
    val messages: List<ChatMessage>,
    val createdAt: Long
)

enum class ChatRole {
    User,
    Agora,
    System
}
