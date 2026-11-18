package com.example.data.repository

import android.net.Uri
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.core.unwrapFirestoreException
import com.example.domain.model.PrivateUserProfile
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

class ProfileRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage? = null
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observePublicProfile(userId: String): Flow<PublicUserProfile?> =
        db.collection("user_public").document(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    PublicUserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val fsErr = unwrapFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(fsErr ?: error, OperationType.GET, "user_public/$userId")
                }
                throw error
            }

    fun observePrivateProfile(userId: String): Flow<PrivateUserProfile?> =
        db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    PrivateUserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val fsErr = unwrapFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(fsErr ?: error, OperationType.GET, "users/$userId")
                }
                throw error
            }

    suspend fun ensureUserProfilesInitialized(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        val uid = user.uid
        return try {
            val publicRef = db.collection("user_public").document(uid)
            val privateRef = db.collection("users").document(uid)

            val existingPublic = publicRef.get().await()
            if (!existingPublic.exists()) {
                val displayName = (user.displayName ?: "").trim().ifBlank { "Lukuman Chat User" }
                val username = Validators.sanitizeUsernameFromEmailOrName(
                    user.email ?: displayName,
                    uid
                )
                val publicPayload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName.take(80),
                    "username" to username,
                    "bio" to "",
                    "avatarUrl" to (user.photoUrl?.toString() ?: "").take(500),
                    "allowPhoneSearch" to false,
                    "searchablePhone" to "",
                    "isDiscoverable" to true,
                    "isOnline" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                publicRef.set(publicPayload).await()
            }

            val existingPrivate = privateRef.get().await()
            if (!existingPrivate.exists()) {
                val authPhone = user.phoneNumber.orEmpty()
                val privatePayload = mapOf(
                    "userId" to uid,
                    "email" to (user.email ?: "").take(150),
                    "phoneNumber" to authPhone,
                    "countryCode" to "+234",
                    "isPhoneVerified" to authPhone.isNotBlank(),
                    "preferredLanguage" to "ha",
                    "themeMode" to "system",
                    "notificationsEnabled" to true,
                    "allowPhoneSearch" to false,
                    "readReceiptsEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                privateRef.set(privatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        username: String,
        bio: String,
        countryCode: String,
        phoneE164: String,
        allowPhoneSearch: Boolean,
        avatarUrl: String? = null
    ): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        val uid = user.uid
        val cleanName = displayName.trim().take(80)
        val cleanUsername = Validators.normalizeUsername(username)
        require(Validators.isValidDisplayName(cleanName)) { "Display name is required" }
        require(Validators.isValidUsername(cleanUsername)) { "Username must be 3-30 lowercase letters, numbers, or underscores" }
        require(Validators.isValidPhoneE164(phoneE164)) { "Invalid phone number format" }

        // Only mark verified if Firebase Auth token actually owns this exact phone number
        val verifiedPhone = user.phoneNumber
        val isVerified = !verifiedPhone.isNullOrBlank() && verifiedPhone == phoneE164

        return try {
            val publicRef = db.collection("user_public").document(uid)
            val privateRef = db.collection("users").document(uid)

            val publicUpdates = mutableMapOf<String, Any>(
                "displayName" to cleanName,
                "username" to cleanUsername,
                "bio" to bio.trim().take(200),
                "allowPhoneSearch" to allowPhoneSearch,
                "searchablePhone" to if (allowPhoneSearch) phoneE164 else "",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (avatarUrl != null) {
                publicUpdates["avatarUrl"] = avatarUrl.take(500)
            }

            val privateUpdates = mapOf(
                "phoneNumber" to phoneE164,
                "countryCode" to countryCode,
                "isPhoneVerified" to isVerified,
                "allowPhoneSearch" to allowPhoneSearch,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.runBatch { batch ->
                batch.update(publicRef, publicUpdates)
                batch.update(privateRef, privateUpdates)
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun updatePreferences(
        preferredLanguage: String? = null,
        themeMode: String? = null,
        notificationsEnabled: Boolean? = null,
        allowPhoneSearch: Boolean? = null,
        readReceiptsEnabled: Boolean? = null,
        fcmToken: String? = null
    ): Result<Unit> {
        val uid = requireUserId()
        return try {
            val privateRef = db.collection("users").document(uid)
            val publicRef = db.collection("user_public").document(uid)
            val privateDoc = privateRef.get().await()
            val phone = privateDoc.getString("phoneNumber").orEmpty()

            val privateUpdates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            preferredLanguage?.let { privateUpdates["preferredLanguage"] = it }
            themeMode?.let { privateUpdates["themeMode"] = it }
            notificationsEnabled?.let { privateUpdates["notificationsEnabled"] = it }
            allowPhoneSearch?.let { privateUpdates["allowPhoneSearch"] = it }
            readReceiptsEnabled?.let { privateUpdates["readReceiptsEnabled"] = it }
            fcmToken?.let { privateUpdates["fcmToken"] = it.take(500) }

            db.runBatch { batch ->
                batch.update(privateRef, privateUpdates)
                if (allowPhoneSearch != null) {
                    batch.update(
                        publicRef,
                        mapOf(
                            "allowPhoneSearch" to allowPhoneSearch,
                            "searchablePhone" to if (allowPhoneSearch) phone else "",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun uploadAvatar(imageUri: Uri): Result<String> {
        val uid = requireUserId()
        val storageInstance = storage
            ?: return Result.failure(IllegalStateException("Firebase Storage is not configured"))
        return try {
            val ref = storageInstance.reference.child("avatars/$uid/avatar_${System.currentTimeMillis()}.jpg")
            ref.putFile(imageUri).await()
            val url = ref.downloadUrl.await().toString()
            db.collection("user_public").document(uid)
                .update(mapOf("avatarUrl" to url, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProfileData(): Result<Unit> {
        val uid = requireUserId()
        return try {
            db.runBatch { batch ->
                batch.delete(db.collection("user_public").document(uid))
                batch.delete(db.collection("users").document(uid))
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(e)
        }
    }
}
