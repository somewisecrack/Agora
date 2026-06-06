package com.example.agora.llm

import com.example.agora.data.Attachment

interface LocalLlm {
    suspend fun generate(prompt: String, attachments: List<Attachment> = emptyList()): String
}
