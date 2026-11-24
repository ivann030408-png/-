package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.GameRoom
import com.example.data.GameSettings
import com.example.data.GameSettingsStore
import com.example.data.MultiplayerRepository
import com.example.data.UiState
import com.example.data.authStateFlow
import com.example.data.signOutUser
import com.example.engine.ChickenSkin
import com.example.engine.LobbyBotSlot
import com.example.engine.LobbyMusicPlayer
import com.example.engine.WeaponSkin
import com.example.ui.AuthScreen
import com.example.ui.GameScreen
import com.example.ui.LobbyViewModel
import com.example.ui.MainMenuScreen
import com.example.ui.OnlineLobbyScreen
import com.example.ui.RoomWaitingLobbyScreen
import com.example.ui.SettingsOverlay
import com.example.ui.theme.ChickenGunTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private sealed interface AppDestination {
    data object MainMenu : AppDestination
    data object OnlineLobby : AppDestination
    data object SoloWaitingLobby : AppDestination
    data class OnlineWaitingLobby(val room: GameRoom) : AppDestination
    data class SoloGame(
        val botRoster: List<LobbyBotSlot>,
        val peacefulMode: Boolean = false,
        val openBuildInitially: Boolean = false
    ) : AppDestination
    data class OnlineGame(
        val room: GameRoom,
        val botRoster: List<LobbyBotSlot>,
        val peacefulMode: Boolean = false
    ) : AppDestination
}

private sealed interface OfflineFlowState {
    data object Auth : OfflineFlowState
    data object SoloLobby : OfflineFlowState
    data class SoloPlaying(
        val botRoster: List<LobbyBotSlot>,
        val peacefulMode: Boolean = false,
        val openBuildInitially: Boolean = false
    ) : OfflineFlowState
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureImmersiveFullscreen()
        setContent {
            ChickenGunTheme {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LobbyMusicPlayer.setAppBackgrounded(false)
    }

    override fun onPause() {
        LobbyMusicPlayer.setAppBackgrounded(true)
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing) {
            LobbyMusicPlayer.stopAndRelease()
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            configureImmersiveFullscreen()
        }
    }

    private fun configureImmersiveFullscreen() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val settingsStore = remember(context) { GameSettingsStore(context) }
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var offlineState by remember { mutableStateOf<OfflineFlowState>(OfflineFlowState.Auth) }
    var menuSettingsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(settings.masterVolume, settings.musicVolume) {
        LobbyMusicPlayer.updateSettings(settings)
    }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            offlineState = OfflineFlowState.Auth
        }
    }

    val user = currentUser
    if (user == null) {
        LaunchedEffect(offlineState) {
            LobbyMusicPlayer.setLobbyActive(offlineState !is OfflineFlowState.SoloPlaying)
        }
        when (val state = offlineState) {
            is OfflineFlowState.Auth -> {
                AuthScreen(
                    settings = settings,
                    onUpdateSettings = { settingsStore.update(it) },
                    onAuthSuccess = { offlineState = OfflineFlowState.Auth },
                    onPlayOfflineSolo = { offlineState = OfflineFlowState.SoloLobby },
                    onQuickPeaceful = {
                        offlineState = OfflineFlowState.SoloPlaying(
                            botRoster = emptyList(),
                            peacefulMode = true,
                            openBuildInitially = false
                        )
                    },
                    onOpenSettings = { menuSettingsOpen = true }
                )
            }
            is OfflineFlowState.SoloLobby -> {
                RoomWaitingLobbyScreen(
                    roomTitle = "Одиночная арена (офлайн)",
                    roomCode = null,
                    playerName = "Боец Курица",
                    onlineRoom = null,
                    lobbyViewModel = null,
                    settings = settings,
                    onUpdateSettings = { settingsStore.update(it) },
                    onLeaveLobby = { offlineState = OfflineFlowState.Auth },
                    onStartMatch = { roster, peaceful ->
                        offlineState = OfflineFlowState.SoloPlaying(
                            botRoster = roster,
                            peacefulMode = peaceful,
                            openBuildInitially = false
                        )
                    }
                )
            }
            is OfflineFlowState.SoloPlaying -> {
                GameScreen(
                    onlineRoom = null,
                    lobbyViewModel = null,
                    settings = settings,
                    onUpdateSettings = { settingsStore.update(it) },
                    onResetSettings = { settingsStore.resetToDefaults() },
                    botRoster = state.botRoster,
                    peacefulMode = state.peacefulMode,
                    openBuildInitially = state.openBuildInitially,
                    onExitToMenu = {
                        offlineState = if (state.peacefulMode && state.botRoster.isEmpty()) {
                            OfflineFlowState.Auth
                        } else {
                            OfflineFlowState.SoloLobby
                        }
                    }
                )
            }
        }
    } else {
        val uid = user.uid
        val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Боец Курица"
        key(uid) {
            AuthenticatedAppShell(
                currentUserId = uid,
                displayName = displayName,
                settings = settings,
                onUpdateSettings = { settingsStore.update(it) },
                onResetSettings = { settingsStore.resetToDefaults() },
                onOpenMenuSettings = { menuSettingsOpen = true }
            )
        }
    }

    if (menuSettingsOpen) {
        SettingsOverlay(
            settings = settings,
            onUpdate = { settingsStore.update(it) },
            onReset = { settingsStore.resetToDefaults() },
            onDismiss = { menuSettingsOpen = false }
        )
    }
}

