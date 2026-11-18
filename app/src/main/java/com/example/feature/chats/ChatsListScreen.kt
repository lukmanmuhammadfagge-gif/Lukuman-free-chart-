package com.example.feature.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.AppStrings
import com.example.core.UiState
import com.example.feature.common.AvatarBadge
import com.example.feature.common.EmptyStateCard
import com.example.feature.common.formatMessageTime

@Composable
fun ChatsListScreen(
    viewModel: ChatsViewModel,
    currentUserId: String,
    strings: AppStrings,
    onConversationClick: (String) -> Unit,
    onFindContactsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val cachedConversations by viewModel.cachedConversations.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onFindContactsClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("new_chat_fab")
            ) {
                Icon(
                    imageVector = Icons.Filled.AddComment,
                    contentDescription = strings.startNewChat
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = strings.chatsTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text(strings.searchChatsHint) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_chats_input")
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (val state = conversationsState) {
                UiState.Loading -> {
                    if (cachedConversations.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val filteredCache = cachedConversations.filter {
                            searchQuery.isBlank() ||
                                it.otherDisplayName.contains(searchQuery, ignoreCase = true) ||
                                it.lastMessageText.contains(searchQuery, ignoreCase = true)
                        }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 84.dp)
                        ) {
                            items(filteredCache, key = { it.id }) { cached ->
                                ConversationRowCard(
                                    name = cached.otherDisplayName,
                                    avatarUrl = cached.otherAvatarUrl,
                                    lastMessage = cached.lastMessageText,
                                    timestampMs = cached.lastMessageAtMs,
                                    unreadCount = cached.unreadCount,
                                    onClick = { onConversationClick(cached.id) }
                                )
                            }
                        }
                    }
                }

                is UiState.Error -> {
                    if (cachedConversations.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 84.dp)
                        ) {
                            items(cachedConversations, key = { it.id }) { cached ->
                                ConversationRowCard(
                                    name = cached.otherDisplayName,
                                    avatarUrl = cached.otherAvatarUrl,
                                    lastMessage = cached.lastMessageText,
                                    timestampMs = cached.lastMessageAtMs,
                                    unreadCount = cached.unreadCount,
                                    onClick = { onConversationClick(cached.id) }
                                )
                            }
                        }
                    } else {
                        EmptyStateCard(
                            illustrationRes = R.drawable.img_empty_chats,
                            title = strings.errorPrefix,
                            subtitle = state.message,
                            actionLabel = strings.startNewChat,
                            onAction = onFindContactsClick
                        )
                    }
                }

                is UiState.Success -> {
                    val filtered = state.data.filter { conv ->
                        val name = conv.otherParticipantName(currentUserId)
                        searchQuery.isBlank() ||
                            name.contains(searchQuery, ignoreCase = true) ||
                            conv.lastMessageText.contains(searchQuery, ignoreCase = true)
                    }
                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyStateCard(
                                illustrationRes = R.drawable.img_empty_chats,
                                title = strings.emptyChatsTitle,
                                subtitle = strings.emptyChatsSubtitle,
                                actionLabel = strings.startNewChat,
                                onAction = onFindContactsClick
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 84.dp)
                        ) {
                            items(filtered, key = { it.id }) { conv ->
                                val timeMs = (conv.lastMessageAt ?: conv.updatedAt)?.toDate()?.time ?: 0L
                                ConversationRowCard(
                                    name = conv.otherParticipantName(currentUserId),
                                    avatarUrl = conv.otherParticipantAvatar(currentUserId),
                                    lastMessage = conv.lastMessageText.ifBlank { strings.startNewChat },
                                    timestampMs = timeMs,
                                    unreadCount = conv.unreadCountFor(currentUserId),
                                    onClick = { onConversationClick(conv.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRowCard(
    name: String,
    avatarUrl: String,
    lastMessage: String,
    timestampMs: Long,
    unreadCount: Long,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("conversation_card_$name")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarBadge(name = name, imageUrl = avatarUrl, size = 50.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (timestampMs > 0L) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatMessageTime(timestampMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lastMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (unreadCount > 0L) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = unreadCount.toString(),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
