package com.example.agora.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.agora.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agora.data.Attachment
import com.example.agora.data.AttachmentType
import com.example.agora.data.ChatMessage
import com.example.agora.data.ChatRole
import com.example.agora.data.Conversation
import com.example.agora.viewmodel.AgoraViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun SplashOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(durationMillis = 800))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1208))
        ) {
            Image(
                painter = painterResource(id = R.drawable.agora_bg),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                contentScale = ContentScale.FillWidth
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0.0f to Color(0xFF1A1208),
                            0.45f to Color(0xFF1A1208).copy(alpha = 0.7f),
                            0.75f to Color.Transparent
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AGORA",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = FontFamily.Serif,
                        color = Color.White,
                        letterSpacing = 8.sp
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Where great minds debate",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.75f))
                )
                Spacer(modifier = Modifier.height(32.dp))
                CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f), strokeWidth = 2.dp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: AgoraViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showAttachOptions by remember { mutableStateOf(false) }

    // Camera
    var pendingCameraFile by remember { mutableStateOf<java.io.File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraFile?.let { viewModel.onCameraCaptured(it) }
        pendingCameraFile = null
        showAttachOptions = false
    }

    // Gallery
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.addImageAttachment(it.toString()) }
        showAttachOptions = false
    }

    // Camera permission
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = viewModel.newCameraFile()
            pendingCameraFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraLauncher.launch(uri)
        }
    }

    // Mic permission
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.startRecording()
            showAttachOptions = false
        }
    }

    LaunchedEffect(uiState.activeMessages.size) {
        if (uiState.activeMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.activeMessages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawer(
                conversations = uiState.conversations,
                activeId = uiState.activeConversationId,
                onNewChat = {
                    viewModel.newChat()
                    scope.launch { drawerState.close() }
                },
                onSelectConversation = { id ->
                    viewModel.loadConversation(id)
                    scope.launch { drawerState.close() }
                },
                onDeleteConversation = viewModel::deleteConversation
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Open menu")
                            }
                        },
                        title = { Text("Agora") }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.activeMessages, key = { it.id }) { message ->
                            SwipeToDeleteMessage(message = message, onDelete = { viewModel.deleteMessage(message.id) })
                        }
                        if (uiState.isGenerating) {
                            item { StatusLabel(uiState.statusLabel) }
                        }
                    }

                    if (!uiState.modelReady && uiState.errorMessage == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text = uiState.statusLabel.ifEmpty { "Loading model..." },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    uiState.errorMessage?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    // Attachment previews
                    if (uiState.pendingAttachments.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(uiState.pendingAttachments) { index, attachment ->
                                AttachmentChip(attachment = attachment, onRemove = { viewModel.removeAttachment(index) })
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Attach options row
                    AnimatedVisibility(visible = showAttachOptions && !uiState.isRecording) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AttachOptionButton(icon = Icons.Default.PhotoCamera, label = "Camera") {
                                cameraPermission.launch(android.Manifest.permission.CAMERA)
                            }
                            AttachOptionButton(icon = Icons.Default.PhotoLibrary, label = "Gallery") {
                                galleryLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }
                            AttachOptionButton(icon = Icons.Default.Mic, label = "Voice") {
                                micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }

                    // Recording indicator
                    AnimatedVisibility(visible = uiState.isRecording) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                            Text("Recording...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.cancelRecording() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.outline)
                            }
                            IconButton(onClick = { viewModel.stopRecording() }) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    InputRow(
                        input = uiState.input,
                        isGenerating = uiState.isGenerating || !uiState.modelReady || uiState.isRecording,
                        showAttachOptions = showAttachOptions,
                        onInputChanged = viewModel::onInputChanged,
                        onSend = viewModel::onSend,
                        onToggleAttach = { showAttachOptions = !showAttachOptions }
                    )
                }
            }

            SplashOverlay(visible = !uiState.modelReady && uiState.errorMessage == null)
        }
    }
}

@Composable
private fun ConversationDrawer(
    conversations: List<Conversation>,
    activeId: String?,
    onNewChat: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.widthIn(max = 300.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Agora",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onNewChat) {
                Icon(Icons.Default.Edit, contentDescription = "New chat")
            }
        }
        HorizontalDivider()

        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No previous chats",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(conversations.sortedByDescending { it.createdAt }, key = { it.id }) { conv ->
                    ConversationItem(
                        conversation = conv,
                        isActive = conv.id == activeId,
                        onClick = { onSelectConversation(conv.id) },
                        onDelete = { onDeleteConversation(conv.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val bg = if (isActive) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .then(
                Modifier.padding(0.dp) // clickable handled below
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(0.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatConversationDate(conversation.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun formatConversationDate(timestampMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMillis
    return when {
        diff < 24 * 60 * 60 * 1000L -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestampMillis))
        diff < 7 * 24 * 60 * 60 * 1000L -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestampMillis))
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMillis))
    }
}

@Composable
private fun AttachOptionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun AttachmentChip(attachment: Attachment, onRemove: () -> Unit) {
    Box {
        when (attachment.type) {
            AttachmentType.IMAGE -> {
                val bitmap = remember(attachment.filePath) {
                    runCatching { BitmapFactory.decodeFile(attachment.filePath)?.asImageBitmap() }.getOrNull()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)))
                }
            }
            AttachmentType.AUDIO -> {
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Voice", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(20.dp).align(Alignment.TopEnd)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteMessage(message: ChatMessage, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(modifier = Modifier.fillMaxSize().padding(end = 16.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    ) {
        MessageBubble(message)
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val context = LocalContext.current
    val isUser = message.role == ChatRole.User

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            Column(horizontalAlignment = Alignment.End) {
                if (message.attachments.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 4.dp)
                    ) {
                        items(message.attachments) { attachment ->
                            AttachmentPreview(attachment)
                        }
                    }
                }
                if (message.text.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        } else {
            AgoraBubble(
                advisory = message.text,
                transcript = message.transcript,
                onShare = {
                    val shareText = message.transcript?.let { "${message.text}\n\n$it" } ?: message.text
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share via"))
                }
            )
        }
    }
}

@Composable
private fun AttachmentPreview(attachment: Attachment) {
    when (attachment.type) {
        AttachmentType.IMAGE -> {
            val bitmap = remember(attachment.filePath) {
                runCatching { BitmapFactory.decodeFile(attachment.filePath)?.asImageBitmap() }.getOrNull()
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
        AttachmentType.AUDIO -> {
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Voice message", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun AgoraBubble(advisory: String, transcript: String?, onShare: () -> Unit) {
    var transcriptExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column {
            Text(
                text = advisory,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (transcript != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { transcriptExpanded = !transcriptExpanded },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = if (transcriptExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null, modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (transcriptExpanded) "Hide debate" else "View debate",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.outline)
                    }
                }

                AnimatedVisibility(visible = transcriptExpanded) {
                    Column {
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = transcript,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(status: String) {
    if (status.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }
}

@Composable
private fun InputRow(
    input: String,
    isGenerating: Boolean,
    showAttachOptions: Boolean,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onToggleAttach: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleAttach) {
            Icon(
                imageVector = if (showAttachOptions) Icons.Default.Close else Icons.Default.Add,
                contentDescription = "Attach",
                tint = if (showAttachOptions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
        OutlinedTextField(
            value = input,
            onValueChange = onInputChanged,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask a question...") },
            enabled = !isGenerating,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            shape = RoundedCornerShape(24.dp)
        )
        Spacer(modifier = Modifier.padding(4.dp))
        IconButton(onClick = onSend, enabled = !isGenerating && input.isNotBlank()) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (!isGenerating && input.isNotBlank())
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
}
