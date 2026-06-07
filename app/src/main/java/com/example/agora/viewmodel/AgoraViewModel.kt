package com.example.agora.viewmodel

import android.app.Application
import android.media.MediaRecorder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agora.data.Attachment
import com.example.agora.data.AttachmentType
import com.example.agora.data.ChatMessage
import com.example.agora.data.ChatRepository
import com.example.agora.data.ChatRole
import com.example.agora.data.Conversation
import com.example.agora.debate.AgoraDebateEngine
import com.example.agora.debate.TranscriptFormatter
import com.example.agora.llm.GemmaLocalLlm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class AgoraUiState(
    val conversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,   // null = new unsaved chat
    val activeMessages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isGenerating: Boolean = false,
    val statusLabel: String = "",
    val errorMessage: String? = null,
    val modelReady: Boolean = false,
    val pendingAttachments: List<Attachment> = emptyList(),
    val isRecording: Boolean = false
)

class AgoraViewModel(application: Application) : AndroidViewModel(application) {

    private var gemmaLlm: GemmaLocalLlm? = null
    private var debateEngine: AgoraDebateEngine? = null
    private val repository = ChatRepository(application)
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null

    private val attachmentsDir: File
        get() = File(getApplication<Application>().filesDir, "attachments").also { it.mkdirs() }

    private val capturesDir: File
        get() = File(getApplication<Application>().cacheDir, "captures").also { it.mkdirs() }

    private val _uiState = MutableStateFlow(AgoraUiState())
    val uiState: StateFlow<AgoraUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val conversations = withContext(Dispatchers.IO) { repository.load() }
            _uiState.update { it.copy(conversations = conversations) }
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

    fun newChat() {
        _uiState.update {
            it.copy(
                activeConversationId = null,
                activeMessages = emptyList(),
                input = "",
                pendingAttachments = emptyList(),
                errorMessage = null
            )
        }
    }

    fun loadConversation(id: String) {
        val conv = _uiState.value.conversations.find { it.id == id } ?: return
        _uiState.update {
            it.copy(
                activeConversationId = id,
                activeMessages = conv.messages,
                input = "",
                pendingAttachments = emptyList(),
                errorMessage = null
            )
        }
    }

