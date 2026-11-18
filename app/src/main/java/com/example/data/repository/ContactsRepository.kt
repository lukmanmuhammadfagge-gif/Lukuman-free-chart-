package com.example.data.repository

import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.core.unwrapFirestoreException
import com.example.domain.model.BlockedUser
import com.example.domain.model.FriendRequest
import com.example.domain.model.PublicUserProfile
import com.example.domain.validation.Validators
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ContactsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    suspend fun searchByUsername(query: String): Result<List<PublicUserProfile>> {
        val uid = requireUserId()
        val normalized = Validators.normalizeUsername(query)
        if (normalized.isBlank()) return Result.success(emptyList())
        return try {
            val snapshot = db.collection("user_public")
                .whereEqualTo("isDiscoverable", true)
                .limit(100)
                .get()
                .await()
            val matches = snapshot.toObjects(
                PublicUserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).filter { profile ->
                profile.userId != uid &&
                    (profile.username.contains(normalized, ignoreCase = true) ||
                        profile.displayName.contains(query.trim(), ignoreCase = true))
            }
            Result.success(matches)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "user_public")
            Result.failure(e)
        }
    }

    suspend fun searchByPhone(phoneE164: String): Result<List<PublicUserProfile>> {
        val uid = requireUserId()
        if (phoneE164.isBlank()) return Result.success(emptyList())
        return try {
            val snapshot = db.collection("user_public")
                .whereEqualTo("isDiscoverable", true)
                .limit(100)
                .get()
                .await()
            val matches = snapshot.toObjects(
                PublicUserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).filter { profile ->
                profile.userId != uid &&
                    profile.allowPhoneSearch &&
                    profile.searchablePhone.isNotBlank() &&
                    profile.searchablePhone == phoneE164
            }
            Result.success(matches)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "user_public")
            Result.failure(e)
        }
    }

    fun observeFriendRequests(userId: String): Flow<List<FriendRequest>> {
        val incomingFlow = db.collection("friend_requests")
            .whereEqualTo("toUserId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    FriendRequest::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
        val outgoingFlow = db.collection("friend_requests")
            .whereEqualTo("fromUserId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    FriendRequest::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
        return combine(incomingFlow, outgoingFlow) { incoming, outgoing ->
            (incoming + outgoing).distinctBy { it.id }
        }.catch { error ->
            val fsErr = unwrapFirestoreException(error)
            if (fsErr == null && error is CancellationException) throw error
            if (auth.currentUser?.uid == userId) {
                handleFirestoreError(fsErr ?: error, OperationType.LIST, "friend_requests")
            }
            throw error
        }
    }

    fun observeBlockedUsers(userId: String): Flow<List<BlockedUser>> =
        db.collection("users").document(userId).collection("blocked")
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    BlockedUser::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val fsErr = unwrapFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, "users/$userId/blocked")
                }
                throw error
            }

    suspend fun getPublicProfilesByIds(userIds: List<String>): Map<String, PublicUserProfile> {
        val uniqueIds = userIds.filter { it.isNotBlank() }.distinct()
        if (uniqueIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, PublicUserProfile>()
        for (id in uniqueIds) {
            try {
                val doc = db.collection("user_public").document(id).get().await()
                val profile = doc.toObject(
                    PublicUserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (profile != null) {
                    result[id] = profile
                }
            } catch (_: Exception) {
                // Skip unavailable profiles cleanly
            }
        }
        return result
    }

    suspend fun sendFriendRequest(
        currentProfile: PublicUserProfile,
        targetProfile: PublicUserProfile
    ): Result<Unit> {
        val uid = requireUserId()
        require(targetProfile.userId != uid) { "Cannot add yourself" }
        val requestId = Validators.deterministicFriendRequestId(uid, targetProfile.userId)
        val docRef = db.collection("friend_requests").document(requestId)
        val payload = mapOf(
            "fromUserId" to uid,
            "toUserId" to targetProfile.userId,
            "fromDisplayName" to currentProfile.displayName.ifBlank { "User" }.take(80),
            "fromUsername" to currentProfile.username.ifBlank { "user_${uid.take(5)}" }.take(30),
            "toDisplayName" to targetProfile.displayName.ifBlank { "User" }.take(80),
            "toUsername" to targetProfile.username.ifBlank { "user" }.take(30),
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun respondToFriendRequest(requestId: String, accept: Boolean): Result<Unit> {
        requireUserId()
        val docRef = db.collection("friend_requests").document(requestId)
        val status = if (accept) "accepted" else "declined"
        return try {
            docRef.update(
                mapOf(
                    "status" to status,
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

    suspend fun blockUser(targetProfile: PublicUserProfile): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("blocked").document(targetProfile.userId)
        val payload = mapOf(
            "blockedUserId" to targetProfile.userId,
            "displayName" to targetProfile.displayName.take(80),
            "username" to targetProfile.username.take(30),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun unblockUser(blockedUserId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("blocked").document(blockedUserId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun reportUser(
        reportedUserId: String,
        reason: String,
        details: String
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("reports").document()
        val payload = mutableMapOf<String, Any>(
            "reporterId" to uid,
            "reportedUserId" to reportedUserId,
            "reason" to reason.trim().take(100),
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (details.isNotBlank()) {
            payload["details"] = details.trim().take(500)
        }
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }
}
