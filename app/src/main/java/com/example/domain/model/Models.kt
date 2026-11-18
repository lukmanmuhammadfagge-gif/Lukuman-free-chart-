package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class PublicUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val allowPhoneSearch: Boolean = false,
    val searchablePhone: String = "",
    val isDiscoverable: Boolean = true,
    val isOnline: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PrivateUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val countryCode: String = "+234",
    val isPhoneVerified: Boolean = false,
    val preferredLanguage: String = "ha",
    val themeMode: String = "system",
    val notificationsEnabled: Boolean = true,
    val allowPhoneSearch: Boolean = false,
    val readReceiptsEnabled: Boolean = true,
    val fcmToken: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BlockedUser(
    @DocumentId val id: String = "",
    val blockedUserId: String = "",
    val displayName: String = "",
    val username: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FriendRequest(
    @DocumentId val id: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val fromDisplayName: String = "",
    val fromUsername: String = "",
    val toDisplayName: String = "",
    val toUsername: String = "",
    val status: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantAvatars: Map<String, String> = emptyMap(),
    val lastMessageText: String = "",
    val lastMessageType: String = "text",
    val lastMessageSenderId: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val unreadCounts: Map<String, Long> = emptyMap(),
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun otherParticipantId(currentUserId: String): String =
        participantIds.firstOrNull { it != currentUserId } ?: ""

    fun otherParticipantName(currentUserId: String): String {
        val otherId = otherParticipantId(currentUserId)
        return participantNames[otherId].orEmpty().ifBlank { "Chat User" }
    }

    fun otherParticipantAvatar(currentUserId: String): String {
        val otherId = otherParticipantId(currentUserId)
        return participantAvatars[otherId].orEmpty()
    }

    fun unreadCountFor(currentUserId: String): Long =
        unreadCounts[currentUserId] ?: 0L
}

@IgnoreExtraProperties
data class DirectMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val participantIds: List<String> = emptyList(),
    val text: String = "",
    val messageType: String = "text",
    val mediaUrl: String = "",
    val mediaName: String = "",
    val voiceDurationMs: Long = 0L,
    val replyToMessageId: String = "",
    val replyToPreview: String = "",
    val status: String = "sent",
    val reactions: Map<String, String> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatGroup(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val avatarUrl: String = "",
    val ownerId: String = "",
    val adminIds: List<String> = emptyList(),
    val memberIds: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val onlyAdminsCanPost: Boolean = false,
    val onlyAdminsCanAddMembers: Boolean = true,
    val lastMessageText: String = "",
    val lastMessageSenderName: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GroupMessage(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val memberIds: List<String> = emptyList(),
    val text: String = "",
    val messageType: String = "text",
    val mediaUrl: String = "",
    val mediaName: String = "",
    val voiceDurationMs: Long = 0L,
    val replyToMessageId: String = "",
    val replyToPreview: String = "",
    val reactions: Map<String, String> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class CachedOrLiveMessage(
    val id: String,
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
    val reactions: Map<String, String>,
    val timestampMs: Long,
    val isPendingLocal: Boolean = false,
    val isFailedLocal: Boolean = false
)