    fun deleteConversation(id: String) {
        val updated = _uiState.value.conversations.filter { it.id != id }
        val wasActive = _uiState.value.activeConversationId == id
        _uiState.update {
            it.copy(
                conversations = updated,
                activeConversationId = if (wasActive) null else it.activeConversationId,
                activeMessages = if (wasActive) emptyList() else it.activeMessages
            )
        }
        viewModelScope.launch(Dispatchers.IO) { repository.save(updated) }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(input = text, errorMessage = null) }
    }

    fun addImageAttachment(uriString: String) {
        val dest = File(attachmentsDir, "img_${System.currentTimeMillis()}.jpg")
        try {
            val uri = android.net.Uri.parse(uriString)
            if (uri.scheme == "content") {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: throw Exception("Could not open URI")
            } else {
                File(uriString).copyTo(dest, overwrite = true)
            }
            _uiState.update { it.copy(pendingAttachments = it.pendingAttachments + Attachment(AttachmentType.IMAGE, dest.absolutePath)) }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to attach image: ${e.message}") }
        }
    }

    fun removeAttachment(index: Int) {
        _uiState.update {
            it.copy(pendingAttachments = it.pendingAttachments.toMutableList().also { list -> list.removeAt(index) })
        }
    }

    fun newCameraFile(): File = File(capturesDir, "photo_${System.currentTimeMillis()}.jpg")

    fun onCameraCaptured(file: File) {
        val dest = File(attachmentsDir, file.name)
        file.copyTo(dest, overwrite = true)
        _uiState.update { it.copy(pendingAttachments = it.pendingAttachments + Attachment(AttachmentType.IMAGE, dest.absolutePath)) }
    }

    fun startRecording() {
        val file = File(capturesDir, "voice_${System.currentTimeMillis()}.m4a")
        currentRecordingFile = file
        try {
            mediaRecorder = MediaRecorder(getApplication()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            _uiState.update { it.copy(isRecording = true, errorMessage = null) }
        } catch (e: Exception) {
            mediaRecorder?.release()
            mediaRecorder = null
            _uiState.update { it.copy(errorMessage = "Could not start recording: ${e.message}") }
        }
    }

    fun stopRecording() {
        try { mediaRecorder?.stop() } catch (_: Exception) { }
        mediaRecorder?.release()
        mediaRecorder = null
        _uiState.update { it.copy(isRecording = false) }
        val file = currentRecordingFile ?: return
        currentRecordingFile = null
        if (file.exists() && file.length() > 0) {
            val dest = File(attachmentsDir, file.name)
            file.copyTo(dest, overwrite = true)
            _uiState.update { it.copy(pendingAttachments = it.pendingAttachments + Attachment(AttachmentType.AUDIO, dest.absolutePath)) }
        }
    }

    fun cancelRecording() {
        try { mediaRecorder?.stop() } catch (_: Exception) { }
        mediaRecorder?.release()
        mediaRecorder = null
        currentRecordingFile?.delete()
        currentRecordingFile = null
        _uiState.update { it.copy(isRecording = false) }
    }

    fun onSend() {
        val question = _uiState.value.input.trim()
        val attachments = _uiState.value.pendingAttachments
        if (question.isEmpty() && attachments.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter a question or attach a file.") }
            return
        }
        if (_uiState.value.isGenerating) return
        val engine = debateEngine ?: run {
            _uiState.update { it.copy(errorMessage = "Model not ready yet. Please wait.") }
            return
        }

        val effectiveQuestion = question.ifEmpty { "Please analyze the attached content." }
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.User,
            text = effectiveQuestion,
            attachments = attachments,
            timestampMillis = System.currentTimeMillis()
        )

        // Determine if we're continuing an existing conversation or starting a new one
        val currentActiveId = _uiState.value.activeConversationId
        val newActiveId: String
        val currentMessages: List<ChatMessage>
        val updatedConversations: List<Conversation>

        if (currentActiveId == null) {
            // New conversation
            newActiveId = UUID.randomUUID().toString()
            val title = effectiveQuestion.take(50).let { if (effectiveQuestion.length > 50) "$it…" else it }
            currentMessages = listOf(userMessage)
            val newConv = Conversation(id = newActiveId, title = title, messages = currentMessages, createdAt = System.currentTimeMillis())
            updatedConversations = listOf(newConv) + _uiState.value.conversations
        } else {
            newActiveId = currentActiveId
            currentMessages = _uiState.value.activeMessages + userMessage
            updatedConversations = _uiState.value.conversations.map {
                if (it.id == newActiveId) it.copy(messages = currentMessages) else it
            }
        }

        _uiState.update {
            it.copy(
                conversations = updatedConversations,
                activeConversationId = newActiveId,
                activeMessages = currentMessages,
                input = "",
                pendingAttachments = emptyList(),
                isGenerating = true,
                statusLabel = "Starting debate...",
                errorMessage = null
            )
        }
        viewModelScope.launch(Dispatchers.IO) { repository.save(updatedConversations) }

        viewModelScope.launch {
            try {
                val result = engine.runDebate(effectiveQuestion, attachments) { status ->
                    _uiState.update { it.copy(statusLabel = status) }
                }

                val agoraMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.Agora,
                    text = TranscriptFormatter.formatAdvisory(result),
                    transcript = TranscriptFormatter.formatTranscript(result),
                    timestampMillis = System.currentTimeMillis()
                )

                val messagesWithResponse = _uiState.value.activeMessages + agoraMessage
                val finalConversations = _uiState.value.conversations.map {
                    if (it.id == newActiveId) it.copy(messages = messagesWithResponse) else it
                }

                _uiState.update {
                    it.copy(
                        conversations = finalConversations,
                        activeMessages = messagesWithResponse,
                        isGenerating = false,
                        statusLabel = ""
                    )
                }
                withContext(Dispatchers.IO) { repository.save(finalConversations) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isGenerating = false, statusLabel = "", errorMessage = "Generation failed: ${e.message}")
                }
            }
        }
    }

    fun deleteMessage(id: String) {
        val activeId = _uiState.value.activeConversationId ?: return
        val updatedMessages = _uiState.value.activeMessages.filter { it.id != id }
        val updatedConversations = _uiState.value.conversations.map {
            if (it.id == activeId) it.copy(messages = updatedMessages) else it
        }
        _uiState.update { it.copy(activeMessages = updatedMessages, conversations = updatedConversations) }
        viewModelScope.launch(Dispatchers.IO) { repository.save(updatedConversations) }
    }

    override fun onCleared() {
        super.onCleared()
        cancelRecording()
        gemmaLlm?.close()
    }
}
