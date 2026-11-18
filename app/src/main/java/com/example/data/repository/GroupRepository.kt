package com.example.data.repository

import android.net.Uri
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.core.unwrapFirestoreException
import com.example.domain.model.ChatGroup
import com.example.domain.model.GroupMessage
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File

class GroupRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage? = null
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeGroups(userId: String): Flow<List<ChatGroup>> =
        db.collection("groups")
            .whereArrayContains("memberIds", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ChatGroup::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending {
                    (it.lastMessageAt ?: it.updatedAt ?: it.createdAt)?.toDate()?.time ?: 0L
                }
            }
            .catch { error ->
                val fsErr = unwrapFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, "groups")
                }
                throw error
            }

    fun observeGroup(groupId: String): Flow<ChatGroup?> =
        db.collection("groups").document(groupId)
            .snapshots()
            .map { snap ->
                snap.toObject(
                    ChatGroup::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val fsErr = unwrapFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                handleFirestoreError(fsErr ?: error, OperationType.GET, "groups/$groupId")
                throw error
            }

    fun observeGroupMessages(groupId: String, userId: String, pageLimit: Long): Flow<List<GroupMessage>> =
        db.collection("groups").document(groupId)
            .collection("messages")
            .whereArrayContains("memberIds", userId)
            .limit(pageLimit)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    GroupMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { (it.createdAt ?: it.updatedAt)?.toDate()?.time ?: 0L }
            }
            .catch { error ->
                val fsErr = unwrapFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, "groups/$groupId/messages")
                }
                throw error
            }

    suspend fun createGroup(
        name: String,
        description: String,
        creatorProfile: PublicUserProfile,
        initialMembers: List<PublicUserProfile>,
        onlyAdminsCanPost: Boolean,
        onlyAdminsCanAddMembers: Boolean
    ): Result<String> {
        val uid = requireUserId()
        val cleanName = name.trim().take(80)
        require(Validators.isValidGroupName(cleanName)) { "Group name is required" }

        val memberIds = (listOf(uid) + initialMembers.map { it.userId }).distinct().take(50)
        val memberNames = buildMap {
            put(uid, creatorProfile.displayName.ifBlank { "Owner" }.take(80))
            initialMembers.forEach { put(it.userId, it.displayName.ifBlank { "Member" }.take(80)) }
        }
        val docRef = db.collection("groups").document()
        val payload = mapOf(
            "name" to cleanName,
            "description" to description.trim().take(300),
            "avatarUrl" to "",
            "ownerId" to uid,
            "adminIds" to listOf(uid),
            "memberIds" to memberIds,
            "memberNames" to memberNames,
            "onlyAdminsCanPost" to onlyAdminsCanPost,
            "onlyAdminsCanAddMembers" to onlyAdminsCanAddMembers,
            "lastMessageText" to "",
            "lastMessageSenderName" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateGroupDetails(
        groupId: String,
        name: String,
        description: String,
        onlyAdminsCanPost: Boolean,
        onlyAdminsCanAddMembers: Boolean
    ): Result<Unit> {
        requireUserId()
        val docRef = db.collection("groups").document(groupId)
        return try {
            docRef.update(
                mapOf(
                    "name" to name.trim().take(80),
                    "description" to description.trim().take(300),
                    "onlyAdminsCanPost" to onlyAdminsCanPost,
                    "onlyAdminsCanAddMembers" to onlyAdminsCanAddMembers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun uploadGroupAvatar(groupId: String, imageUri: Uri): Result<String> {
        requireUserId()
        val storageInstance = storage
            ?: return Result.failure(IllegalStateException("Firebase Storage is not configured"))
        return try {
            val ref = storageInstance.reference.child("group_avatars/$groupId/avatar_${System.currentTimeMillis()}.jpg")
            ref.putFile(imageUri).await()
            val url = ref.downloadUrl.await().toString()
            db.collection("groups").document(groupId)
                .update(mapOf("avatarUrl" to url, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addMember(group: ChatGroup, member: PublicUserProfile): Result<Unit> {
        requireUserId()
        if (member.userId in group.memberIds) return Result.success(Unit)
        if (group.memberIds.size >= 50) {
            return Result.failure(IllegalStateException("Group limit of 50 members reached"))
        }
        val updatedMembers = (group.memberIds + member.userId).distinct()
        val updatedNames = group.memberNames.toMutableMap().apply {
            put(member.userId, member.displayName.ifBlank { "Member" }.take(80))
        }
        val docRef = db.collection("groups").document(group.id)
        return try {
            docRef.update(
                mapOf(
                    "memberIds" to updatedMembers,
                    "memberNames" to updatedNames,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun removeMember(group: ChatGroup, targetUserId: String): Result<Unit> {
        requireUserId()
        if (targetUserId == group.ownerId) {
            return Result.failure(IllegalStateException("Cannot remove group owner"))
        }
        val updatedMembers = group.memberIds.filterNot { it == targetUserId }
        val updatedAdmins = group.adminIds.filterNot { it == targetUserId }
        val updatedNames = group.memberNames.toMutableMap().apply { remove(targetUserId) }
        val docRef = db.collection("groups").document(group.id)
        return try {
            docRef.update(
                mapOf(
                    "memberIds" to updatedMembers,
                    "adminIds" to updatedAdmins,
                    "memberNames" to updatedNames,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleAdminRole(group: ChatGroup, targetUserId: String): Result<Unit> {
        requireUserId()
        if (targetUserId == group.ownerId) {
            return Result.failure(IllegalStateException("Owner is always an administrator"))
        }
        val updatedAdmins = if (targetUserId in group.adminIds) {
            group.adminIds.filterNot { it == targetUserId }
        } else {
            (group.adminIds + targetUserId).distinct().take(20)
        }
        val docRef = db.collection("groups").document(group.id)
        return try {
            docRef.update(
                mapOf(
                    "adminIds" to updatedAdmins,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun sendGroupMessage(
        group: ChatGroup,
        senderName: String,
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
        val groupRef = db.collection("groups").document(group.id)
        val msgRef = groupRef.collection("messages").document()
        return try {
            var uploadedUrl = ""
            if (messageType != "text" && (mediaUri != null || localFile != null)) {
                val storageInstance = storage
                    ?: throw IllegalStateException("Firebase Storage is not enabled")
                val fileName = "${System.currentTimeMillis()}_${mediaName.ifBlank { "media" }}"
                val storageRef = storageInstance.reference
                    .child("group_media/${group.id}/$uid/$fileName")
                if (localFile != null && localFile.exists()) {
                    storageRef.putFile(Uri.fromFile(localFile)).await()
                } else if (mediaUri != null) {
                    storageRef.putFile(mediaUri).await()
                }
                uploadedUrl = storageRef.downloadUrl.await().toString()
            }

            val payload = mutableMapOf<String, Any>(
                "groupId" to group.id,
                "senderId" to uid,
                "senderName" to senderName.ifBlank { "Member" }.take(80),
                "memberIds" to group.memberIds,
                "text" to text.trim().take(4000),
                "messageType" to messageType,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (uploadedUrl.isNotBlank()) payload["mediaUrl"] = uploadedUrl
            if (mediaName.isNotBlank()) payload["mediaName"] = mediaName.take(200)
            if (voiceDurationMs > 0L) payload["voiceDurationMs"] = voiceDurationMs
            if (replyToMessageId.isNotBlank()) payload["replyToMessageId"] = replyToMessageId
            if (replyToPreview.isNotBlank()) payload["replyToPreview"] = replyToPreview.take(200)

            val preview = when (messageType) {
                "image" -> "📷 Photo"
                "document" -> "📄 ${mediaName.ifBlank { "Document" }}"
                "voice" -> "🎤 Voice note"
                else -> text.trim()
            }.take(500)

            db.runBatch { batch ->
                batch.set(msgRef, payload)
                batch.update(
                    groupRef,
                    mapOf(
                        "lastMessageText" to preview,
                        "lastMessageSenderName" to senderName.ifBlank { "Member" }.take(80),
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleGroupReaction(
        groupId: String,
        messageId: String,
        currentReactions: Map<String, String>,
        emoji: String
    ): Result<Unit> {
        val uid = requireUserId()
        val updated = currentReactions.toMutableMap()
        if (updated[uid] == emoji) updated.remove(uid) else updated[uid] = emoji
        val msgRef = db.collection("groups").document(groupId).collection("messages").document(messageId)
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
}
