package com.example.data.repository

import android.net.Uri
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.core.unwrapFirestoreException
import com.example.data.local.CachedConversationEntity
import com.example.data.local.CachedMessageEntity
import com.example.data.local.ConversationDao
import com.example.data.local.MessageDao
import com.example.data.local.PendingOutboxDao
import com.example.data.local.PendingOutboxEntity
import com.example.domain.model.CachedOrLiveMessage
import com.example.domain.model.Conversation
import com.example.domain.model.DirectMessage
import com.example.domain.model.PublicUserProfile
import com.example.domain.validation.Validators
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val pendingOutboxDao: PendingOutboxDao,
    private val storage: FirebaseStorage? = null
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeCachedConversations(): Flow<List<CachedConversationEntity>> =
        conversationDao.observeConversations()

    fun observeConversations(userId: String): Flow<List<Conversation>> =
        db.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .snapshots()
            .map { snapshot ->
                val list = snapshot.toObjects(
                    Conversation::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending {
                    (it.lastMessageAt ?: it.updatedAt ?: it.createdAt)?.toDate()?.time ?: 0L
                }
                conversationDao.upsertAll(
                    list.map { conv ->
                        CachedConversationEntity(
                            id = conv.id,
                            otherUserId = conv.otherParticipantId(userId),
                            otherDisplayName = conv.otherParticipantName(userId),
                            otherAvatarUrl = conv.otherParticipantAvatar(userId),
                            lastMessageText = conv.lastMessageText,
                            lastMessageType = conv.lastMessageType,
                            lastMessageSenderId = conv.lastMessageSenderId,
                            lastMessageAtMs = (conv.lastMessageAt ?: conv.updatedAt)?.toDate()?.time ?: 0L,
                            unreadCount = conv.unreadCountFor(userId)
                        )
                    }
                )
                list
            }
            .catch { error ->
                val fsErr = unwrapFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, "conversations")
                }
                throw error
            }

    suspend fun getOrCreateDirectConversation(
        currentUser: PublicUserProfile,
        otherUser: PublicUserProfile
    ): Result<String> {
        val uid = requireUserId()
        val convId = Validators.deterministicConversationId(uid, otherUser.userId)
        val docRef = db.collection("conversations").document(convId)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "participantIds" to listOf(uid, otherUser.userId).sorted(),
                    "participantNames" to mapOf(
                        uid to currentUser.displayName.ifBlank { "User" }.take(80),
                        otherUser.userId to otherUser.displayName.ifBlank { "User" }.take(80)
                    ),
                    "participantAvatars" to mapOf(
                        uid to currentUser.avatarUrl.take(500),
                        otherUser.userId to otherUser.avatarUrl.take(500)
                    ),
                    "lastMessageText" to "",
                    "lastMessageType" to "text",
                    "lastMessageSenderId" to "",
                    "unreadCounts" to mapOf(uid to 0L, otherUser.userId to 0L),
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeConversationDoc(conversationId: String): Flow<Conversation?> =
        db.collection("conversations").document(conversationId)
            .snapshots()
            .map { snap ->
                snap.toObject(
                    Conversation::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val fsErr = unwrapFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                handleFirestoreError(fsErr ?: error, OperationType.GET, "conversations/$conversationId")
                throw error
            }

    fun observeMessagesWithCache(
        conversationId: String,
        userId: String,
        pageLimit: Long
    ): Flow<List<CachedOrLiveMessage>> {
        val liveFlow = db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .whereArrayContains("participantIds", userId)
            .limit(pageLimit)
            .snapshots()
            .map { snapshot ->
                val messages = snapshot.toObjects(
                    DirectMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { (it.createdAt ?: it.updatedAt)?.toDate()?.time ?: 0L }

                messageDao.upsertAll(
                    messages.map { msg ->
                        CachedMessageEntity(
                            id = msg.id,
                            conversationId = msg.conversationId,
                            senderId = msg.senderId,
                            senderName = msg.senderName,
                            text = msg.text,
                            messageType = msg.messageType,
                            mediaUrl = msg.mediaUrl,
                            mediaName = msg.mediaName,
                            voiceDurationMs = msg.voiceDurationMs,
                            replyToMessageId = msg.replyToMessageId,
                            replyToPreview = msg.replyToPreview,
                            status = msg.status,
                            reactionsJson = JSONObject(msg.reactions).toString(),
                            timestampMs = (msg.createdAt ?: msg.updatedAt)?.toDate()?.time ?: 0L
                        )
                    }
                )
                messages
            }
            .catch { error ->
                val fsErr = unwrapFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(
                        fsErr ?: error,
                        OperationType.LIST,
                        "conversations/$conversationId/messages"
                    )
                }
                throw error
            }

        val pendingFlow = pendingOutboxDao.observePendingForConversation(conversationId)

        return combine(liveFlow, pendingFlow) { live, pending ->
            val liveMapped = live.map { msg ->
                CachedOrLiveMessage(
                    id = msg.id,
                    conversationId = msg.conversationId,
                    senderId = msg.senderId,
                    senderName = msg.senderName,
                    text = msg.text,
                    messageType = msg.messageType,
                    mediaUrl = msg.mediaUrl,
                    mediaName = msg.mediaName,
                    voiceDurationMs = msg.voiceDurationMs,
                    replyToMessageId = msg.replyToMessageId,
                    replyToPreview = msg.replyToPreview,
                    status = msg.status,
                    reactions = msg.reactions,
                    timestampMs = (msg.createdAt ?: msg.updatedAt)?.toDate()?.time ?: 0L
                )
            }
            val pendingMapped = pending.map { item ->
                CachedOrLiveMessage(
                    id = item.localId,
                    conversationId = item.conversationId,
                    senderId = item.senderId,
                    senderName = item.senderName,
                    text = item.text,
                    messageType = item.messageType,
                    mediaUrl = item.localFilePath,
                    mediaName = item.mediaName,
                    voiceDurationMs = item.voiceDurationMs,
                    replyToMessageId = item.replyToMessageId,
                    replyToPreview = item.replyToPreview,
                    status = "sent",
                    reactions = emptyMap(),
                    timestampMs = item.createdAtMs,
                    isPendingLocal = !item.failed,
                    isFailedLocal = item.failed
                )
            }
            (liveMapped + pendingMapped).sortedBy { it.timestampMs }
        }
    }

    fun observeOfflineMessages(conversationId: String): Flow<List<CachedOrLiveMessage>> =
        combine(
            messageDao.observeMessages(conversationId),
            pendingOutboxDao.observePendingForConversation(conversationId)
        ) { cached, pending ->
            val cachedMapped = cached.map { item ->
                CachedOrLiveMessage(
                    id = item.id,
                    conversationId = item.conversationId,
                    senderId = item.senderId,
                    senderName = item.senderName,
                    text = item.text,
                    messageType = item.messageType,
                    mediaUrl = item.mediaUrl,
                    mediaName = item.mediaName,
                    voiceDurationMs = item.voiceDurationMs,
                    replyToMessageId = item.replyToMessageId,
                    replyToPreview = item.replyToPreview,
                    status = item.status,
                    reactions = parseReactionsJson(item.reactionsJson),
                    timestampMs = item.timestampMs
                )
            }
            val pendingMapped = pending.map { item ->
                CachedOrLiveMessage(
                    id = item.localId,
                    conversationId = item.conversationId,
                    senderId = item.senderId,
                    senderName = item.senderName,
                    text = item.text,
                    messageType = item.messageType,
                    mediaUrl = item.localFilePath,
                    mediaName = item.mediaName,
                    voiceDurationMs = item.voiceDurationMs,
                    replyToMessageId = item.replyToMessageId,
                    replyToPreview = item.replyToPreview,
                    status = "sent",
                    reactions = emptyMap(),
                    timestampMs = item.createdAtMs,
                    isPendingLocal = !item.failed,
                    isFailedLocal = item.failed
                )
            }
            (cachedMapped + pendingMapped).sortedBy { it.timestampMs }
        }

    suspend fun sendDirectMessage(
        conversationId: String,
        senderName: String,
        otherUserId: String,
        otherUserName: String,
        otherUserAvatar: String,
        text: String,
        messageType: String = "text",
        mediaUri: Uri? = null,
        localFile: File? = null,
        mediaName: String = "",
        voiceDurationMs: Long = 0L,
        replyToMessageId: String = "",
        replyToPreview: String = ""
    ): Result<Unit> {
        val uid = requireUserId()
        val localId = "local_${UUID.randomUUID()}"
        val outboxItem = PendingOutboxEntity(
            localId = localId,
            conversationId = conversationId,
            senderId = uid,
            senderName = senderName.ifBlank { "User" }.take(80),
            recipientId = otherUserId,
            recipientName = otherUserName,
            recipientAvatar = otherUserAvatar,
            text = text.trim().take(4000),
            messageType = messageType,
            localFilePath = localFile?.absolutePath ?: mediaUri?.toString().orEmpty(),
            mediaName = mediaName.take(200),
            voiceDurationMs = voiceDurationMs,
            replyToMessageId = replyToMessageId,
            replyToPreview = replyToPreview.take(200),
            createdAtMs = System.currentTimeMillis(),
            failed = false
        )
        pendingOutboxDao.upsert(outboxItem)
        return dispatchOutboxItem(outboxItem, mediaUri, localFile)
    }

    suspend fun retryPendingMessage(localId: String): Result<Unit> {
        val items = pendingOutboxDao.getAllPending()
        val item = items.firstOrNull { it.localId == localId }
            ?: return Result.failure(IllegalStateException("Pending message not found"))
        pendingOutboxDao.setFailed(localId, false)
        val uri = item.localFilePath.takeIf { it.startsWith("content://") }?.let { Uri.parse(it) }
        val file = item.localFilePath.takeIf { it.startsWith("/") }?.let { File(it) }
        return dispatchOutboxItem(item, uri, file)
    }

    private suspend fun dispatchOutboxItem(
        item: PendingOutboxEntity,
        mediaUri: Uri?,
        localFile: File?
    ): Result<Unit> {
        val uid = requireUserId()
        return try {
            var uploadedUrl = ""
            if (item.messageType != "text" && (mediaUri != null || localFile != null)) {
                val storageInstance = storage
                    ?: throw IllegalStateException("Firebase Storage is not enabled")
                val fileName = "${System.currentTimeMillis()}_${item.mediaName.ifBlank { "media" }}"
                val storageRef = storageInstance.reference
                    .child("chat_media/${item.conversationId}/$uid/$fileName")
                if (localFile != null && localFile.exists()) {
                    storageRef.putFile(Uri.fromFile(localFile)).await()
                } else if (mediaUri != null) {
                    storageRef.putFile(mediaUri).await()
                }
                uploadedUrl = storageRef.downloadUrl.await().toString()
            }

            val participants = listOf(uid, item.recipientId).sorted()
            val msgRef = db.collection("conversations")
                .document(item.conversationId)
                .collection("messages")
                .document()

            val msgPayload = mutableMapOf<String, Any>(
                "conversationId" to item.conversationId,
                "senderId" to uid,
                "senderName" to item.senderName,
                "participantIds" to participants,
                "text" to item.text,
                "messageType" to item.messageType,
                "status" to "sent",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (uploadedUrl.isNotBlank()) msgPayload["mediaUrl"] = uploadedUrl
            if (item.mediaName.isNotBlank()) msgPayload["mediaName"] = item.mediaName
            if (item.voiceDurationMs > 0L) msgPayload["voiceDurationMs"] = item.voiceDurationMs
            if (item.replyToMessageId.isNotBlank()) msgPayload["replyToMessageId"] = item.replyToMessageId
            if (item.replyToPreview.isNotBlank()) msgPayload["replyToPreview"] = item.replyToPreview

            val previewText = when (item.messageType) {
                "image" -> "📷 Photo"
                "document" -> "📄 ${item.mediaName.ifBlank { "Document" }}"
                "voice" -> "🎤 Voice note"
                else -> item.text
            }.take(500)

            val convRef = db.collection("conversations").document(item.conversationId)
            db.runBatch { batch ->
                batch.set(msgRef, msgPayload)
                batch.update(
                    convRef,
                    mapOf(
                        "lastMessageText" to previewText,
                        "lastMessageType" to item.messageType,
                        "lastMessageSenderId" to uid,
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "unreadCounts.${item.recipientId}" to FieldValue.increment(1),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            pendingOutboxDao.deleteById(item.localId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            pendingOutboxDao.setFailed(item.localId, true)
            handleFirestoreError(e, OperationType.CREATE, "conversations/${item.conversationId}/messages")
            Result.failure(e)
        }
    }

    suspend fun markConversationMessagesDeliveredOrRead(
        conversationId: String,
        messages: List<CachedOrLiveMessage>,
        readReceiptsEnabled: Boolean
    ) {
        val uid = auth.currentUser?.uid ?: return
        val incomingToMark = messages.filter {
            !it.isPendingLocal && !it.isFailedLocal && it.senderId != uid && it.status != "read"
        }
        val targetStatus = if (readReceiptsEnabled) "read" else "delivered"
        try {
            val convRef = db.collection("conversations").document(conversationId)
            db.runBatch { batch ->
                batch.update(
                    convRef,
                    mapOf(
                        "unreadCounts.$uid" to 0L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                incomingToMark.take(25).forEach { msg ->
                    if (msg.status != targetStatus) {
                        val msgRef = convRef.collection("messages").document(msg.id)
                        batch.update(
                            msgRef,
                            mapOf(
                                "status" to targetStatus,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }.await()
        } catch (_: Exception) {
            // Non-critical status sync
        }
    }

    suspend fun toggleReaction(
        conversationId: String,
        messageId: String,
        currentReactions: Map<String, String>,
        emoji: String
    ): Result<Unit> {
        val uid = requireUserId()
        val updated = currentReactions.toMutableMap()
        if (updated[uid] == emoji) {
            updated.remove(uid)
        } else {
            updated[uid] = emoji
        }
        val msgRef = db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .document(messageId)
        return try {
            msgRef.update(
                mapOf(
                    "reactions" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    private fun parseReactionsJson(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return try {
            val json = JSONObject(raw)
            val result = mutableMapOf<String, String>()
            json.keys().forEach { key ->
                result[key] = json.optString(key)
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun clearLocalCaches() {
        conversationDao.clearAll()
        messageDao.clearAll()
        pendingOutboxDao.clearAll()
    }
}
