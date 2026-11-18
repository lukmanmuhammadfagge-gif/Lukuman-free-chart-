package com.example.feature.contacts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.CountryCodes
import com.example.core.UiState
import com.example.core.unwrapFirestoreException
import com.example.data.repository.ChatRepository
import com.example.data.repository.ContactsRepository
import com.example.data.repository.ProfileRepository
import com.example.domain.model.BlockedUser
import com.example.domain.model.FriendRequest
import com.example.domain.model.PublicUserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SearchMode {
    USERNAME,
    PHONE
}

class ContactsViewModel(
    private val contactsRepository: ContactsRepository,
    private val chatRepository: ChatRepository,
    private val profileRepository: ProfileRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _searchMode = MutableStateFlow(SearchMode.USERNAME)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _countryCode = MutableStateFlow(CountryCodes.defaultNigeria.dialCode)
    val countryCode: StateFlow<String> = _countryCode.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PublicUserProfile>>(emptyList())
    val searchResults: StateFlow<List<PublicUserProfile>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val currentUserProfile: StateFlow<PublicUserProfile?> =
        profileRepository.observePublicProfile(currentUserId)
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val friendRequestsState: StateFlow<UiState<List<FriendRequest>>> =
        contactsRepository.observeFriendRequests(currentUserId)
            .map<List<FriendRequest>, UiState<List<FriendRequest>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = unwrapFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                Log.w("ContactsVM", "Failed observing friend requests", fsErr ?: error)
                emit(UiState.Error((fsErr ?: error).message ?: "Failed to load contacts"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val blockedUsers: StateFlow<List<BlockedUser>> =
        contactsRepository.observeBlockedUsers(currentUserId)
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun setSearchMode(mode: SearchMode) {
        _searchMode.value = mode
        _searchResults.value = emptyList()
    }

    fun setCountryCode(dialCode: String) {
        _countryCode.value = dialCode
    }

    fun onQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun executeSearch() {
        val raw = _searchQuery.value.trim()
        if (raw.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            val result = if (_searchMode.value == SearchMode.USERNAME) {
                contactsRepository.searchByUsername(raw)
            } else {
                val e164 = if (raw.startsWith("+")) raw else CountryCodes.formatE164(_countryCode.value, raw)
                contactsRepository.searchByPhone(e164)
            }
            _isSearching.value = false
            result.onSuccess { list ->
                val blockedIds = blockedUsers.value.map { it.blockedUserId }.toSet()
                _searchResults.value = list.filter { it.userId !in blockedIds }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Search failed"
            }
        }
    }

    fun sendFriendRequest(target: PublicUserProfile) {
        val me = currentUserProfile.value ?: return
        viewModelScope.launch {
            val res = contactsRepository.sendFriendRequest(me, target)
            _statusMessage.value = if (res.isSuccess) {
                "Friend request sent to @${target.username}"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Unable to send friend request"
            }
        }
    }

    fun respondToRequest(requestId: String, accept: Boolean) {
        viewModelScope.launch {
            val res = contactsRepository.respondToFriendRequest(requestId, accept)
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Action failed"
            }
        }
    }

    fun startDirectChat(otherUser: PublicUserProfile, onConversationReady: (String) -> Unit) {
        val me = currentUserProfile.value ?: return
        viewModelScope.launch {
            val res = chatRepository.getOrCreateDirectConversation(me, otherUser)
            res.onSuccess(onConversationReady)
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Unable to open chat"
                }
        }
    }

    fun startDirectChatByUserId(
        userId: String,
        displayName: String,
        username: String,
        onConversationReady: (String) -> Unit
    ) {
        startDirectChat(
            PublicUserProfile(
                userId = userId,
                displayName = displayName,
                username = username
            ),
            onConversationReady
        )
    }

    fun blockUser(target: PublicUserProfile) {
        viewModelScope.launch {
            val res = contactsRepository.blockUser(target)
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Block failed"
            }
        }
    }

    fun unblockUser(blockedUserId: String) {
        viewModelScope.launch {
            val res = contactsRepository.unblockUser(blockedUserId)
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Unblock failed"
            }
        }
    }

    fun reportUser(targetUserId: String, reason: String, details: String) {
        viewModelScope.launch {
            val res = contactsRepository.reportUser(targetUserId, reason, details)
            _statusMessage.value = if (res.isSuccess) {
                "Report submitted"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Report failed"
            }
        }
    }
}
