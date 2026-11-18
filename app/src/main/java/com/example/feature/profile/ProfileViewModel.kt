package com.example.feature.profile

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.CountryCodes
import com.example.core.UiState
import com.example.core.unwrapFirestoreException
import com.example.data.repository.AuthRepository
import com.example.data.repository.PhoneOtpEvent
import com.example.data.repository.ProfileRepository
import com.example.domain.model.PrivateUserProfile
import com.example.domain.model.PublicUserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CombinedProfile(
    val publicProfile: PublicUserProfile,
    val privateProfile: PrivateUserProfile
)

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _otpVerificationId = MutableStateFlow<String?>(null)
    val otpVerificationId: StateFlow<String?> = _otpVerificationId.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    val profileState: StateFlow<UiState<CombinedProfile>> =
        combine(
            profileRepository.observePublicProfile(currentUserId),
            profileRepository.observePrivateProfile(currentUserId)
        ) { pub, priv ->
            if (pub != null && priv != null) {
                UiState.Success(CombinedProfile(pub, priv))
            } else {
                UiState.Loading
            }
        }.catch { error ->
            val fsErr = unwrapFirestoreException(error)
            if (fsErr == null && error is CancellationException) throw error
            Log.w("ProfileVM", "Error observing profile", fsErr ?: error)
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load profile"))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun uploadAvatar(imageUri: Uri) {
        viewModelScope.launch {
            _isSaving.value = true
            val res = profileRepository.uploadAvatar(imageUri)
            _isSaving.value = false
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to upload photo"
            }
        }
    }

    fun saveProfile(
        displayName: String,
        username: String,
        bio: String,
        countryCode: String,
        localPhoneInput: String,
        allowPhoneSearch: Boolean,
        successMsg: String
    ) {
        val e164 = if (localPhoneInput.trim().startsWith("+")) {
            localPhoneInput.trim()
        } else {
            CountryCodes.formatE164(countryCode, localPhoneInput)
        }
        viewModelScope.launch {
            _isSaving.value = true
            val res = try {
                profileRepository.updateProfile(
                    displayName = displayName,
                    username = username,
                    bio = bio,
                    countryCode = countryCode,
                    phoneE164 = e164,
                    allowPhoneSearch = allowPhoneSearch
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
            _isSaving.value = false
            _statusMessage.value = if (res.isSuccess) {
                successMsg
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Unable to save profile"
            }
        }
    }

    fun startPhoneOtpVerification(activity: Activity, countryCode: String, localPhoneInput: String) {
        val e164 = if (localPhoneInput.trim().startsWith("+")) {
            localPhoneInput.trim()
        } else {
            CountryCodes.formatE164(countryCode, localPhoneInput)
        }
        if (e164.isBlank()) {
            _statusMessage.value = "Enter a valid phone number first"
            return
        }
        authRepository.startPhoneOtpVerification(activity, e164) { event ->
            when (event) {
                is PhoneOtpEvent.CodeSent -> {
                    _otpVerificationId.value = event.verificationId
                }
                is PhoneOtpEvent.Verified -> {
                    _otpVerificationId.value = null
                    viewModelScope.launch {
                        val current = (profileState.value as? UiState.Success)?.data
                        if (current != null) {
                            profileRepository.updateProfile(
                                displayName = current.publicProfile.displayName,
                                username = current.publicProfile.username,
                                bio = current.publicProfile.bio,
                                countryCode = countryCode,
                                phoneE164 = event.phoneNumber,
                                allowPhoneSearch = current.privateProfile.allowPhoneSearch
                            )
                        }
                        _statusMessage.value = "Phone verified: ${event.phoneNumber}"
                    }
                }
                is PhoneOtpEvent.Failed -> {
                    _statusMessage.value = event.message
                }
            }
        }
    }

    fun submitOtpCode(smsCode: String, countryCode: String) {
        val verificationId = _otpVerificationId.value ?: return
        viewModelScope.launch {
            val res = authRepository.verifyOtpCodeAndLinkPhone(verificationId, smsCode)
            res.onSuccess { verifiedPhone ->
                _otpVerificationId.value = null
                val current = (profileState.value as? UiState.Success)?.data
                if (current != null) {
                    profileRepository.updateProfile(
                        displayName = current.publicProfile.displayName,
                        username = current.publicProfile.username,
                        bio = current.publicProfile.bio,
                        countryCode = countryCode,
                        phoneE164 = verifiedPhone,
                        allowPhoneSearch = current.privateProfile.allowPhoneSearch
                    )
                }
                _statusMessage.value = "Verified: $verifiedPhone"
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Invalid OTP code"
            }
        }
    }
}
