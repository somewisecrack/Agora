package com.example.agora.llm

interface LocalLlm {
    suspend fun generate(prompt: String): String
}
