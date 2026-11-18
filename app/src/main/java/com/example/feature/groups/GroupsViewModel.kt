package com.example.feature.groups

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.UiState
import com.example.core.unwrapFirestoreException
import com.example.data.repository.ContactsRepository
import com.example.data.repository.GroupRepository
import com.example.data.repository.ProfileRepository
import com.example.domain.model.ChatGroup
import com.example.domain.model.GroupMessage
import com.example.domain.model.PublicUserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class GroupsViewModel(
    private val groupRepository: GroupRepository,
    private val contactsRepository: ContactsRepository,
    private val profileRepository: ProfileRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _activeGroupId = MutableStateFlow<String?>(null)
    private val _pageLimit = MutableStateFlow(50L)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val currentUserProfile: StateFlow<PublicUserProfile?> =
        profileRepository.observePublicProfile(currentUserId)
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val groupsState: StateFlow<UiState<List<ChatGroup>>> =
        groupRepository.observeGroups(currentUserId)
            .map<List<ChatGroup>, UiState<List<ChatGroup>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = unwrapFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                Log.w("GroupsVM", "Error observing groups", fsErr ?: error)
                emit(UiState.Error((fsErr ?: error).message ?: "Failed to load groups"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeGroup: StateFlow<ChatGroup?> =
        _activeGroupId.flatMapLatest { groupId ->
            if (groupId.isNullOrBlank()) flowOf(null)
            else groupRepository.observeGroup(groupId).catch { emit(null) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val groupMessagesState: StateFlow<UiState<List<GroupMessage>>> =
        _activeGroupId.flatMapLatest { groupId ->
            if (groupId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                _pageLimit.flatMapLatest { limit ->
                    groupRepository.observeGroupMessages(groupId, currentUserId, limit)
                        .map<List<GroupMessage>, UiState<List<GroupMessage>>> { UiState.Success(it) }
                        .catch { error ->
                            val fsErr = unwrapFirestoreException(error)
                            if (fsErr == null && error is CancellationException) throw error
                            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load group messages"))
                        }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _candidateFriends = MutableStateFlow<List<PublicUserProfile>>(emptyList())
    val candidateFriends: StateFlow<List<PublicUserProfile>> = _candidateFriends.asStateFlow()

    fun openGroup(groupId: String) {
        if (_activeGroupId.value != groupId) {
            _pageLimit.value = 50L
            _activeGroupId.value = groupId
        }
    }

    fun loadMoreMessages() {
        _pageLimit.value += 50L
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun searchUsersForGroup(query: String) {
        if (query.isBlank()) {
            _candidateFriends.value = emptyList()
            return
        }
        viewModelScope.launch {
            contactsRepository.searchByUsername(query)
                .onSuccess { _candidateFriends.value = it }
        }
    }

    fun createGroup(
        name: String,
        description: String,
        onlyAdminsCanPost: Boolean,
        onlyAdminsCanAddMembers: Boolean,
        onCreated: (String) -> Unit
    ) {
        val me = currentUserProfile.value ?: return
        viewModelScope.launch {
            val res = groupRepository.createGroup(
                name = name,
                description = description,
                creatorProfile = me,
                initialMembers = emptyList(),
                onlyAdminsCanPost = onlyAdminsCanPost,
                onlyAdminsCanAddMembers = onlyAdminsCanAddMembers
            )
            res.onSuccess(onCreated)
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to create group" }
        }
    }

    fun updateGroup(
        groupId: String,
        name: String,
        description: String,
        onlyAdminsCanPost: Boolean,
        onlyAdminsCanAddMembers: Boolean
    ) {
        viewModelScope.launch {
            val res = groupRepository.updateGroupDetails(
                groupId = groupId,
                name = name,
                description = description,
                onlyAdminsCanPost = onlyAdminsCanPost,
                onlyAdminsCanAddMembers = onlyAdminsCanAddMembers
            )
            _statusMessage.value = if (res.isSuccess) "Group updated" else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun uploadGroupAvatar(groupId: String, imageUri: Uri) {
        viewModelScope.launch {
            val res = groupRepository.uploadGroupAvatar(groupId, imageUri)
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to upload group photo"
            }
        }
    }

    fun addMember(group: ChatGroup, member: PublicUserProfile) {
        viewModelScope.launch {
            val res = groupRepository.addMember(group, member)
            _statusMessage.value = if (res.isSuccess) "Added ${member.displayName}" else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun removeMember(group: ChatGroup, targetUserId: String) {
        viewModelScope.launch {
            val res = groupRepository.removeMember(group, targetUserId)
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to remove member"
            }
        }
    }

    fun toggleAdmin(group: ChatGroup, targetUserId: String) {
        viewModelScope.launch {
            val res = groupRepository.toggleAdminRole(group, targetUserId)
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update admin"
            }
        }
    }

    fun sendGroupText(group: ChatGroup, text: String, replyTo: GroupMessage?) {
        if (text.isBlank()) return
        val senderName = currentUserProfile.value?.displayName ?: "Member"
        viewModelScope.launch {
            val res = groupRepository.sendGroupMessage(
                group = group,
                senderName = senderName,
                text = text,
                messageType = "text",
                replyToMessageId = replyTo?.id.orEmpty(),
                replyToPreview = replyTo?.text?.ifBlank { replyTo.mediaName }.orEmpty()
            )
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to send message"
            }
        }
    }

    fun sendGroupMedia(
        group: ChatGroup,
        messageType: String,
        mediaUri: Uri? = null,
        localFile: File? = null,
        mediaName: String = "",
        voiceDurationMs: Long = 0L,
        replyTo: GroupMessage? = null
    ) {
        val senderName = currentUserProfile.value?.displayName ?: "Member"
        viewModelScope.launch {
            val res = groupRepository.sendGroupMessage(
                group = group,
                senderName = senderName,
                text = "",
                messageType = messageType,
                mediaUri = mediaUri,
                localFile = localFile,
                mediaName = mediaName,
                voiceDurationMs = voiceDurationMs,
                replyToMessageId = replyTo?.id.orEmpty(),
                replyToPreview = replyTo?.text?.ifBlank { replyTo.mediaName }.orEmpty()
            )
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to upload media"
            }
        }
    }

    fun toggleReaction(groupId: String, message: GroupMessage, emoji: String) {
        viewModelScope.launch {
            groupRepository.toggleGroupReaction(groupId, message.id, message.reactions, emoji)
        }
    }
}
