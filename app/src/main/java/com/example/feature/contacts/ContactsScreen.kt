package com.example.feature.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.AppLanguage
import com.example.core.AppStrings
import com.example.core.UiState
import com.example.domain.model.PublicUserProfile
import com.example.feature.common.AvatarBadge
import com.example.feature.common.CountryCodeDropdown
import com.example.feature.common.EmptyStateCard

@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    currentUserId: String,
    language: AppLanguage,
    strings: AppStrings,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchMode by viewModel.searchMode.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val countryCode by viewModel.countryCode.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val friendRequestsState by viewModel.friendRequestsState.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var reportTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var reportReason by remember { mutableStateOf("Spam") }
    var reportDetails by remember { mutableStateOf("") }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    if (reportTarget != null) {
        AlertDialog(
            onDismissRequest = { reportTarget = null },
            title = { Text("${strings.reportUser}: ${reportTarget?.second}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        label = { Text(strings.reportReasonLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reportDetails,
                        onValueChange = { reportDetails = it },
                        placeholder = { Text(strings.reportDetailsHint) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = reportTarget ?: return@Button
                    viewModel.reportUser(target.first, reportReason.ifBlank { "Abuse" }, reportDetails)
                    reportDetails = ""
                    reportTarget = null
                }) {
                    Text(strings.submitReport)
                }
            },
            dismissButton = {
                TextButton(onClick = { reportTarget = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val blockedIds = blockedUsers.map { it.blockedUserId }.toSet()
        val requests = (friendRequestsState as? UiState.Success)?.data.orEmpty()
        val incomingPending = requests.filter {
            it.status == "pending" && it.toUserId == currentUserId && it.fromUserId !in blockedIds
        }
        val acceptedFriends = requests.filter { it.status == "accepted" }.filter { req ->
            val peerId = if (req.fromUserId == currentUserId) req.toUserId else req.fromUserId
            peerId !in blockedIds
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = strings.contactsTitle,
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = searchMode == SearchMode.USERNAME,
                        onClick = { viewModel.setSearchMode(SearchMode.USERNAME) },
                        label = { Text(strings.searchByUsername) },
                        modifier = Modifier.testTag("search_mode_username")
                    )
                    FilterChip(
                        selected = searchMode == SearchMode.PHONE,
                        onClick = { viewModel.setSearchMode(SearchMode.PHONE) },
                        label = { Text(strings.searchByPhone) },
                        modifier = Modifier.testTag("search_mode_phone")
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (searchMode == SearchMode.PHONE) {
                        CountryCodeDropdown(
                            selectedDialCode = countryCode,
                            language = language,
                            onSelect = { viewModel.setCountryCode(it.dialCode) }
                        )
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::onQueryChanged,
                        placeholder = { Text(strings.searchUsersHint) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (searchMode == SearchMode.PHONE) KeyboardType.Phone else KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.executeSearch() }),
                        trailingIcon = {
                            IconButton(
                                onClick = viewModel::executeSearch,
                                modifier = Modifier.testTag("execute_user_search")
                            ) {
                                if (isSearching) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Search, contentDescription = strings.searchByUsername)
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("contacts_search_input")
                    )
                }
                if (searchMode == SearchMode.PHONE) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.phoneSearchPrivacyNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (searchResults.isNotEmpty()) {
                items(searchResults, key = { "search_${it.userId}" }) { profile ->
                    UserDiscoveryCard(
                        profile = profile,
                        strings = strings,
                        onAddFriend = { viewModel.sendFriendRequest(profile) },
                        onMessage = {
                            viewModel.startDirectChat(profile, onOpenConversation)
                        },
                        onBlock = { viewModel.blockUser(profile) },
                        onReport = { reportTarget = profile.userId to profile.displayName }
                    )
                }
            }

            if (incomingPending.isNotEmpty()) {
                item {
                    Text(
                        text = strings.incomingRequests,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(incomingPending, key = { "req_${it.id}" }) { req ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarBadge(name = req.fromDisplayName, size = 44.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(req.fromDisplayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "@${req.fromUsername}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { viewModel.respondToRequest(req.id, accept = true) },
                                modifier = Modifier.testTag("accept_request_${req.id}")
                            ) {
                                Text(strings.accept)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = { viewModel.respondToRequest(req.id, accept = false) }
                            ) {
                                Text(strings.decline)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = strings.friendsList,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (friendRequestsState is UiState.Loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (acceptedFriends.isEmpty() && searchResults.isEmpty()) {
                item {
                    EmptyStateCard(
                        illustrationRes = R.drawable.img_empty_chats,
                        title = strings.emptyContactsTitle,
                        subtitle = strings.emptyContactsSubtitle
                    )
                }
            } else {
                items(acceptedFriends, key = { "friend_${it.id}" }) { req ->
                    val isSender = req.fromUserId == currentUserId
                    val peerId = if (isSender) req.toUserId else req.fromUserId
                    val peerName = if (isSender) req.toDisplayName else req.fromDisplayName
                    val peerUsername = if (isSender) req.toUsername else req.fromUsername

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarBadge(name = peerName, size = 44.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(peerName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "@$peerUsername",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.startDirectChatByUserId(
                                        peerId,
                                        peerName,
                                        peerUsername,
                                        onOpenConversation
                                    )
                                }
                            ) {
                                Icon(Icons.Filled.Chat, contentDescription = strings.messageAction)
                            }
                            IconButton(
                                onClick = {
                                    viewModel.blockUser(
                                        PublicUserProfile(
                                            userId = peerId,
                                            displayName = peerName,
                                            username = peerUsername
                                        )
                                    )
                                }
                            ) {
                                Icon(Icons.Filled.Block, contentDescription = strings.blockUser)
                            }
                            IconButton(
                                onClick = { reportTarget = peerId to peerName }
                            ) {
                                Icon(Icons.Filled.Flag, contentDescription = strings.reportUser)
                            }
                        }
                    }
                }
            }

            if (blockedUsers.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.blockedUsersTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                items(blockedUsers, key = { "blocked_${it.blockedUserId}" }) { blocked ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(blocked.displayName.ifBlank { blocked.blockedUserId })
                                if (blocked.username.isNotBlank()) {
                                    Text("@${blocked.username}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            OutlinedButton(onClick = { viewModel.unblockUser(blocked.blockedUserId) }) {
                                Text(strings.unblockUser)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserDiscoveryCard(
    profile: PublicUserProfile,
    strings: AppStrings,
    onAddFriend: () -> Unit,
    onMessage: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarBadge(
                    name = profile.displayName,
                    imageUrl = profile.avatarUrl,
                    size = 46.dp,
                    isOnline = profile.isOnline
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "@${profile.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (profile.bio.isNotBlank()) {
                        Text(
                            profile.bio,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onAddFriend) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.sendFriendRequest)
                }
                OutlinedButton(onClick = onMessage) {
                    Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.messageAction)
                }
                IconButton(onClick = onBlock) {
                    Icon(Icons.Filled.Block, contentDescription = strings.blockUser)
                }
                IconButton(onClick = onReport) {
                    Icon(Icons.Filled.Flag, contentDescription = strings.reportUser)
                }
            }
        }
    }
}
