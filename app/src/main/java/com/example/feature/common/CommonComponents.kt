package com.example.feature.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.AppLanguage
import com.example.core.AppStrings
import com.example.core.CountryCodes
import com.example.core.CountryDialCode
import com.example.ui.theme.AzureAccent
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.RoyalBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AvatarBadge(
    name: String,
    imageUrl: String = "",
    size: Dp = 46.dp,
    isOnline: Boolean = false,
    modifier: Modifier = Modifier
) {
    val initials = remember(name) {
        name.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "L" }
    }
    Box(modifier = modifier.size(size)) {
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(RoyalBlue, NavyDeep)
                        )
                    )
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(EmeraldVerified)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    @DrawableRes illustrationRes: Int,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = illustrationRes),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(176.dp)
                .clip(RoundedCornerShape(28.dp))
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.testTag("empty_state_action_button")
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun CountryCodeDropdown(
    selectedDialCode: String,
    language: AppLanguage,
    onSelect: (CountryDialCode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = remember(selectedDialCode) { CountryCodes.findByDialCode(selectedDialCode) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .height(54.dp)
                .testTag("country_code_selector")
        ) {
            Text(text = "${selected.flagEmoji} ${selected.dialCode}")
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Select country code"
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            CountryCodes.all.forEach { country ->
                val label = if (language == AppLanguage.HAUSA) country.nameHa else country.nameEn
                DropdownMenuItem(
                    text = { Text("${country.flagEmoji} $label (${country.dialCode})") },
                    onClick = {
                        expanded = false
                        onSelect(country)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageBubbleCard(
    senderName: String,
    showSenderName: Boolean,
    text: String,
    messageType: String,
    mediaUrl: String,
    mediaName: String,
    voiceDurationMs: Long,
    replyToPreview: String,
    status: String,
    reactions: Map<String, String>,
    timestampMs: Long,
    isMine: Boolean,
    isPending: Boolean,
    isFailed: Boolean,
    isPlayingVoice: Boolean,
    strings: AppStrings,
    onReplyClick: () -> Unit,
    onReact: (String) -> Unit,
    onRetryClick: () -> Unit,
    onPlayVoiceClick: () -> Unit,
    onOpenDocumentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showActions by remember { mutableStateOf(false) }
    val quickEmojis = listOf("👍", "❤️", "😂", "🙏", "🔥")

    val bubbleColor = if (isMine) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            tonalElevation = if (isMine) 2.dp else 0.dp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clickable { showActions = !showActions }
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (showSenderName && !isMine) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isMine) AzureAccent else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                if (replyToPreview.isNotBlank()) {
                    Surface(
                        color = contentColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = replyToPreview,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                when (messageType) {
                    "image" -> {
                        if (mediaUrl.isNotBlank()) {
                            AsyncImage(
                                model = mediaUrl,
                                contentDescription = strings.imageLabel,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            if (text.isNotBlank()) Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    "document" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(contentColor.copy(alpha = 0.12f))
                                .clickable(enabled = mediaUrl.isNotBlank()) {
                                    onOpenDocumentClick(mediaUrl)
                                }
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = strings.documentLabel,
                                tint = contentColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = mediaName.ifBlank { strings.documentLabel },
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (text.isNotBlank()) Spacer(modifier = Modifier.height(6.dp))
                    }

                    "voice" -> {
                        val secs = (voiceDurationMs / 1000L).coerceAtLeast(1L)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(contentColor.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            IconButton(
                                onClick = onPlayVoiceClick,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlayingVoice) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = strings.voiceMessageLabel,
                                    tint = contentColor
                                )
                            }
                            Text(
                                text = "${strings.voiceMessageLabel} • ${secs}s",
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor
                            )
                        }
                    }
                }

                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = formatMessageTime(timestampMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.72f)
                    )
                    if (isMine) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when {
                            isFailed -> {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = strings.failedSend,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            isPending -> {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = strings.pendingOffline,
                                    tint = contentColor.copy(alpha = 0.72f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            status == "read" -> {
                                Icon(
                                    imageVector = Icons.Filled.DoneAll,
                                    contentDescription = strings.statusRead,
                                    tint = AzureAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            status == "delivered" -> {
                                Icon(
                                    imageVector = Icons.Filled.DoneAll,
                                    contentDescription = strings.statusDelivered,
                                    tint = contentColor.copy(alpha = 0.8f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = strings.statusSent,
                                    tint = contentColor.copy(alpha = 0.72f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isFailed) {
            TextButton(onClick = onRetryClick) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = strings.retrySend,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(strings.retrySend)
            }
        }

        if (reactions.isNotEmpty()) {
            val grouped = reactions.values.groupingBy { it }.eachCount()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                grouped.forEach { (emoji, count) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.clickable { onReact(emoji) }
                    ) {
                        Text(
                            text = if (count > 1) "$emoji $count" else emoji,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        if (showActions && !isPending && !isFailed) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    quickEmojis.forEach { emoji ->
                        Text(
                            text = emoji,
                            modifier = Modifier
                                .clickable {
                                    showActions = false
                                    onReact(emoji)
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        )
                    }
                    TextButton(onClick = {
                        showActions = false
                        onReplyClick()
                    }) {
                        Text("Reply", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

fun formatMessageTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
}
