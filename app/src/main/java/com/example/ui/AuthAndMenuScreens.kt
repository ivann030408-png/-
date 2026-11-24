package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.GameRoom
import com.example.data.GameSettings
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.data.attemptAutoSignIn
import com.example.data.onGoogleSignInClicked
import com.example.engine.GameMap
import com.example.ui.theme.ArenaSkyCyan
import com.example.ui.theme.BeakAmber
import com.example.ui.theme.BunkerSlate
import com.example.ui.theme.CombCrimson
import com.example.ui.theme.ExplosiveOrange
import com.example.ui.theme.FeatherWhite
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NitroEmerald
import com.example.ui.theme.SteelSurface
import com.example.ui.theme.TacticalNavy
import kotlinx.coroutines.delay

/**
 * Fullscreen menu slideshow backgrounds (easy to extend with new drawable entries).
 * Automatically cycles every 7 seconds with a smooth 1.35s crossfade behind the entire UI.
 */
val MENU_BACKGROUNDS: List<Int> = listOf(
    R.drawable.img_bg_blue_chicken_1795199604978,
    R.drawable.lobby_hero_poster,
    R.drawable.img_bg_chalohouse_sunset_1795199606926,
    R.drawable.img_bg_stunt_arena_1795199607105,
    R.drawable.img_menu_arena_1795119038541
)

internal val GlassPanelFill = Color(0xFF0A1220).copy(alpha = 0.52f)
internal val GlassPanelSoftFill = Color(0xFF0A1220).copy(alpha = 0.44f)
internal val GlassBorderLight = FeatherWhite.copy(alpha = 0.16f)

@Composable
fun AuthScreen(
    settings: GameSettings = GameSettings(),
    onUpdateSettings: ((GameSettings) -> GameSettings) -> Unit = {},
    onAuthSuccess: () -> Unit,
    onPlayOfflineSolo: () -> Unit,
    onQuickPeaceful: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var mapSkinPickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    ArenaBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(GlassPanelFill)
                    .border(1.dp, FeatherWhite.copy(alpha = 0.24f), CircleShape)
                    .testTag("auth_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.cd_open_settings),
                    tint = BeakAmber
                )
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // LEFT: Clean floating title directly over the fullscreen slideshow + translucent Map/Skin bar
                Column(
                    modifier = Modifier
                        .weight(1.12f)
                        .fillMaxSize()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "ПЕТУШИНЫЕ БОИ",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.78f),
                                    offset = Offset(0f, 4f),
                                    blurRadius = 14f
                                )
                            ),
                            color = FeatherWhite
                        )
                        Text(
                            text = stringResource(R.string.tagline),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.80f),
                                    offset = Offset(0f, 2f),
                                    blurRadius = 10f
                                )
                            ),
                            color = BeakAmber
                        )
                        Text(
                            text = "Карта «Полигон» • 6 скинов • 9 машин • 70+ 3D-моделей • 120 FPS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    offset = Offset(0f, 2f),
                                    blurRadius = 8f
                                )
                            ),
                            color = FeatherWhite.copy(alpha = 0.88f)
                        )
                    }

                    MapAndSkinQuickBar(
                        selectedMap = settings.selectedMap,
                        selectedSkin = settings.selectedSkin,
                        onOpenPicker = { mapSkinPickerOpen = true },
                        selectedWeaponSkin = settings.weaponSkin,
                        onToggleWeaponSkin = { onUpdateSettings { it.copy(weaponSkin = it.weaponSkin.next()) } }
                    )
                }

                // RIGHT: Translucent Glass Mode Selection & Google Sign-In Panel
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = GlassPanelFill,
                    border = BorderStroke(1.dp, GlassBorderLight),
                    modifier = Modifier.weight(0.88f)
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ИГРАТЬ СЕЙЧАС",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = FeatherWhite
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onQuickPeaceful,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NitroEmerald,
                            contentColor = TacticalNavy
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auth_quick_peaceful_button")
                    ) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${stringResource(R.string.btn_quick_peaceful)} (Стройка)",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            isLoading = true
                            errorMessage = null
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isLoading = false
                                    errorMessage = msg
                                },
                                scope = scope,
                                onAuthCancelled = { isLoading = false }
                            )
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BeakAmber,
                            contentColor = TacticalNavy
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = TacticalNavy,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(Icons.Filled.Login, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_sign_in_google),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CombCrimson
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = onPlayOfflineSolo,
                            border = BorderStroke(1.5.dp, ArenaSkyCyan.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("offline_solo_button")
                        ) {
                            Icon(Icons.Filled.SmartToy, contentDescription = null, tint = ArenaSkyCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_play_offline_solo),
                                style = MaterialTheme.typography.labelLarge,
                                color = FeatherWhite
                            )
                        }
                    }
                }
            }
        }
        if (mapSkinPickerOpen) {
            MapAndSkinPickerDialog(
                selectedMap = settings.selectedMap,
                selectedSkin = settings.selectedSkin,
                onSelectMap = { map -> onUpdateSettings { it.copy(selectedMap = map) } },
                onSelectSkin = { skin -> onUpdateSettings { it.copy(selectedSkin = skin) } },
                onDismiss = { mapSkinPickerOpen = false }
            )
        }
    }
}

