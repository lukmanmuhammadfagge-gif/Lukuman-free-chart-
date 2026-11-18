package com.example.feature.chats

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AppStrings
import com.example.core.UiState
import com.example.core.VoicePlayerManager
import com.example.core.VoiceRecorderHelper
import com.example.domain.model.CachedOrLiveMessage
import com.example.feature.common.AvatarBadge
import com.example.feature.common.MessageBubbleCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversationId: String,
    viewModel: ChatsViewModel,
    currentUserId: String,
    strings: AppStrings,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(conversationId) {
        viewModel.openConversation(conversationId)
    }

    val conversation by viewModel.activeConversationDoc.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val offlineMessages by viewModel.offlineMessages.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    var messageText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<CachedOrLiveMessage?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var playingVoiceId by remember { mutableStateOf<String?>(null) }

    val voiceRecorder = remember(context) { VoiceRecorderHelper(context) }
    val voicePlayer = remember { VoicePlayerManager() }

    DisposableEffect(Unit) {
        onDispose { voicePlayer.stop() }
    }

    LaunchedEffect(actionError) {
        actionError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val conv = conversation ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            viewModel.sendMediaMessage(
                conversation = conv,
                messageType = "image",
                mediaUri = uri,
                mediaName = "photo_${System.currentTimeMillis()}.jpg",
                replyTo = replyingTo
            )
            replyingTo = null
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val conv = conversation ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            val fileName = resolveFileName(context, uri)
            viewModel.sendMediaMessage(
                conversation = conv,
                messageType = "document",
                mediaUri = uri,
                mediaName = fileName,
                replyTo = replyingTo
            )
            replyingTo = null
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                voiceRecorder.startRecording()
                isRecording = true
            } catch (e: Exception) {
                isRecording = false
            }
        }
    }

    val messagesToShow = when (val state = messagesState) {
        is UiState.Success -> state.data
        is UiState.Error -> offlineMessages
        UiState.Loading -> offlineMessages
    }

    LaunchedEffect(messagesToShow.size, conversationId) {
        if (messagesToShow.isNotEmpty()) {
            viewModel.markVisibleMessagesRead(conversationId, messagesToShow)
            listState.animateScrollToItem(messagesToShow.lastIndex)
        }
    }

    val peerName = conversation?.otherParticipantName(currentUserId) ?: strings.chatsTitle
    val peerAvatar = conversation?.otherParticipantAvatar(currentUserId).orEmpty()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.close)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarBadge(name = peerName, imageUrl = peerAvatar, size = 38.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = peerName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    if (replyingTo != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${strings.replyingTo}: ${replyingTo?.text?.ifBlank { replyingTo?.mediaName }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { replyingTo = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = strings.cancel)
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("pick_image_button")
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = strings.shareImage)
                        }
                        IconButton(
                            onClick = {
                                documentPickerLauncher.launch(arrayOf("application/pdf", "text/plain", "*/*"))
                            },
                            modifier = Modifier.testTag("pick_document_button")
                        ) {
                            Icon(Icons.Filled.AttachFile, contentDescription = strings.shareDocument)
                        }
                        IconButton(
                            onClick = {
                                val conv = conversation ?: return@IconButton
                                if (!isRecording) {
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) {
                                        voiceRecorder.startRecording()
                                        isRecording = true
                                    } else {
                                        recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    isRecording = false
                                    val (file, duration) = voiceRecorder.stopRecording()
                                    if (file != null) {
                                        viewModel.sendMediaMessage(
                                            conversation = conv,
                                            messageType = "voice",
                                            localFile = file,
                                            mediaName = file.name,
                                            voiceDurationMs = duration,
                                            replyTo = replyingTo
                                        )
                                        replyingTo = null
                                    }
                                }
                            },
                            modifier = Modifier.testTag("record_voice_button")
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = if (isRecording) strings.stopRecording else strings.recordVoice,
                                tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text(strings.typeMessageHint) },
                            maxLines = 4,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("message_input")
                        )

                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                val conv = conversation ?: return@IconButton
                                val payload = messageText
                                messageText = ""
                                viewModel.sendTextMessage(conv, payload, replyingTo)
                                replyingTo = null
                            },
                            enabled = messageText.isNotBlank() && conversation != null,
                            modifier = Modifier.testTag("send_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = strings.send,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (messagesState is UiState.Loading && messagesToShow.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (messagesToShow.size >= 50) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TextButton(onClick = viewModel::loadMoreMessages) {
                                    Text(strings.loadEarlierMessages)
                                }
                            }
                        }
                    }
                    items(messagesToShow, key = { it.id }) { msg ->
                        MessageBubbleCard(
                            senderName = msg.senderName,
                            showSenderName = false,
                            text = msg.text,
                            messageType = msg.messageType,
                            mediaUrl = msg.mediaUrl,
                            mediaName = msg.mediaName,
                            voiceDurationMs = msg.voiceDurationMs,
                            replyToPreview = msg.replyToPreview,
                            status = msg.status,
                            reactions = msg.reactions,
                            timestampMs = msg.timestampMs,
                            isMine = msg.senderId == currentUserId,
                            isPending = msg.isPendingLocal,
                            isFailed = msg.isFailedLocal,
                            isPlayingVoice = playingVoiceId == msg.id,
                            strings = strings,
                            onReplyClick = { replyingTo = msg },
                            onReact = { emoji -> viewModel.toggleReaction(conversationId, msg, emoji) },
                            onRetryClick = { viewModel.retryMessage(msg.id) },
                            onPlayVoiceClick = {
                                if (msg.mediaUrl.isNotBlank()) {
                                    val started = voicePlayer.playOrToggle(
                                        sourceUrl = msg.mediaUrl,
                                        onCompletion = { playingVoiceId = null },
                                        onError = { playingVoiceId = null }
                                    )
                                    playingVoiceId = if (started) msg.id else null
                                }
                            },
                            onOpenDocumentClick = { url ->
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: Exception) {
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun resolveFileName(context: android.content.Context, uri: Uri): String {
    var name = "document"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) {
            name = cursor.getString(idx) ?: name
        }
    }
    return name
}
