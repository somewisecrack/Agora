package com.example.agora.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agora.BuildConfig
import com.example.agora.data.ChatMessage
import com.example.agora.data.ChatRepository
import com.example.agora.data.ChatRole
import com.example.agora.debate.AgoraDebateEngine
import com.example.agora.debate.TranscriptFormatter
import com.example.agora.llm.GemmaLocalLlm
import com.example.agora.web.WebSearchClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class AgoraUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isGenerating: Boolean = false,
    val statusLabel: String = "",
    val errorMessage: String? = null,
    val modelReady: Boolean = false,
    val webSearchEnabled: Boolean = false,
    val webSearchAvailable: Boolean = false
)

class AgoraViewModel(application: Application) : AndroidViewModel(application) {

    private var gemmaLlm: GemmaLocalLlm? = null
    private var debateEngine: AgoraDebateEngine? = null
    private val repository = ChatRepository(application)
    private val webSearchClient: WebSearchClient? =
        BuildConfig.BRAVE_SEARCH_API_KEY.takeIf { it.isNotEmpty() }
            ?.let { WebSearchClient(it) }

    private val _uiState = MutableStateFlow(AgoraUiState())
    val uiState: StateFlow<AgoraUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { repository.load() }
            _uiState.update { it.copy(
                messages = history,
                webSearchAvailable = webSearchClient != null
            ) }
        }
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

    fun toggleWebSearch() {
        if (webSearchClient == null) {
            _uiState.update { it.copy(errorMessage = "Add BRAVE_SEARCH_API_KEY to local.properties and rebuild.") }
            return
        }
        _uiState.update { it.copy(webSearchEnabled = !it.webSearchEnabled) }
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

        val messagesWithUser = _uiState.value.messages + userMessage
        _uiState.update {
            it.copy(
                messages = messagesWithUser,
                input = "",
                isGenerating = true,
                statusLabel = "Starting debate...",
                errorMessage = null
            )
        }
        viewModelScope.launch(Dispatchers.IO) { repository.save(messagesWithUser) }

        viewModelScope.launch {
            try {
                val searchContext: String? = if (_uiState.value.webSearchEnabled && webSearchClient != null) {
                    _uiState.update { it.copy(statusLabel = "Searching the web...") }
                    try {
                        val results = webSearchClient.search(question)
                        if (results.isEmpty()) null
                        else results.mapIndexed { i, r ->
                            "[${i + 1}] ${r.title}\n${r.snippet}"
                        }.joinToString("\n\n")
                    } catch (e: Exception) {
                        null
                    }
                } else null

                val result = engine.runDebate(question, searchContext) { status ->
                    _uiState.update { it.copy(statusLabel = status) }
                }

                val agoraMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.Agora,
                    text = TranscriptFormatter.formatAdvisory(result),
                    transcript = TranscriptFormatter.formatTranscript(result),
                    timestampMillis = System.currentTimeMillis()
                )

                val updatedMessages = _uiState.value.messages + agoraMessage
                _uiState.update {
                    it.copy(messages = updatedMessages, isGenerating = false, statusLabel = "")
                }
                withContext(Dispatchers.IO) { repository.save(updatedMessages) }
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

    fun deleteMessage(id: String) {
        val updated = _uiState.value.messages.filter { it.id != id }
        _uiState.update { it.copy(messages = updated) }
        viewModelScope.launch(Dispatchers.IO) { repository.save(updated) }
    }

    fun deleteAllMessages() {
        _uiState.update { it.copy(messages = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) { repository.clear() }
    }

    override fun onCleared() {
        super.onCleared()
        gemmaLlm?.close()
    }
}
