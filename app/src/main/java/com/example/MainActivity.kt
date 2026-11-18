package com.example

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.AppLanguage
import com.example.core.AppStrings
import com.example.core.StringsProvider
import com.example.core.ThemeMode
import com.example.di.AppContainer
import com.example.feature.auth.AuthScreen
import com.example.feature.chats.ChatDetailScreen
import com.example.feature.chats.ChatsListScreen
import com.example.feature.chats.ChatsViewModel
import com.example.feature.contacts.ContactsScreen
import com.example.feature.contacts.ContactsViewModel
import com.example.feature.groups.GroupDetailScreen
import com.example.feature.groups.GroupsScreen
import com.example.feature.groups.GroupsViewModel
import com.example.feature.profile.ProfileScreen
import com.example.feature.profile.ProfileViewModel
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.SettingsViewModel
import com.example.notifications.FreeChatMessagingService
import com.example.ui.theme.LukumanFreeChatTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import kotlinx.coroutines.launch

object Routes {
    const val CHATS = "chats"
    const val CONTACTS = "contacts"
    const val GROUPS = "groups"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val CHAT_DETAIL = "chat_detail/{conversationId}"
    const val GROUP_DETAIL = "group_detail/{groupId}"

    fun chatDetail(conversationId: String) = "chat_detail/$conversationId"
    fun groupDetail(groupId: String) = "group_detail/$groupId"
}

private data class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            FirebaseApp.initializeApp(this)
            val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
            }
            FreeChatMessagingService.ensureChannel(this)
        } catch (_: Exception) {
        }

        val container = AppContainer(applicationContext)

        setContent {
            val language by container.preferences.languageFlow
                .collectAsStateWithLifecycle(initialValue = AppLanguage.HAUSA)
            val themeMode by container.preferences.themeModeFlow
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val strings = remember(language) { StringsProvider.forLanguage(language) }
            val scope = rememberCoroutineScope()

            LukumanFreeChatTheme(themeMode = themeMode) {
                AppRoot(
                    container = container,
                    language = language,
                    themeMode = themeMode,
                    strings = strings,
                    onLanguageChange = { lang ->
                        scope.launch { container.preferences.setLanguage(lang) }
                    }
                )
            }
        }
    }
}

@Composable
fun AppRoot(
    container: AppContainer,
    language: AppLanguage,
    themeMode: ThemeMode,
    strings: AppStrings,
    onLanguageChange: (AppLanguage) -> Unit
) {
    val currentUser by container.authRepository.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = container.authRepository.currentUser())

    val user = currentUser
    if (user == null) {
        AuthScreen(
            authRepository = container.authRepository,
            language = language,
            strings = strings,
            onLanguageChange = onLanguageChange,
            onAuthSuccess = {}
        )
    } else {
        key(user.uid) {
            AuthenticatedMainApp(
                container = container,
                currentUserId = user.uid,
                language = language,
                themeMode = themeMode,
                strings = strings
            )
        }
    }
}