private enum class MainMenuLeftTab {
    MISSIONS,
    LEADERBOARD
}

@Composable
fun MainMenuScreen(
    viewModel: LobbyViewModel,
    settings: GameSettings = GameSettings(),
    onUpdateSettings: ((GameSettings) -> GameSettings) -> Unit = {},
    onStartSoloBots: () -> Unit,
    onQuickPeaceful: () -> Unit,
    onOpenOnlineLobby: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val leaderboardState by viewModel.leaderboardState.collectAsStateWithLifecycle()
    val dailyMissionsState by viewModel.dailyMissionsState.collectAsStateWithLifecycle()
    val claimingMissionId by viewModel.claimingMissionId.collectAsStateWithLifecycle()
    val missionFeedback by viewModel.missionFeedback.collectAsStateWithLifecycle()
    val profileSaveFeedback by viewModel.profileSaveFeedback.collectAsStateWithLifecycle()
    val profile = (profileState as? UiState.Success)?.data
    val missionsList = (dailyMissionsState as? UiState.Success)?.data.orEmpty()
    val claimableCount = missionsList.count { it.status == "completed" }

    var activeLeftTab by remember { mutableStateOf(MainMenuLeftTab.MISSIONS) }
    var sortMode by remember { mutableStateOf(LeaderboardSort.KILLS) }
    var leaderboardExpanded by remember { mutableStateOf(false) }
    var missionsExpanded by remember { mutableStateOf(false) }
    var mapSkinPickerOpen by remember { mutableStateOf(false) }
    var profileDialogOpen by remember { mutableStateOf(false) }

    ArenaBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Top bar: Clean floating title directly over the slideshow + compact profile & glass actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            text = "ПЕТУШИНЫЕ БОИ",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.78f),
                                    offset = Offset(0f, 3f),
                                    blurRadius = 12f
                                )
                            ),
                            color = FeatherWhite
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.tagline),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.78f),
                                        offset = Offset(0f, 2f),
                                        blurRadius = 8f
                                    )
                                ),
                                color = BeakAmber,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "• ${profile?.nickname ?: viewModel.initialDisplayName}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.78f),
                                        offset = Offset(0f, 2f),
                                        blurRadius = 8f
                                    )
                                ),
                                color = FeatherWhite.copy(alpha = 0.88f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CoinsBalanceBadge(coins = profile?.coins ?: 0L)

                    OutlinedButton(
                        onClick = {
                            viewModel.clearProfileSaveFeedback()
                            profileDialogOpen = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassPanelSoftFill),
                        border = BorderStroke(1.dp, ArenaSkyCyan.copy(alpha = 0.55f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("menu_profile_button")
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = stringResource(R.string.cd_open_profile),
                            tint = ArenaSkyCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(stringResource(R.string.profile_btn), color = FeatherWhite)
                    }

                    OutlinedButton(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassPanelSoftFill),
                        border = BorderStroke(1.dp, GlassBorderLight),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("menu_settings_button")
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.cd_open_settings),
                            tint = BeakAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(stringResource(R.string.settings_btn), color = FeatherWhite)
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassPanelSoftFill),
                        border = BorderStroke(1.dp, GlassBorderLight),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = FeatherWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(stringResource(R.string.btn_sign_out), color = FeatherWhite)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Left column: Glass Career Stats + Glass Tabbed Daily Missions / Global Leaderboard
                Column(
                    modifier = Modifier
                        .weight(1.06f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileStatsRow(profile)

                    // Lightweight Glass Tab Bar switching between Daily Missions and Global Leaderboard
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = GlassPanelFill,
                        border = BorderStroke(1.dp, GlassBorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MainMenuTabChip(
                                label = stringResource(R.string.missions_tab_title),
                                icon = Icons.Filled.TaskAlt,
                                badgeCount = claimableCount,
                                selected = activeLeftTab == MainMenuLeftTab.MISSIONS,
                                accent = NitroEmerald,
                                testTag = "main_menu_tab_missions",
                                onClick = { activeLeftTab = MainMenuLeftTab.MISSIONS },
                                modifier = Modifier.weight(1f)
                            )
                            MainMenuTabChip(
                                label = stringResource(R.string.leaderboard_tab_title),
                                icon = Icons.Filled.EmojiEvents,
                                badgeCount = 0,
                                selected = activeLeftTab == MainMenuLeftTab.LEADERBOARD,
                                accent = BeakAmber,
                                testTag = "main_menu_tab_leaderboard",
                                onClick = { activeLeftTab = MainMenuLeftTab.LEADERBOARD },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    when (activeLeftTab) {
                        MainMenuLeftTab.MISSIONS -> {
                            DailyMissionsCard(
                                state = dailyMissionsState,
                                claimingMissionId = claimingMissionId,
                                feedbackMessage = missionFeedback,
                                onClearFeedback = { viewModel.clearMissionFeedback() },
                                onClaimMission = { missionId -> viewModel.claimMissionReward(missionId) },
                                onExpand = { missionsExpanded = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        MainMenuLeftTab.LEADERBOARD -> {
                            GlobalLeaderboardCard(
                                state = leaderboardState,
                                currentUserId = viewModel.currentUserId,
                                sortMode = sortMode,
                                onSortModeChange = { sortMode = it },
                                onExpand = { leaderboardExpanded = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Right column: Prominent START Hero CTA + Skin/Weapon Bar + Clean Game Mode Cards
                Column(
                    modifier = Modifier
                        .weight(1.04f)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    ProminentStartHeroButton(
                        onClick = onOpenOnlineLobby
                    )
                    MapAndSkinQuickBar(
                        selectedMap = settings.selectedMap,
                        selectedSkin = settings.selectedSkin,
                        onOpenPicker = { mapSkinPickerOpen = true },
                        selectedWeaponSkin = settings.weaponSkin,
                        onToggleWeaponSkin = { onUpdateSettings { it.copy(weaponSkin = it.weaponSkin.next()) } }
                    )
                    ModeActionCard(
                        title = stringResource(R.string.btn_online_rooms),
                        subtitle = "Онлайн-мультиплеер на карте «Полигон» • чат • постройки и транспорт",
                        accentColor = ArenaSkyCyan,
                        icon = Icons.Filled.Public,
                        testTag = "menu_online_rooms_button",
                        onClick = onOpenOnlineLobby
                    )
                    ModeActionCard(
                        title = stringResource(R.string.btn_solo_bots),
                        subtitle = "Одиночный бой с ботами и Хряками-Бургерами на карте «Полигон»",
                        accentColor = BeakAmber,
                        icon = Icons.Filled.SmartToy,
                        testTag = "menu_solo_bots_button",
                        onClick = onStartSoloBots
                    )
                    ModeActionCard(
                        title = stringResource(R.string.btn_quick_peaceful),
                        subtitle = "Свободная стройка на «Полигоне» • 70+ 3D-объектов и 9 машин без урона",
                        accentColor = NitroEmerald,
                        icon = Icons.Filled.DirectionsCar,
                        testTag = "menu_quick_peaceful_button",
                        onClick = onQuickPeaceful
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        if (mapSkinPickerOpen) {
            MapAndSkinPickerDialog(
                selectedMap = settings.selectedMap,
                selectedSkin = settings.selectedSkin,
                onSelectMap = { map -> onUpdateSettings { it.copy(selectedMap = map) } },
                onSelectSkin = { skin -> onUpdateSettings { it.copy(selectedSkin = skin) } },
                onDismiss = { mapSkinPickerOpen = false }
            )
        }

        if (leaderboardExpanded) {
            GlobalLeaderboardDialog(
                state = leaderboardState,
                currentUserId = viewModel.currentUserId,
                sortMode = sortMode,
                onSortModeChange = { sortMode = it },
                onDismiss = { leaderboardExpanded = false }
            )
        }

        if (missionsExpanded) {
            DailyMissionsDialog(
                state = dailyMissionsState,
                coins = profile?.coins ?: 0L,
                claimingMissionId = claimingMissionId,
                feedbackMessage = missionFeedback,
                onClaimMission = { missionId -> viewModel.claimMissionReward(missionId) },
                onDismiss = { missionsExpanded = false }
            )
        }

        if (profileDialogOpen) {
            PlayerProfileDialog(
                profile = profile,
                fallbackNickname = viewModel.initialDisplayName,
                selectedSkin = settings.selectedSkin,
                weaponSkin = settings.weaponSkin,
                saveFeedback = profileSaveFeedback,
                onSelectSkin = { skin -> onUpdateSettings { it.copy(selectedSkin = skin) } },
                onSelectWeaponSkin = { wSkin -> onUpdateSettings { it.copy(weaponSkin = wSkin) } },
                onSaveProfile = { nickname, skin, wSkin ->
                    onUpdateSettings { it.copy(selectedSkin = skin, weaponSkin = wSkin) }
                    viewModel.saveProfileCustomization(nickname, skin.name, wSkin.name)
                },
                onDismiss = {
                    viewModel.clearProfileSaveFeedback()
                    profileDialogOpen = false
                }
            )
        }
    }
}

@Composable
private fun PlayerProfileDialog(
    profile: UserProfile?,
    fallbackNickname: String,
    selectedSkin: com.example.engine.ChickenSkin,
    weaponSkin: com.example.engine.WeaponSkin,
    saveFeedback: String?,
    onSelectSkin: (com.example.engine.ChickenSkin) -> Unit,
    onSelectWeaponSkin: (com.example.engine.WeaponSkin) -> Unit,
    onSaveProfile: (String, com.example.engine.ChickenSkin, com.example.engine.WeaponSkin) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    var nicknameInput by remember(profile?.nickname, fallbackNickname) {
        mutableStateOf(profile?.nickname?.takeIf { it.isNotBlank() } ?: fallbackNickname)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.68f))
            .clickable(onClick = onDismiss)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = BunkerSlate),
            border = BorderStroke(1.dp, ArenaSkyCyan.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .clickable(enabled = false) {}
                .testTag("player_profile_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = NitroEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.profile_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = FeatherWhite
                            )
                            Text(
                                text = stringResource(R.string.profile_cloud_synced),
                                style = MaterialTheme.typography.labelSmall,
                                color = NitroEmerald
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_profile_dialog_button")
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.btn_back),
                            tint = FeatherWhite
                        )
                    }
                }

                // Career statistics strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileStatTile(
                        label = stringResource(R.string.stat_kills),
                        value = (profile?.totalKills ?: 0L).toString(),
                        accent = CombCrimson,
                        modifier = Modifier.weight(1f)
                    )
                    ProfileStatTile(
                        label = stringResource(R.string.stat_wins),
                        value = (profile?.totalWins ?: 0L).toString(),
                        accent = NitroEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    ProfileStatTile(
                        label = stringResource(R.string.stat_matches),
                        value = (profile?.totalMatches ?: 0L).toString(),
                        accent = ArenaSkyCyan,
                        modifier = Modifier.weight(1f)
                    )
                    ProfileStatTile(
                        label = stringResource(R.string.profile_stat_streak),
                        value = (profile?.bestStreak ?: 0L).toString(),
                        accent = BeakAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Editable nickname
                OutlinedTextField(
                    value = nicknameInput,
                    onValueChange = { if (it.length <= 30) nicknameInput = it },
                    label = { Text(stringResource(R.string.profile_nickname_label)) },
                    singleLine = true,
                    colors = lobbyTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_nickname_input")
                )

                // Cloud-synced Skins section
                Text(
                    text = stringResource(R.string.profile_skin_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = BeakAmber,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    com.example.engine.ChickenSkin.entries.forEach { skin ->
                        val active = skin == selectedSkin
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) BeakAmber.copy(alpha = 0.24f) else TacticalNavy,
                            border = BorderStroke(
                                width = if (active) 1.5.dp else 1.dp,
                                color = if (active) BeakAmber else SteelSurface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clickable { onSelectSkin(skin) }
                                .testTag("profile_skin_${skin.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(skin.vestR, skin.vestG, skin.vestB))
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = skin.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) BeakAmber else FeatherWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.example.engine.WeaponSkin.entries.forEach { wSkin ->
                        val active = wSkin == weaponSkin
                        OutlinedButton(
                            onClick = { onSelectWeaponSkin(wSkin) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (active) ArenaSkyCyan.copy(alpha = 0.22f) else TacticalNavy
                            ),
                            border = BorderStroke(
                                width = if (active) 1.5.dp else 1.dp,
                                color = if (active) ArenaSkyCyan else SteelSurface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("profile_weapon_skin_${wSkin.name.lowercase()}")
                        ) {
                            Text(
                                text = "Оружие: ${wSkin.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) ArenaSkyCyan else FeatherWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (!saveFeedback.isNullOrBlank()) {
                    Text(
                        text = saveFeedback,
                        style = MaterialTheme.typography.labelMedium,
                        color = NitroEmerald,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        val trimmed = nicknameInput.trim().ifEmpty { fallbackNickname }
                        onSaveProfile(trimmed, selectedSkin, weaponSkin)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NitroEmerald),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("profile_save_button")
                ) {
                    Text(
                        text = stringResource(R.string.profile_save_btn),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF04140B)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileStatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = TacticalNavy,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MutedSteel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MainMenuTabChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeCount: Int,
    selected: Boolean,
    accent: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) accent.copy(alpha = 0.7f) else Color.Transparent,
                shape = RoundedCornerShape(9.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) accent else MutedSteel,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) FeatherWhite else MutedSteel
        )
        if (badgeCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(NitroEmerald),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = TacticalNavy
                )
            }
        }
    }
}

private enum class RoomBrowserFilter(val label: String) {
    ALL("Все"),
    OPEN_ONLY("Открытые"),
    PINNED("Закреплённые")
}

@Composable
fun OnlineLobbyScreen(
    viewModel: LobbyViewModel,
    initialMap: GameMap = GameMap.MAIN_ARENA,
    onBack: () -> Unit,
    onRoomEntered: (GameRoom) -> Unit
) {
    BackHandler { onBack() }

    val openRoomsState by viewModel.openRoomsState.collectAsStateWithLifecycle()
    val loading by viewModel.actionLoading.collectAsStateWithLifecycle()
    val errorMsg by viewModel.actionError.collectAsStateWithLifecycle()
    val roomClosedNotice by viewModel.roomClosedNotice.collectAsStateWithLifecycle()

    var joinCodeInput by remember { mutableStateOf("") }
    var joinPasswordInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var roomFilter by remember { mutableStateOf(RoomBrowserFilter.ALL) }
    var createDialogOpen by remember { mutableStateOf(false) }
    var passwordPromptRoom by remember { mutableStateOf<GameRoom?>(null) }

    ArenaBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(BunkerSlate.copy(alpha = 0.85f))
                            .testTag("lobby_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back),
                            tint = FeatherWhite
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ОНЛАЙН-КОМНАТЫ",
                            style = MaterialTheme.typography.headlineMedium,
                            color = FeatherWhite
                        )
                        Text(
                            text = "Только игроки • без ботов • живые настройки хоста",
                            style = MaterialTheme.typography.bodySmall,
                            color = ArenaSkyCyan
                        )
                    }
                }

                Button(
                    onClick = { createDialogOpen = true },
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BeakAmber,
                        contentColor = TacticalNavy
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("open_create_room_dialog_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_create_room), fontWeight = FontWeight.ExtraBold)
                }
            }

            if (roomClosedNotice != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = BeakAmber.copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, BeakAmber),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 880.dp)
                        .testTag("room_closed_notice_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = roomClosedNotice!!,
                            color = FeatherWhite,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearRoomClosedNotice() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Скрыть уведомление", tint = FeatherWhite)
                        }
                    }
                }
            }

            if (errorMsg != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = CombCrimson.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, CombCrimson),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 880.dp)
                ) {
                    Text(
                        text = errorMsg!!,
                        color = FeatherWhite,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Two-column Landscape Browser: Left = Quick Create & Join by Code, Right = Active Rooms Browser
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.86f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Extended Create Room Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = GlassPanelFill,
                        border = BorderStroke(1.dp, BeakAmber.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Tune, contentDescription = null, tint = BeakAmber, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Своя комната",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = FeatherWhite
                                )
                            }
                            Text(
                                text = "Выбор из 3 карт, режим, пароль, закрепление, правила стройки и PvP. Боты отключены.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedSteel
                            )
                            Button(
                                onClick = { createDialogOpen = true },
                                enabled = !loading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BeakAmber,
                                    contentColor = TacticalNavy
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("create_room_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("НАСТРОИТЬ И СОЗДАТЬ", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    // Join by Code (+ optional password) Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = GlassPanelFill,
                        border = BorderStroke(1.dp, GlassBorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = joinCodeInput,
                                onValueChange = { if (it.length <= 12) joinCodeInput = it.uppercase() },
                                label = { Text(stringResource(R.string.label_room_code)) },
                                placeholder = { Text("CHKN42") },
                                singleLine = true,
                                colors = lobbyTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("room_code_input")
                            )
                            OutlinedTextField(
                                value = joinPasswordInput,
                                onValueChange = { if (it.length <= 24) joinPasswordInput = it },
                                label = { Text("Пароль (если нужен)") },
                                singleLine = true,
                                colors = lobbyTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("room_code_password_input")
                            )
                            Button(
                                onClick = {
                                    viewModel.joinRoomByCode(
                                        code = joinCodeInput,
                                        password = joinPasswordInput,
                                        onJoined = onRoomEntered
                                    )
                                },
                                enabled = !loading && joinCodeInput.trim().length >= 4,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ArenaSkyCyan,
                                    contentColor = TacticalNavy
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("join_code_button")
                            ) {
                                Icon(Icons.Filled.Key, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.btn_join_code), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Right Column: Active Rooms Live Browser with Search & Filter
                Column(
                    modifier = Modifier.weight(1.38f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { if (it.length <= 32) searchQuery = it },
                            placeholder = { Text("Поиск по названию, коду или карте") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ArenaSkyCyan) },
                            singleLine = true,
                            colors = lobbyTextFieldColors(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rooms_search_input")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RoomBrowserFilter.entries.forEach { filter ->
                            val selected = roomFilter == filter
                            Surface(
                                onClick = { roomFilter = filter },
                                color = if (selected) ArenaSkyCyan else GlassPanelFill,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (selected) ArenaSkyCyan else GlassBorderLight),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("rooms_filter_${filter.name.lowercase()}")
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = filter.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) TacticalNavy else FeatherWhite
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        val count = (openRoomsState as? UiState.Success)?.data?.size ?: 0
                        Text(
                            text = "Комнат: $count",
                            style = MaterialTheme.typography.labelMedium,
                            color = NitroEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (val state = openRoomsState) {
                            is UiState.Loading -> {
                                CircularProgressIndicator(
                                    color = BeakAmber,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            is UiState.Error -> {
                                Text(
                                    text = state.message,
                                    color = CombCrimson,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            is UiState.Success -> {
                                val query = searchQuery.trim().lowercase()
                                val filteredRooms = remember(state.data, query, roomFilter) {
                                    state.data.filter { room ->
                                        val filterMatch = when (roomFilter) {
                                            RoomBrowserFilter.ALL -> true
                                            RoomBrowserFilter.OPEN_ONLY -> !room.privateRoom && !room.joinLocked && room.memberUids.size < room.maxPlayers
                                            RoomBrowserFilter.PINNED -> room.pinned
                                        }
                                        val mapLabel = GameMap.fromNameSafe(room.mapId).label.lowercase()
                                        val modeLabel = room.resolvedGameMode().label.lowercase()
                                        val queryMatch = query.isEmpty() ||
                                            room.name.lowercase().contains(query) ||
                                            room.roomCode.lowercase().contains(query) ||
                                            mapLabel.contains(query) ||
                                            modeLabel.contains(query)
                                        filterMatch && queryMatch
                                    }
                                }

                                if (filteredRooms.isEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = BunkerSlate.copy(alpha = 0.78f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                Icons.Filled.SportsEsports,
                                                contentDescription = null,
                                                tint = MutedSteel,
                                                modifier = Modifier.size(34.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = if (state.data.isEmpty()) stringResource(R.string.empty_rooms_hint)
                                                else "По фильтру комнат не найдено",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MutedSteel
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(bottom = 12.dp)
                                    ) {
                                        items(filteredRooms, key = { it.id }) { room ->
                                            RoomListItemCard(
                                                room = room,
                                                enabled = !loading,
                                                onJoin = {
                                                    if (room.privateRoom) {
                                                        passwordPromptRoom = room
                                                    } else {
                                                        viewModel.joinRoomById(
                                                            roomId = room.id,
                                                            onJoined = onRoomEntered
                                                        )
                                                    }
                                                }
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

        if (createDialogOpen) {
            CreateOnlineRoomDialog(
                initialMap = initialMap,
                loading = loading,
                onCreate = { config ->
                    viewModel.createAndEnterRoom(config) { room ->
                        createDialogOpen = false
                        onRoomEntered(room)
                    }
                },
                onDismiss = { createDialogOpen = false }
            )
        }

        passwordPromptRoom?.let { targetRoom ->
            RoomPasswordDialog(
                room = targetRoom,
                onConfirm = { enteredPassword ->
                    passwordPromptRoom = null
                    viewModel.joinRoomById(
                        roomId = targetRoom.id,
                        password = enteredPassword,
                        onJoined = onRoomEntered
                    )
                },
                onDismiss = { passwordPromptRoom = null }
            )
        }
    }
}

@Composable
private fun RoomListItemCard(
    room: GameRoom,
    enabled: Boolean,
    onJoin: () -> Unit
) {
    val map = GameMap.fromNameSafe(room.mapId)
    val mode = room.resolvedGameMode()
    val isFull = room.memberUids.size >= room.maxPlayers
    val canJoin = enabled && !room.joinLocked && !isFull

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassPanelFill),
        border = BorderStroke(
            width = if (room.pinned) 1.5.dp else 1.dp,
            color = if (room.pinned) BeakAmber.copy(alpha = 0.78f) else GlassBorderLight
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("room_card_${room.roomCode}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (room.pinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = "Закреплённая комната",
                            tint = BeakAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (room.privateRoom) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "Приватная с паролем",
                            tint = ArenaSkyCyan,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FeatherWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RoomMiniBadge(text = map.label, color = ArenaSkyCyan)
                    RoomMiniBadge(text = mode.label, color = BeakAmber)
                    RoomMiniBadge(text = "${room.memberUids.size}/${room.maxPlayers}", color = NitroEmerald)
                    if (room.joinLocked) {
                        RoomMiniBadge(text = "ЗАКРЫТ", color = CombCrimson)
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onJoin,
                enabled = canJoin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (room.privateRoom) ArenaSkyCyan else NitroEmerald,
                    contentColor = TacticalNavy
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("join_room_${room.roomCode}")
            ) {
                Icon(
                    imageVector = if (room.privateRoom) Icons.Filled.Lock else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when {
                        room.joinLocked -> "ЗАКРЫТО"
                        isFull -> "ПОЛНАЯ"
                        else -> "ВОЙТИ"
                    },
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun RoomMiniBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.8.dp, color.copy(alpha = 0.55f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ProfileStatsRow(profile: UserProfile?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatChip(
            label = stringResource(R.string.stat_kills),
            value = (profile?.totalKills ?: 0L).toString(),
            accent = ExplosiveOrange,
            modifier = Modifier.weight(1f)
        )
        StatChip(
            label = stringResource(R.string.stat_wins),
            value = (profile?.totalWins ?: 0L).toString(),
            accent = BeakAmber,
            modifier = Modifier.weight(1f)
        )
        StatChip(
            label = stringResource(R.string.stat_matches),
            value = (profile?.totalMatches ?: 0L).toString(),
            accent = ArenaSkyCyan,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatChip(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = GlassPanelFill,
        border = BorderStroke(1.dp, GlassBorderLight),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = FeatherWhite.copy(alpha = 0.72f))
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
            }
            Icon(
                Icons.Filled.MilitaryTech,
                contentDescription = null,
                tint = accent.copy(alpha = 0.80f),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun ProminentStartHeroButton(
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(1.8.dp, NitroEmerald),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("menu_start_play_button")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            NitroEmerald.copy(alpha = 0.92f),
                            ArenaSkyCyan.copy(alpha = 0.88f),
                            BeakAmber.copy(alpha = 0.90f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(TacticalNavy.copy(alpha = 0.88f))
                        .border(1.5.dp, FeatherWhite.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Старт",
                        tint = NitroEmerald,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column {
                    Text(
                        text = "ИГРАТЬ • В БОЙ!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = TacticalNavy,
                        letterSpacing = 1.1.sp
                    )
                    Text(
                        text = "Арена «Полигон» • онлайн-комнаты и быстрый старт",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TacticalNavy.copy(alpha = 0.84f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = TacticalNavy.copy(alpha = 0.88f)
            ) {
                Text(
                    text = "СТАРТ",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = NitroEmerald,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeActionCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassPanelFill),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FeatherWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = FeatherWhite.copy(alpha = 0.76f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
internal fun ArenaBackdrop(content: @Composable () -> Unit) {
    var activeBgIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(MENU_BACKGROUNDS.size) {
        if (MENU_BACKGROUNDS.size > 1) {
            while (true) {
                delay(7000L)
                activeBgIndex = (activeBgIndex + 1) % MENU_BACKGROUNDS.size
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = MENU_BACKGROUNDS[activeBgIndex],
            animationSpec = tween(durationMillis = 1350),
            label = "menu_background_slideshow",
            modifier = Modifier.fillMaxSize()
        ) { resId ->
            Image(
                painter = painterResource(id = resId),
                contentDescription = stringResource(R.string.cd_hero_banner),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Light atmospheric gradient scrim: keeps photos vivid while guaranteeing high text legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF070C16).copy(alpha = 0.44f),
                            0.45f to Color(0xFF070C16).copy(alpha = 0.28f),
                            1.0f to Color(0xFF070C16).copy(alpha = 0.58f)
                        )
                    )
                )
        )
        content()
    }
}

@Composable
private fun lobbyTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BeakAmber,
    unfocusedBorderColor = SteelSurface,
    focusedTextColor = FeatherWhite,
    unfocusedTextColor = FeatherWhite,
    focusedLabelColor = BeakAmber,
    unfocusedLabelColor = MutedSteel,
    cursorColor = BeakAmber
)
