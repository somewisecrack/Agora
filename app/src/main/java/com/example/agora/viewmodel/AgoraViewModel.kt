package com.example.agora.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agora.data.ChatMessage
import com.example.agora.data.ChatRole
import com.example.agora.debate.AgoraDebateEngine
import com.example.agora.debate.TranscriptFormatter
import com.example.agora.llm.GemmaLocalLlm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AgoraUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isGenerating: Boolean = false,
    val statusLabel: String = "",
    val errorMessage: String? = null,
    val modelReady: Boolean = false
)

class AgoraViewModel(application: Application) : AndroidViewModel(application) {

    private var gemmaLlm: GemmaLocalLlm? = null
    private var debateEngine: AgoraDebateEngine? = null

    private val _uiState = MutableStateFlow(AgoraUiState())
    val uiState: StateFlow<AgoraUiState> = _uiState.asStateFlow()

    init {
        loadModel()
    }

    private fun loadModel() {
        viewModelScope.launch {
            _uiState.update { it.copy(statusLabel = "Loading model...") }
            try {
                val llm = GemmaLocalLlm(getApplication())
                llm.initialize()
                gemmaLlm = llm
                debateEngine = AgoraDebateEngine(llm)
                _uiState.update { it.copy(modelReady = true, statusLabel = "") }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        statusLabel = "",
                        errorMessage = "Failed to load model: ${e.message}\n" +
                            "Ensure the model is at: ${GemmaLocalLlm.MODEL_FILE}"
                    )
                }
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(input = text, errorMessage = null) }
    }

    fun onSend() {
        val question = _uiState.value.input.trim()
        if (question.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter a question.") }
            return
        }
        if (_uiState.value.isGenerating) return
        val engine = debateEngine ?: run {
            _uiState.update { it.copy(errorMessage = "Model not ready yet. Please wait.") }
            return
        }

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.User,
            text = question,
            timestampMillis = System.currentTimeMillis()
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                input = "",
                isGenerating = true,
                statusLabel = "Starting debate...",
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val result = engine.runDebate(question) { status ->
                    _uiState.update { it.copy(statusLabel = status) }
                }

                val agoraMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.Agora,
                    text = TranscriptFormatter.formatFullResult(result),
                    timestampMillis = System.currentTimeMillis()
                )

                _uiState.update {
                    it.copy(
                        messages = it.messages + agoraMessage,
                        isGenerating = false,
                        statusLabel = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        statusLabel = "",
                        errorMessage = "Generation failed: ${e.message}"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        gemmaLlm?.close()
    }
}
