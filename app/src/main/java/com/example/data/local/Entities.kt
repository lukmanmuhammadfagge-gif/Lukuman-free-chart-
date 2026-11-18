package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_conversations")
data class CachedConversationEntity(
    @PrimaryKey val id: String,
    val otherUserId: String,
    val otherDisplayName: String,
    val otherAvatarUrl: String,
    val lastMessageText: String,
    val lastMessageType: String,
    val lastMessageSenderId: String,
    val lastMessageAtMs: Long,
    val unreadCount: Long
)

@Entity(tableName = "cached_messages")
data class CachedMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val messageType: String,
    val mediaUrl: String,
    val mediaName: String,
    val voiceDurationMs: Long,
    val replyToMessageId: String,
    val replyToPreview: String,
    val status: String,
    val reactionsJson: String,
    val timestampMs: Long
)

@Entity(tableName = "pending_outbox")
data class PendingOutboxEntity(
    @PrimaryKey val localId: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String,
    val recipientName: String,
    val recipientAvatar: String,
    val text: String,
    val messageType: String,
    val localFilePath: String,
    val mediaName: String,
    val voiceDurationMs: Long,
    val replyToMessageId: String,
    val replyToPreview: String,
    val createdAtMs: Long,
    val failed: Boolean = false
)
