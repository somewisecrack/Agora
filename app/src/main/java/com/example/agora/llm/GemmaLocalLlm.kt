package com.example.agora.llm

import android.content.Context
import com.example.agora.data.Attachment
import com.example.agora.data.AttachmentType
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GemmaLocalLlm(context: Context) : LocalLlm {

    companion object {
        const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
    }

    private val modelPath = context.filesDir.absolutePath + "/$MODEL_FILE"

    private val engine: Engine = Engine(
        EngineConfig(
            modelPath = modelPath,
            backend = Backend.GPU(),
            visionBackend = Backend.GPU(),
            audioBackend = Backend.CPU()
        )
    )

    suspend fun initialize() = withContext(Dispatchers.IO) {
        engine.initialize()
    }

    override suspend fun generate(
        prompt: String,
        attachments: List<Attachment>
    ): String = withContext(Dispatchers.IO) {
        engine.createConversation().use { conversation ->
            val message = if (attachments.isEmpty()) {
                conversation.sendMessage(prompt)
            } else {
                val contentList = mutableListOf<Content>()
                attachments.forEach { attachment ->
                    when (attachment.type) {
                        AttachmentType.IMAGE -> contentList.add(Content.ImageFile(attachment.filePath))
                        AttachmentType.AUDIO -> contentList.add(Content.AudioFile(attachment.filePath))
                    }
                }
                contentList.add(Content.Text(prompt))
                conversation.sendMessage(Contents.of(*contentList.toTypedArray()))
            }
            message.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
        }
    }

    fun close() {
        engine.close()
    }
}
