package com.example.agora.data

data class Attachment(
    val type: AttachmentType,
    val filePath: String
)

enum class AttachmentType { IMAGE, AUDIO }
