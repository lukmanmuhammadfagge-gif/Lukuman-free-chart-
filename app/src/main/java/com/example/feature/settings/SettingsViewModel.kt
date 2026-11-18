package com.example.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppLanguage
import com.example.core.AppPreferences
import com.example.core.ThemeMode
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.ProfileRepository
import com.example.domain.model.PrivateUserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferences: AppPreferences,
    private val profileRepository: ProfileRepository,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val privateProfile: StateFlow<PrivateUserProfile?> =
        profileRepository.observePrivateProfile(currentUserId)
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            preferences.setLanguage(language)
            profileRepository.updatePreferences(preferredLanguage = language.code)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
            profileRepository.updatePreferences(themeMode = mode.key)
        }
    }

    fun setAllowPhoneSearch(allowed: Boolean) {
        viewModelScope.launch {
            val res = profileRepository.updatePreferences(allowPhoneSearch = allowed)
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Update failed"
            }
        }
    }

    fun setReadReceiptsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val res = profileRepository.updatePreferences(readReceiptsEnabled = enabled)
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Update failed"
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setNotificationsEnabled(enabled)
            profileRepository.updatePreferences(notificationsEnabled = enabled)
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            chatRepository.clearLocalCaches()
            authRepository.signOut(context)
        }
    }

    fun deleteAccount(context: Context) {
        viewModelScope.launch {
            chatRepository.clearLocalCaches()
            profileRepository.deleteProfileData()
            val res = authRepository.deleteCurrentUserAccount(context)
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.localizedMessage
                    ?: "Re-authentication may be required before deleting account."
            }
        }
    }
}
