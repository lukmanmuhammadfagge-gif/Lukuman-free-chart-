package com.example.feature.chats

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.UiState
import com.example.core.unwrapFirestoreException
import com.example.data.local.CachedConversationEntity
import com.example.data.repository.ChatRepository
import com.example.data.repository.ProfileRepository
import com.example.domain.model.CachedOrLiveMessage
import com.example.domain.model.Conversation
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

class ChatsViewModel(
    private val chatRepository: ChatRepository,
    private val profileRepository: ProfileRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    private val _pageLimit = MutableStateFlow(50L)

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    val currentUserPublic: StateFlow<PublicUserProfile?> =
        profileRepository.observePublicProfile(currentUserId)
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val readReceiptsEnabled: StateFlow<Boolean> =
        profileRepository.observePrivateProfile(currentUserId)
            .map { it?.readReceiptsEnabled ?: true }
            .catch { emit(true) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val cachedConversations: StateFlow<List<CachedConversationEntity>> =
        chatRepository.observeCachedConversations()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        chatRepository.observeConversations(currentUserId)
            .map<List<Conversation>, UiState<List<Conversation>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = unwrapFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w("ChatsVM", "Error observing conversations", root)
                emit(UiState.Error(root.message ?: "Failed to load conversations"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeConversationDoc: StateFlow<Conversation?> =
        _activeConversationId.flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(null)
            else chatRepository.observeConversationDoc(id).catch { emit(null) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val messagesState: StateFlow<UiState<List<CachedOrLiveMessage>>> =
        _activeConversationId.flatMapLatest { convId ->
            if (convId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                _pageLimit.flatMapLatest { limit ->
                    chatRepository.observeMessagesWithCache(convId, currentUserId, limit)
                        .map<List<CachedOrLiveMessage>, UiState<List<CachedOrLiveMessage>>> {
                            UiState.Success(it)
                        }
                        .catch { error ->
                            val fsErr = unwrapFirestoreException(error)
                            if (fsErr == null && error is CancellationException) throw error
                            Log.w("ChatsVM", "Falling back to Room cache for $convId", fsErr ?: error)
                            emit(UiState.Error((fsErr ?: error).message ?: "Offline cache active"))
                        }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val offlineMessages: StateFlow<List<CachedOrLiveMessage>> =
        _activeConversationId.flatMapLatest { convId ->
            if (convId.isNullOrBlank()) flowOf(emptyList())
            else chatRepository.observeOfflineMessages(convId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun openConversation(conversationId: String) {
        if (_activeConversationId.value != conversationId) {
            _pageLimit.value = 50L
            _activeConversationId.value = conversationId
        }
    }

    fun loadMoreMessages() {
        _pageLimit.value += 50L
    }

    fun clearError() {
        _actionError.value = null
    }

    fun sendTextMessage(
        conversation: Conversation,
        text: String,
        replyTo: CachedOrLiveMessage?
    ) {
        if (text.isBlank()) return
        val senderName = currentUserPublic.value?.displayName ?: "User"
        val otherId = conversation.otherParticipantId(currentUserId)
        val otherName = conversation.otherParticipantName(currentUserId)
        val otherAvatar = conversation.otherParticipantAvatar(currentUserId)

        viewModelScope.launch {
            val res = chatRepository.sendDirectMessage(
                conversationId = conversation.id,
                senderName = senderName,
                otherUserId = otherId,
                otherUserName = otherName,
                otherUserAvatar = otherAvatar,
                text = text,
                messageType = "text",
                replyToMessageId = replyTo?.id.orEmpty(),
                replyToPreview = replyTo?.text?.ifBlank { replyTo.mediaName }.orEmpty()
            )
            if (res.isFailure) {
                _actionError.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to send message"
            }
        }
    }

    fun sendMediaMessage(
        conversation: Conversation,
        messageType: String,
        mediaUri: Uri? = null,
        localFile: File? = null,
        mediaName: String = "",
        voiceDurationMs: Long = 0L,
        replyTo: CachedOrLiveMessage? = null
    ) {
        val senderName = currentUserPublic.value?.displayName ?: "User"
        val otherId = conversation.otherParticipantId(currentUserId)
        val otherName = conversation.otherParticipantName(currentUserId)
        val otherAvatar = conversation.otherParticipantAvatar(currentUserId)

        viewModelScope.launch {
            val res = chatRepository.sendDirectMessage(
                conversationId = conversation.id,
                senderName = senderName,
                otherUserId = otherId,
                otherUserName = otherName,
                otherUserAvatar = otherAvatar,
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
                _actionError.value = res.exceptionOrNull()?.localizedMessage ?: "Media upload failed"
            }
        }
    }

    fun retryMessage(localId: String) {
        viewModelScope.launch {
            val res = chatRepository.retryPendingMessage(localId)
            if (res.isFailure) {
                _actionError.value = res.exceptionOrNull()?.localizedMessage ?: "Retry failed"
            }
        }
    }

    fun markVisibleMessagesRead(conversationId: String, messages: List<CachedOrLiveMessage>) {
        viewModelScope.launch {
            chatRepository.markConversationMessagesDeliveredOrRead(
                conversationId = conversationId,
                messages = messages,
                readReceiptsEnabled = readReceiptsEnabled.value
            )
        }
    }

    fun toggleReaction(conversationId: String, message: CachedOrLiveMessage, emoji: String) {
        viewModelScope.launch {
            val res = chatRepository.toggleReaction(
                conversationId = conversationId,
                messageId = message.id,
                currentReactions = message.reactions,
                emoji = emoji
            )
            if (res.isFailure) {
                _actionError.value = res.exceptionOrNull()?.localizedMessage ?: "Reaction failed"
            }
        }
    }
}