@Composable
private fun AuthenticatedAppShell(
    currentUserId: String,
    displayName: String,
    settings: GameSettings,
    onUpdateSettings: ((GameSettings) -> GameSettings) -> Unit,
    onResetSettings: () -> Unit,
    onOpenMenuSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val lobbyViewModel: LobbyViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                LobbyViewModel(
                    repository = MultiplayerRepository(db),
                    currentUserId = currentUserId,
                    initialDisplayName = displayName
                )
            }
        }
    )

    var destination by remember(currentUserId) {
        mutableStateOf<AppDestination>(AppDestination.MainMenu)
    }

    val profileState by lobbyViewModel.profileState.collectAsStateWithLifecycle()
    val cloudProfile = (profileState as? UiState.Success)?.data
    var cloudSkinsApplied by remember(currentUserId) { mutableStateOf(false) }

    // Restore cloud-saved character & weapon skins once when the signed-in profile loads
    LaunchedEffect(cloudProfile, cloudSkinsApplied) {
        val loaded = cloudProfile
        if (loaded != null && !cloudSkinsApplied) {
            cloudSkinsApplied = true
            val cloudCharSkin = ChickenSkin.fromNameSafe(loaded.selectedSkin)
            val cloudWeaponSkin = WeaponSkin.fromNameSafe(loaded.weaponSkin)
            if (settings.selectedSkin != cloudCharSkin || settings.weaponSkin != cloudWeaponSkin) {
                onUpdateSettings {
                    it.copy(
                        selectedSkin = cloudCharSkin,
                        weaponSkin = cloudWeaponSkin
                    )
                }
            }
        }
    }

    val syncingUpdateSettings: ((GameSettings) -> GameSettings) -> Unit = remember(
        onUpdateSettings,
        lobbyViewModel,
        settings.selectedSkin,
        settings.weaponSkin
    ) {
        { transform ->
            val beforeChar = settings.selectedSkin
            val beforeWep = settings.weaponSkin
            var afterChar = beforeChar
            var afterWep = beforeWep
            onUpdateSettings { current ->
                val updated = transform(current)
                afterChar = updated.selectedSkin
                afterWep = updated.weaponSkin
                updated
            }
            if (afterChar != beforeChar || afterWep != beforeWep) {
                lobbyViewModel.syncSkinsToProfile(afterChar.name, afterWep.name)
            }
        }
    }
    val effectivePlayerName = cloudProfile?.nickname?.takeIf { it.isNotBlank() } ?: displayName

    LaunchedEffect(destination) {
        val inLobby = destination !is AppDestination.SoloGame && destination !is AppDestination.OnlineGame
        LobbyMusicPlayer.setLobbyActive(inLobby)
    }

    when (val dest = destination) {
        is AppDestination.MainMenu -> {
            MainMenuScreen(
                viewModel = lobbyViewModel,
                settings = settings,
                onUpdateSettings = syncingUpdateSettings,
                onStartSoloBots = { destination = AppDestination.SoloWaitingLobby },
                onQuickPeaceful = {
                    destination = AppDestination.SoloGame(
                        botRoster = emptyList(),
                        peacefulMode = true,
                        openBuildInitially = false
                    )
                },
                onOpenOnlineLobby = {
                    lobbyViewModel.clearError()
                    destination = AppDestination.OnlineLobby
                },
                onOpenSettings = onOpenMenuSettings,
                onSignOut = {
                    signOutUser(
                        credentialManager = credentialManager,
                        scope = scope,
                        onBeforeSignOut = { lobbyViewModel.leaveActiveRoomSuspend() }
                    )
                }
            )
        }

        is AppDestination.SoloWaitingLobby -> {
            RoomWaitingLobbyScreen(
                roomTitle = "Одиночная арена",
                roomCode = null,
                playerName = effectivePlayerName,
                onlineRoom = null,
                lobbyViewModel = null,
                settings = settings,
                onUpdateSettings = syncingUpdateSettings,
                onLeaveLobby = { destination = AppDestination.MainMenu },
                onStartMatch = { roster, peaceful ->
                    destination = AppDestination.SoloGame(
                        botRoster = roster,
                        peacefulMode = peaceful,
                        openBuildInitially = false
                    )
                }
            )
        }

        is AppDestination.OnlineLobby -> {
            OnlineLobbyScreen(
                viewModel = lobbyViewModel,
                initialMap = settings.selectedMap,
                onBack = { destination = AppDestination.MainMenu },
                onRoomEntered = { room -> destination = AppDestination.OnlineWaitingLobby(room) }
            )
        }

        is AppDestination.OnlineWaitingLobby -> {
            RoomWaitingLobbyScreen(
                roomTitle = dest.room.name,
                roomCode = dest.room.roomCode,
                playerName = effectivePlayerName,
                onlineRoom = dest.room,
                lobbyViewModel = lobbyViewModel,
                settings = settings,
                onUpdateSettings = syncingUpdateSettings,
                onLeaveLobby = {
                    lobbyViewModel.leaveActiveRoom()
                    destination = AppDestination.OnlineLobby
                },
                onStartMatch = { _, _ ->
                    // Online matches are strictly humans-only (no AI bots)
                    val currentRoom = lobbyViewModel.activeRoom.value ?: dest.room
                    destination = AppDestination.OnlineGame(currentRoom, emptyList(), false)
                }
            )
        }

        is AppDestination.SoloGame -> {
            GameScreen(
                onlineRoom = null,
                lobbyViewModel = lobbyViewModel,
                settings = settings,
                onUpdateSettings = syncingUpdateSettings,
                onResetSettings = onResetSettings,
                botRoster = dest.botRoster,
                peacefulMode = dest.peacefulMode,
                openBuildInitially = dest.openBuildInitially,
                onExitToMenu = { destination = AppDestination.MainMenu }
            )
        }

        is AppDestination.OnlineGame -> {
            GameScreen(
                onlineRoom = dest.room,
                lobbyViewModel = lobbyViewModel,
                settings = settings,
                onUpdateSettings = syncingUpdateSettings,
                onResetSettings = onResetSettings,
                botRoster = emptyList(),
                peacefulMode = false,
                openBuildInitially = false,
                onExitToMenu = {
                    lobbyViewModel.leaveActiveRoom()
                    destination = AppDestination.OnlineLobby
                }
            )
        }
    }
}