@Composable
private fun AuthenticatedMainApp(
    container: AppContainer,
    currentUserId: String,
    language: AppLanguage,
    themeMode: ThemeMode,
    strings: AppStrings
) {
    LaunchedEffect(currentUserId) {
        container.profileRepository.ensureUserProfilesInitialized()
    }

    val chatsViewModel: ChatsViewModel = viewModel(
        key = "chats_$currentUserId",
        factory = viewModelFactory {
            initializer {
                ChatsViewModel(container.chatRepository, container.profileRepository, currentUserId)
            }
        }
    )
    val contactsViewModel: ContactsViewModel = viewModel(
        key = "contacts_$currentUserId",
        factory = viewModelFactory {
            initializer {
                ContactsViewModel(
                    container.contactsRepository,
                    container.chatRepository,
                    container.profileRepository,
                    currentUserId
                )
            }
        }
    )
    val groupsViewModel: GroupsViewModel = viewModel(
        key = "groups_$currentUserId",
        factory = viewModelFactory {
            initializer {
                GroupsViewModel(
                    container.groupRepository,
                    container.contactsRepository,
                    container.profileRepository,
                    currentUserId
                )
            }
        }
    )
    val profileViewModel: ProfileViewModel = viewModel(
        key = "profile_$currentUserId",
        factory = viewModelFactory {
            initializer {
                ProfileViewModel(
                    container.profileRepository,
                    container.authRepository,
                    currentUserId
                )
            }
        }
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        key = "settings_$currentUserId",
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    container.preferences,
                    container.profileRepository,
                    container.chatRepository,
                    container.authRepository,
                    currentUserId
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val tabs = remember(strings) {
        listOf(
            BottomNavTab(
                route = Routes.CHATS,
                label = strings.navChats,
                selectedIcon = Icons.AutoMirrored.Filled.Chat,
                unselectedIcon = Icons.AutoMirrored.Outlined.Chat,
                testTag = "nav_tab_chats"
            ),
            BottomNavTab(
                route = Routes.CONTACTS,
                label = strings.navContacts,
                selectedIcon = Icons.Filled.Contacts,
                unselectedIcon = Icons.Outlined.Contacts,
                testTag = "nav_tab_contacts"
            ),
            BottomNavTab(
                route = Routes.GROUPS,
                label = strings.navGroups,
                selectedIcon = Icons.Filled.Groups,
                unselectedIcon = Icons.Outlined.Groups,
                testTag = "nav_tab_groups"
            ),
            BottomNavTab(
                route = Routes.PROFILE,
                label = strings.navProfile,
                selectedIcon = Icons.Filled.Person,
                unselectedIcon = Icons.Outlined.Person,
                testTag = "nav_tab_profile"
            )
        )
    }

    val showTopLevelNav = currentRoute in setOf(
        Routes.CHATS,
        Routes.CONTACTS,
        Routes.GROUPS,
        Routes.PROFILE
    )

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showTopLevelNav && !useRail) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelNav && useRail) {
                    NavigationRail {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.CHATS,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Routes.CHATS) {
                        ChatsListScreen(
                            viewModel = chatsViewModel,
                            currentUserId = currentUserId,
                            strings = strings,
                            onConversationClick = { convId ->
                                navController.navigate(Routes.chatDetail(convId))
                            },
                            onFindContactsClick = { navigateToTab(Routes.CONTACTS) }
                        )
                    }
                    composable(Routes.CONTACTS) {
                        ContactsScreen(
                            viewModel = contactsViewModel,
                            currentUserId = currentUserId,
                            language = language,
                            strings = strings,
                            onOpenConversation = { convId ->
                                navController.navigate(Routes.chatDetail(convId))
                            }
                        )
                    }
                    composable(Routes.GROUPS) {
                        GroupsScreen(
                            viewModel = groupsViewModel,
                            strings = strings,
                            onGroupClick = { groupId ->
                                navController.navigate(Routes.groupDetail(groupId))
                            }
                        )
                    }
                    composable(Routes.PROFILE) {
                        ProfileScreen(
                            viewModel = profileViewModel,
                            language = language,
                            strings = strings,
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            language = language,
                            themeMode = themeMode,
                            strings = strings,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = Routes.CHAT_DETAIL,
                        arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                    ) { entry ->
                        val convId = entry.arguments?.getString("conversationId").orEmpty()
                        ChatDetailScreen(
                            conversationId = convId,
                            viewModel = chatsViewModel,
                            currentUserId = currentUserId,
                            strings = strings,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = Routes.GROUP_DETAIL,
                        arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                    ) { entry ->
                        val groupId = entry.arguments?.getString("groupId").orEmpty()
                        GroupDetailScreen(
                            groupId = groupId,
                            viewModel = groupsViewModel,
                            currentUserId = currentUserId,
                            strings = strings,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
