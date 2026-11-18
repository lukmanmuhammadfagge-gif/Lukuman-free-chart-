package com.example.feature.groups

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
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.domain.model.GroupMessage
import com.example.feature.common.AvatarBadge
import com.example.feature.common.MessageBubbleCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    viewModel: GroupsViewModel,
    currentUserId: String,
    strings: AppStrings,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(groupId) {
        viewModel.openGroup(groupId)
    }

    val group by viewModel.activeGroup.collectAsStateWithLifecycle()
    val messagesState by viewModel.groupMessagesState.collectAsStateWithLifecycle()
    val candidates by viewModel.candidateFriends.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var messageText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<GroupMessage?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var playingVoiceId by remember { mutableStateOf<String?>(null) }
    var showManageDialog by remember { mutableStateOf(false) }

    val voiceRecorder = remember(context) { VoiceRecorderHelper(context) }
    val voicePlayer = remember { VoicePlayerManager() }

    DisposableEffect(Unit) {
        onDispose { voicePlayer.stop() }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadGroupAvatar(groupId, uri)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val grp = group ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            viewModel.sendGroupMedia(
                group = grp,
                messageType = "image",
                mediaUri = uri,
                mediaName = "group_photo_${System.currentTimeMillis()}.jpg",
                replyTo = replyingTo
            )
            replyingTo = null
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val grp = group ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            var docName = "document"
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) docName = c.getString(idx) ?: docName
            }
            viewModel.sendGroupMedia(
                group = grp,
                messageType = "document",
                mediaUri = uri,
                mediaName = docName,
                replyTo = replyingTo
            )
            replyingTo = null
        }
    }

    val recordPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceRecorder.startRecording()
            isRecording = true
        }
    }

    val grp = group
    val isAdmin = grp != null && currentUserId in grp.adminIds
    val canPost = grp != null && (!grp.onlyAdminsCanPost || isAdmin)
    val canAddMembers = grp != null && (!grp.onlyAdminsCanAddMembers || isAdmin)

    if (showManageDialog && grp != null) {
        var editName by remember(grp.id) { mutableStateOf(grp.name) }
        var editDesc by remember(grp.id) { mutableStateOf(grp.description) }
        var onlyAdminsPost by remember(grp.id) { mutableStateOf(grp.onlyAdminsCanPost) }
        var onlyAdminsAdd by remember(grp.id) { mutableStateOf(grp.onlyAdminsCanAddMembers) }
        var memberSearch by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showManageDialog = false },
            title = { Text(strings.editGroup) },
            text = {
                LazyColumn(
                    modifier = Modifier.height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isAdmin) {
                        item {
                            OutlinedButton(
                                onClick = {
                                    avatarPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(strings.changeGroupAvatar)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text(strings.groupNameLabel) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = editDesc,
                                onValueChange = { editDesc = it },
                                label = { Text(strings.groupDescLabel) },
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.onlyAdminsPost, modifier = Modifier.weight(1f))
                                Switch(checked = onlyAdminsPost, onCheckedChange = { onlyAdminsPost = it })
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.onlyAdminsAdd, modifier = Modifier.weight(1f))
                                Switch(checked = onlyAdminsAdd, onCheckedChange = { onlyAdminsAdd = it })
                            }
                            Button(
                                onClick = {
                                    viewModel.updateGroup(
                                        grp.id,
                                        editName,
                                        editDesc,
                                        onlyAdminsPost,
                                        onlyAdminsAdd
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(strings.save)
                            }
                        }
                    }

                    if (canAddMembers) {
                        item {
                            Text(strings.addMember, style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = memberSearch,
                                onValueChange = {
                                    memberSearch = it
                                    viewModel.searchUsersForGroup(it)
                                },
                                placeholder = { Text(strings.searchUsersHint) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        items(candidates.filter { it.userId !in grp.memberIds }, key = { "cand_${it.userId}" }) { cand ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${cand.displayName} (@${cand.username})", modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.addMember(grp, cand) }) {
                                    Icon(Icons.Filled.PersonAdd, contentDescription = strings.addMember)
                                }
                            }
                        }
                    }

                    item {
                        Text(strings.membersTitle, style = MaterialTheme.typography.titleMedium)
                    }
                    items(grp.memberIds, key = { "mem_$it" }) { memberId ->
                        val memberName = grp.memberNames[memberId] ?: memberId.take(8)
                        val memberIsOwner = memberId == grp.ownerId
                        val memberIsAdmin = memberId in grp.adminIds
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(memberName, style = MaterialTheme.typography.bodyLarge)
                                if (memberIsOwner) {
                                    Text(strings.ownerBadge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                } else if (memberIsAdmin) {
                                    Text(strings.adminBadge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (isAdmin && !memberIsOwner) {
                                TextButton(onClick = { viewModel.toggleAdmin(grp, memberId) }) {
                                    Text(if (memberIsAdmin) strings.removeAdmin else strings.makeAdmin)
                                }
                                TextButton(onClick = { viewModel.removeMember(grp, memberId) }) {
                                    Text(strings.removeMember, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageDialog = false }) {
                    Text(strings.close)
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.close)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarBadge(
                            name = grp?.name ?: strings.groupsTitle,
                            imageUrl = grp?.avatarUrl.orEmpty(),
                            size = 38.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = grp?.name ?: strings.groupsTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (grp != null) {
                                Text(
                                    text = "${grp.memberIds.size} ${strings.membersTitle}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showManageDialog = true },
                        modifier = Modifier.testTag("manage_group_button")
                    ) {
                        Icon(Icons.Filled.ManageAccounts, contentDescription = strings.editGroup)
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
                if (grp != null && !canPost) {
                    Text(
                        text = strings.adminOnlyPostNotice,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
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
                                    IconButton(onClick = { replyingTo = null }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Filled.Close, contentDescription = strings.cancel)
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }) {
                                Icon(Icons.Filled.Image, contentDescription = strings.shareImage)
                            }
                            IconButton(onClick = {
                                docPickerLauncher.launch(arrayOf("*/*"))
                            }) {
                                Icon(Icons.Filled.AttachFile, contentDescription = strings.shareDocument)
                            }
                            IconButton(onClick = {
                                val activeGrp = grp ?: return@IconButton
                                if (!isRecording) {
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) {
                                        voiceRecorder.startRecording()
                                        isRecording = true
                                    } else {
                                        recordPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    isRecording = false
                                    val (file, duration) = voiceRecorder.stopRecording()
                                    if (file != null) {
                                        viewModel.sendGroupMedia(
                                            group = activeGrp,
                                            messageType = "voice",
                                            localFile = file,
                                            mediaName = file.name,
                                            voiceDurationMs = duration,
                                            replyTo = replyingTo
                                        )
                                        replyingTo = null
                                    }
                                }
                            }) {
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
                                    .testTag("group_message_input")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    val activeGrp = grp ?: return@IconButton
                                    val payload = messageText
                                    messageText = ""
                                    viewModel.sendGroupText(activeGrp, payload, replyingTo)
                                    replyingTo = null
                                },
                                enabled = messageText.isNotBlank() && grp != null,
                                modifier = Modifier.testTag("send_group_message_button")
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
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = messagesState) {
                UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is UiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }

                is UiState.Success -> {
                    LaunchedEffect(state.data.size) {
                        if (state.data.isNotEmpty()) {
                            listState.animateScrollToItem(state.data.lastIndex)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (state.data.size >= 50) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    TextButton(onClick = viewModel::loadMoreMessages) {
                                        Text(strings.loadEarlierMessages)
                                    }
                                }
                            }
                        }
                        items(state.data, key = { it.id }) { msg ->
                            val ts = (msg.createdAt ?: msg.updatedAt)?.toDate()?.time ?: 0L
                            MessageBubbleCard(
                                senderName = msg.senderName,
                                showSenderName = true,
                                text = msg.text,
                                messageType = msg.messageType,
                                mediaUrl = msg.mediaUrl,
                                mediaName = msg.mediaName,
                                voiceDurationMs = msg.voiceDurationMs,
                                replyToPreview = msg.replyToPreview,
                                status = "sent",
                                reactions = msg.reactions,
                                timestampMs = ts,
                                isMine = msg.senderId == currentUserId,
                                isPending = false,
                                isFailed = false,
                                isPlayingVoice = playingVoiceId == msg.id,
                                strings = strings,
                                onReplyClick = { replyingTo = msg },
                                onReact = { emoji -> viewModel.toggleReaction(groupId, msg, emoji) },
                                onRetryClick = {},
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
}
