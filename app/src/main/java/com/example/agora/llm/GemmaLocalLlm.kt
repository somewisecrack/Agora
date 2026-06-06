package com.example.agora.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
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
            backend = Backend.GPU()
        )
    )

    // Call this once before generate(). Runs the blocking model load off the main thread.
    suspend fun initialize() = withContext(Dispatchers.IO) {
        engine.initialize()
    }

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        engine.createConversation().use { conversation ->
            val message = conversation.sendMessage(prompt)
            message.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
        }
    }

    fun close() {
        engine.close()
    }
}
