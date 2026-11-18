package com.example.feature.groups

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.AppStrings
import com.example.core.UiState
import com.example.feature.common.AvatarBadge
import com.example.feature.common.EmptyStateCard
import com.example.feature.common.formatMessageTime

@Composable
fun GroupsScreen(
    viewModel: GroupsViewModel,
    strings: AppStrings,
    onGroupClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupsState by viewModel.groupsState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var groupDesc by remember { mutableStateOf("") }
    var onlyAdminsPost by remember { mutableStateOf(false) }
    var onlyAdminsAdd by remember { mutableStateOf(true) }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(strings.createGroup) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text(strings.groupNameLabel) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_group_name_input")
                    )
                    OutlinedTextField(
                        value = groupDesc,
                        onValueChange = { groupDesc = it },
                        label = { Text(strings.groupDescLabel) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            strings.onlyAdminsPost,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = onlyAdminsPost, onCheckedChange = { onlyAdminsPost = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            strings.onlyAdminsAdd,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = onlyAdminsAdd, onCheckedChange = { onlyAdminsAdd = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createGroup(
                            name = groupName,
                            description = groupDesc,
                            onlyAdminsCanPost = onlyAdminsPost,
                            onlyAdminsCanAddMembers = onlyAdminsAdd
                        ) { groupId ->
                            groupName = ""
                            groupDesc = ""
                            showCreateDialog = false
                            onGroupClick(groupId)
                        }
                    },
                    enabled = groupName.isNotBlank(),
                    modifier = Modifier.testTag("confirm_create_group_button")
                ) {
                    Text(strings.createGroup)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_group_fab")
            ) {
                Icon(Icons.Filled.GroupAdd, contentDescription = strings.createGroup)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = strings.groupsTitle,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (val state = groupsState) {
                UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    EmptyStateCard(
                        illustrationRes = R.drawable.img_empty_groups,
                        title = strings.errorPrefix,
                        subtitle = state.message
                    )
                }

                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyStateCard(
                                illustrationRes = R.drawable.img_empty_groups,
                                title = strings.emptyGroupsTitle,
                                subtitle = strings.emptyGroupsSubtitle,
                                actionLabel = strings.createGroup,
                                onAction = { showCreateDialog = true }
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 84.dp)
                        ) {
                            items(state.data, key = { it.id }) { group ->
                                val timeMs = (group.lastMessageAt ?: group.updatedAt)?.toDate()?.time ?: 0L
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(18.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onGroupClick(group.id) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AvatarBadge(
                                            name = group.name,
                                            imageUrl = group.avatarUrl,
                                            size = 50.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = group.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (timeMs > 0L) {
                                                    Text(
                                                        text = formatMessageTime(timeMs),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val preview = if (group.lastMessageText.isNotBlank()) {
                                                "${group.lastMessageSenderName}: ${group.lastMessageText}"
                                            } else {
                                                "${group.memberIds.size} ${strings.membersTitle}"
                                            }
                                            Text(
                                                text = preview,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
