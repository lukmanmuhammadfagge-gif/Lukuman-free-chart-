package com.example.feature.profile

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AppLanguage
import com.example.core.AppStrings
import com.example.core.UiState
import com.example.feature.common.AvatarBadge
import com.example.feature.common.CountryCodeDropdown
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldVerified

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    language: AppLanguage,
    strings: AppStrings,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val otpVerificationId by viewModel.otpVerificationId.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadAvatar(uri)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when (val state = profileState) {
            UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is UiState.Success -> {
                val pub = state.data.publicProfile
                val priv = state.data.privateProfile

                var displayName by remember(pub.userId, pub.displayName) {
                    mutableStateOf(pub.displayName)
                }
                var username by remember(pub.userId, pub.username) {
                    mutableStateOf(pub.username)
                }
                var bio by remember(pub.userId, pub.bio) {
                    mutableStateOf(pub.bio)
                }
                var countryCode by remember(priv.userId, priv.countryCode) {
                    mutableStateOf(priv.countryCode.ifBlank { "+234" })
                }
                var phoneInput by remember(priv.userId, priv.phoneNumber) {
                    mutableStateOf(
                        priv.phoneNumber.removePrefix(priv.countryCode.ifBlank { "+234" })
                    )
                }
                var otpCode by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.profileTitle,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("open_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = strings.openSettings
                            )
                        }
                    }

                    // Avatar + Name summary card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarBadge(
                                name = pub.displayName,
                                imageUrl = pub.avatarUrl,
                                size = 72.dp,
                                isOnline = true
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pub.displayName, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "@${pub.username}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (priv.email.isNotBlank()) {
                                    Text(
                                        priv.email,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.testTag("change_avatar_button")
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = strings.changePhoto)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text(strings.displayNameLabel) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_display_name_input")
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(strings.usernameLabel) },
                        prefix = { Text("@") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_username_input")
                    )

                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text(strings.bioLabel) },
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_bio_input")
                    )

                    // Phone Number Section with Nigeria (+234) Default & Honest Verification Badge
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.phoneNumberLabel,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Surface(
                                    color = if (priv.isPhoneVerified) {
                                        EmeraldVerified.copy(alpha = 0.14f)
                                    } else {
                                        AmberWarning.copy(alpha = 0.14f)
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (priv.isPhoneVerified) {
                                                Icons.Filled.CheckCircle
                                            } else {
                                                Icons.Filled.WarningAmber
                                            },
                                            contentDescription = null,
                                            tint = if (priv.isPhoneVerified) EmeraldVerified else AmberWarning,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (priv.isPhoneVerified) {
                                                strings.phoneVerifiedBadge
                                            } else {
                                                strings.phoneUnverifiedBadge
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (priv.isPhoneVerified) EmeraldVerified else AmberWarning
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CountryCodeDropdown(
                                    selectedDialCode = countryCode,
                                    language = language,
                                    onSelect = { countryCode = it.dialCode }
                                )
                                OutlinedTextField(
                                    value = phoneInput,
                                    onValueChange = { phoneInput = it },
                                    placeholder = { Text("8012345678") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("profile_phone_input")
                                )
                            }

                            if (!priv.isPhoneVerified && phoneInput.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        if (activity != null) {
                                            viewModel.startPhoneOtpVerification(activity, countryCode, phoneInput)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("verify_otp_button")
                                ) {
                                    Text(strings.verifyViaOtp)
                                }
                                Text(
                                    text = strings.otpUnavailableHint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (otpVerificationId != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = otpCode,
                                        onValueChange = { otpCode = it },
                                        placeholder = { Text(strings.enterOtpHint) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(onClick = { viewModel.submitOtpCode(otpCode, countryCode) }) {
                                        Text(strings.confirmOtp)
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.saveProfile(
                                displayName = displayName,
                                username = username,
                                bio = bio,
                                countryCode = countryCode,
                                localPhoneInput = phoneInput,
                                allowPhoneSearch = priv.allowPhoneSearch,
                                successMsg = strings.profileSaved
                            )
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_profile_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(strings.saveProfile)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
