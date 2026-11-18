package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

sealed interface AuthActionResult {
    data object Success : AuthActionResult
    data object Cancelled : AuthActionResult
    data class Error(val message: String) : AuthActionResult
}

sealed interface PhoneOtpEvent {
    data class CodeSent(val verificationId: String) : PhoneOtpEvent
    data class Verified(val phoneNumber: String) : PhoneOtpEvent
    data class Failed(val message: String) : PhoneOtpEvent
}

class AuthRepository(
    private val auth: FirebaseAuth
) {
    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun currentUser(): FirebaseUser? = auth.currentUser

    suspend fun attemptSilentSignIn(context: Context): AuthActionResult {
        if (auth.currentUser != null) return AuthActionResult.Success
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            return AuthActionResult.Cancelled
        }
        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                AuthActionResult.Success
            } else {
                AuthActionResult.Cancelled
            }
        } catch (_: Exception) {
            AuthActionResult.Cancelled
        }
    }

    suspend fun signInWithGoogle(activity: Activity): AuthActionResult {
        val clientId = try {
            activity.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            return AuthActionResult.Error("Google Sign-In configuration missing (default_web_client_id)")
        }
        return try {
            val credentialManager = CredentialManager.create(activity)
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(authCredential).await()
                AuthActionResult.Success
            } else {
                AuthActionResult.Error("Unexpected credential type received")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
            AuthActionResult.Cancelled
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            AuthActionResult.Error(e.localizedMessage ?: "Google Sign-In failed")
        }
    }

    fun startPhoneOtpVerification(
        activity: Activity,
        phoneE164: String,
        onEvent: (PhoneOtpEvent) -> Unit
    ) {
        try {
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    val user = auth.currentUser ?: return
                    user.linkWithCredential(credential)
                        .addOnSuccessListener {
                            onEvent(PhoneOtpEvent.Verified(user.phoneNumber ?: phoneE164))
                        }
                        .addOnFailureListener { err ->
                            onEvent(PhoneOtpEvent.Failed(err.localizedMessage ?: "Failed to link verified phone"))
                        }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    onEvent(PhoneOtpEvent.Failed(e.localizedMessage ?: "SMS OTP unavailable. Ensure Phone Auth is enabled in Firebase Console."))
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    onEvent(PhoneOtpEvent.CodeSent(verificationId))
                }
            }

            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneE164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            onEvent(PhoneOtpEvent.Failed(e.localizedMessage ?: "SMS OTP not configured in Firebase Console"))
        }
    }

    suspend fun verifyOtpCodeAndLinkPhone(
        verificationId: String,
        smsCode: String
    ): Result<String> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, smsCode.trim())
            user.linkWithCredential(credential).await()
            user.getIdToken(true).await()
            val verifiedNumber = auth.currentUser?.phoneNumber
            if (!verifiedNumber.isNullOrBlank()) {
                Result.success(verifiedNumber)
            } else {
                Result.failure(IllegalStateException("Phone verification did not attach a verified phone number"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            try {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }

    suspend fun deleteCurrentUserAccount(context: Context): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            user.delete().await()
            try {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
