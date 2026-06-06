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
    val text: String,
    val timestampMillis: Long
)

enum class ChatRole {
    User,
    Agora,
    System
}
