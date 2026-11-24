package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.GameRoom
import com.example.data.GameSettings
import com.example.data.RoomMessage
import com.example.data.RoomPlayer
import com.example.engine.GameMap
import com.example.engine.LobbyBotSlot
import com.example.ui.theme.ArenaSkyCyan
import com.example.ui.theme.BeakAmber
import com.example.ui.theme.BunkerSlate
import com.example.ui.theme.CombCrimson
import com.example.ui.theme.FeatherWhite
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NitroEmerald
import com.example.ui.theme.SteelSurface
import com.example.ui.theme.TacticalNavy

/**
 * Landscape Room Waiting Lobby:
 * Shows room code, live players + customizable AI combat chicken bots (add/remove before match),
 * Peaceful Sandbox Mode toggle, and a large prominent "ПУСК" (Start Game) button.
 */
@Composable
fun RoomWaitingLobbyScreen(
    roomTitle: String,
    roomCode: String?,
    playerName: String,
    onlineRoom: GameRoom?,
    lobbyViewModel: LobbyViewModel?,
    settings: GameSettings = GameSettings(),
    onUpdateSettings: ((GameSettings) -> GameSettings) -> Unit = {},
    onLeaveLobby: () -> Unit,
    onStartMatch: (List<LobbyBotSlot>, Boolean) -> Unit
) {
    BackHandler { onLeaveLobby() }

    val isOnlineLobby = onlineRoom != null && lobbyViewModel != null
    val liveRoom: GameRoom? = if (isOnlineLobby) {
        val active by lobbyViewModel.activeRoom.collectAsStateWithLifecycle()
        active ?: onlineRoom
    } else {
        onlineRoom
    }
    val isLocalHost = liveRoom != null && liveRoom.hostId == lobbyViewModel?.currentUserId
    val roomClosed = isOnlineLobby && liveRoom?.status != null && liveRoom.status != "open"
    val allOnlinePlayers: List<RoomPlayer> = if (isOnlineLobby) {
        val list by lobbyViewModel.roomPlayers.collectAsStateWithLifecycle()
        list
    } else {
        emptyList()
    }
    val remotePlayers: List<RoomPlayer> = remember(allOnlinePlayers, lobbyViewModel?.currentUserId) {
        allOnlinePlayers.filter { it.userId != lobbyViewModel?.currentUserId }
    }
    val roomMessages: List<RoomMessage> = if (isOnlineLobby) {
        val msgs by lobbyViewModel.roomMessages.collectAsStateWithLifecycle()
        msgs
    } else {
        emptyList()
    }
    val chatError: String? = if (isOnlineLobby) {
        val err by lobbyViewModel.chatError.collectAsStateWithLifecycle()
        err
    } else {
        null
    }
    val roomClosedNotice: String? = if (isOnlineLobby) {
        val notice by lobbyViewModel.roomClosedNotice.collectAsStateWithLifecycle()
        notice
    } else {
        null
    }

    LaunchedEffect(roomClosedNotice) {
        if (isOnlineLobby && roomClosedNotice != null) {
            onLeaveLobby()
        }
    }

    // Online rooms are strictly humans-only (zero bots); offline solo keeps customizable bot roster.
    var nextBotIndex by remember(isOnlineLobby) { mutableIntStateOf(if (isOnlineLobby) 0 else 5) }
    var botRoster by remember(isOnlineLobby) {
        mutableStateOf(if (isOnlineLobby) emptyList() else LobbyBotSlot.defaultRoster(5))
    }
    var peacefulMode by remember(isOnlineLobby) { mutableStateOf(false) }
    var compactLeftTab by remember { mutableIntStateOf(0) } // 0 = Participants, 1 = Chat
    var mapSkinPickerOpen by remember { mutableStateOf(false) }
    var hostControlOpen by remember { mutableStateOf(false) }

    val displayRoomTitle = liveRoom?.name?.takeIf { it.isNotBlank() } ?: roomTitle
    val activeOnlineMap = remember(liveRoom?.mapId) { GameMap.fromNameSafe(liveRoom?.mapId) }

    ArenaBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Top Header Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onLeaveLobby,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(BunkerSlate.copy(alpha = 0.9f))
                            .testTag("waiting_lobby_back_button")
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
                            text = if (isOnlineLobby) "ОНЛАЙН-ЛОББИ • ТОЛЬКО ИГРОКИ" else stringResource(R.string.lobby_waiting_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isOnlineLobby) ArenaSkyCyan else BeakAmber
                        )
                        Text(
                            text = displayRoomTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = FeatherWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (roomCode != null) {
                    Surface(
                        color = ArenaSkyCyan.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, ArenaSkyCyan)
                    ) {
                        Text(
                            text = "КОД КОМНАТЫ: $roomCode",
                            style = MaterialTheme.typography.titleMedium,
                            color = FeatherWhite,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    Surface(
                        color = BeakAmber.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BeakAmber.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "${settings.selectedMap.label.uppercase()} • 9 МАШИН",
                            style = MaterialTheme.typography.labelLarge,
                            color = BeakAmber,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Adaptive Landscape Layout (3-column with live semi-transparent chat on wide landscape; tabbed on narrow)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val showSideBySideChat = isOnlineLobby && maxWidth >= 680.dp

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // LEFT COLUMN: Participants Roster (or tabbed Roster/Chat on compact landscape widths)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = GlassPanelFill,
                        border = BorderStroke(1.dp, GlassBorderLight),
                        modifier = Modifier
                            .weight(if (showSideBySideChat) 1.0f else 1.32f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            if (isOnlineLobby && !showSideBySideChat) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        onClick = { compactLeftTab = 0 },
                                        color = if (compactLeftTab == 0) ArenaSkyCyan else TacticalNavy.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .testTag("lobby_tab_players")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = stringResource(R.string.chat_tab_players),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (compactLeftTab == 0) TacticalNavy else FeatherWhite
                                            )
                                        }
                                    }
                                    Surface(
                                        onClick = { compactLeftTab = 1 },
                                        color = if (compactLeftTab == 1) ArenaSkyCyan else TacticalNavy.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .testTag("lobby_tab_chat")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            val suffix = if (roomMessages.isNotEmpty()) " (${roomMessages.size})" else ""
                                            Text(
                                                text = "${stringResource(R.string.chat_tab_chat)}$suffix",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (compactLeftTab == 1) TacticalNavy else FeatherWhite
                                            )
                                        }
                                    }
                                }
                            }

                            if (!isOnlineLobby || showSideBySideChat || compactLeftTab == 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Group, contentDescription = null, tint = ArenaSkyCyan)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.lobby_participants),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = FeatherWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    val totalCount = 1 + remotePlayers.size + botRoster.size
                                    Text(
                                        text = "Всего: $totalCount",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = NitroEmerald
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 6.dp)
                                ) {
                                    item(key = "local_player") {
                                        val localRole = when {
                                            roomClosed -> "Комната закрыта хостом"
                                            isOnlineLobby && isLocalHost -> "Игрок • Хост"
                                            isOnlineLobby -> "Игрок • Участник"
                                            else -> "Игрок"
                                        }
                                        ParticipantRow(
                                            name = "$playerName (Вы)",
                                            roleLabel = localRole,
                                            weaponLabel = if (peacefulMode) "Строитель • 9 машин" else "Скин: ${settings.selectedSkin.label}",
                                            accentColor = if (roomClosed) CombCrimson else BeakAmber,
                                            isBot = false,
                                            onRemove = null
                                        )
                                    }

                                    items(remotePlayers, key = { "remote_${it.userId}" }) { rp ->
                                        val isRemoteHost = liveRoom != null && rp.userId == liveRoom.hostId
                                        ParticipantRow(
                                            name = rp.displayName,
                                            roleLabel = if (isRemoteHost) "Онлайн • Хост" else "Онлайн-игрок",
                                            weaponLabel = rp.weapon,
                                            accentColor = ArenaSkyCyan,
                                            isBot = false,
                                            removeContentDescription = "Исключить ${rp.displayName}",
                                            onRemove = if (isOnlineLobby && isLocalHost && !isRemoteHost) {
                                                { lobbyViewModel?.kickPlayer(rp.userId) }
                                            } else null
                                        )
                                    }

                                    if (!isOnlineLobby) {
                                        items(botRoster, key = { it.id }) { bot ->
                                            ParticipantRow(
                                                name = bot.name,
                                                roleLabel = if (peacefulMode) "Мирная курица" else "Бот-курица",
                                                weaponLabel = if (peacefulMode) "Без оружия" else bot.weapon.displayName,
                                                accentColor = Color(bot.accentR, bot.accentG, bot.accentB),
                                                isBot = true,
                                                onRemove = {
                                                    botRoster = botRoster.filter { it.id != bot.id }
                                                }
                                            )
                                        }
                                    }
                                }
                            } else if (lobbyViewModel != null) {
                                LobbyChatPanel(
                                    messages = roomMessages,
                                    currentUserId = lobbyViewModel.currentUserId,
                                    chatError = chatError,
                                    onSendMessage = { lobbyViewModel.sendChatMessage(it) },
                                    onDismissError = { lobbyViewModel.clearChatError() },
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                    }

                    // MIDDLE COLUMN (Online Wide Landscape): Semi-Transparent Real-Time Lobby Chat
                    if (showSideBySideChat && lobbyViewModel != null) {
                        LobbyChatPanel(
                            messages = roomMessages,
                            currentUserId = lobbyViewModel.currentUserId,
                            chatError = chatError,
                            onSendMessage = { lobbyViewModel.sendChatMessage(it) },
                            onDismissError = { lobbyViewModel.clearChatError() },
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                        )
                    }

                // RIGHT COLUMN: Bot Controls, Peaceful Toggle & HUGE "ПУСК" Start Button
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GlassPanelFill,
                    border = BorderStroke(1.dp, GlassBorderLight),
                    modifier = Modifier
                        .weight(if (showSideBySideChat) 0.95f else 1f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (isOnlineLobby && liveRoom != null) {
                                // Online humans-only Room Rules Summary + Host Control Panel button
                                Text(
                                    text = "ПАРАМЕТРЫ КОМНАТЫ",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = FeatherWhite
                                )
                                Surface(
                                    color = TacticalNavy.copy(alpha = 0.72f),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, ArenaSkyCyan.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Карта: ${activeOnlineMap.label}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = ArenaSkyCyan
                                        )
                                        Text(
                                            text = "Режим: ${liveRoom.resolvedGameMode().label} • До ${liveRoom.maxPlayers} игроков",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = BeakAmber
                                        )
                                        val ruleSummary = buildList {
                                            add("Без ботов")
                                            if (liveRoom.allowGuestPropSpawn) add("Стройка")
                                            if (liveRoom.allowGuestVehicleSpawn) add("Авто")
                                            if (liveRoom.friendlyFire) add("PvP")
                                            if (liveRoom.infiniteAmmo) add("∞ Патроны")
                                            if (liveRoom.godMode) add("Режим бога")
                                            if (liveRoom.joinLocked) add("Вход закрыт")
                                            if (liveRoom.privateRoom) add("Пароль")
                                        }.joinToString(" • ")
                                        Text(
                                            text = ruleSummary,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedSteel,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (isLocalHost) {
                                    Button(
                                        onClick = { hostControlOpen = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ArenaSkyCyan,
                                            contentColor = TacticalNavy
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("lobby_host_controls_button")
                                    ) {
                                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("УПРАВЛЕНИЕ КОМНАТОЙ", fontWeight = FontWeight.ExtraBold)
                                    }
                                }

                                MapAndSkinQuickBar(
                                    selectedMap = activeOnlineMap,
                                    selectedSkin = settings.selectedSkin,
                                    selectedWeaponSkin = settings.weaponSkin,
                                    onToggleWeaponSkin = {
                                        onUpdateSettings { s ->
                                            s.copy(weaponSkin = s.weaponSkin.next())
                                        }
                                    },
                                    onOpenPicker = { mapSkinPickerOpen = true }
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.lobby_bots_control),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = FeatherWhite
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        onClick = {
                                            if (botRoster.isNotEmpty()) {
                                                botRoster = botRoster.dropLast(1)
                                            }
                                        },
                                        color = CombCrimson.copy(alpha = 0.22f),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.2.dp, CombCrimson),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("lobby_remove_bot_button")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Filled.Remove,
                                                contentDescription = stringResource(R.string.lobby_remove_bot),
                                                tint = FeatherWhite
                                            )
                                        }
                                    }

                                    Surface(
                                        color = TacticalNavy.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "Ботов: ${botRoster.size} / 8",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = BeakAmber
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = {
                                            if (botRoster.size < 8) {
                                                botRoster = botRoster + LobbyBotSlot.createSlot(nextBotIndex++)
                                            }
                                        },
                                        color = NitroEmerald.copy(alpha = 0.22f),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.2.dp, NitroEmerald),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("lobby_add_bot_button")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Filled.Add,
                                                contentDescription = stringResource(R.string.lobby_add_bot),
                                                tint = FeatherWhite
                                            )
                                        }
                                    }
                                }

                                // Peaceful Mode Toggle Card (solo only)
                                Surface(
                                    color = if (peacefulMode) NitroEmerald.copy(alpha = 0.16f) else TacticalNavy.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.2.dp,
                                        if (peacefulMode) NitroEmerald else SteelSurface
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                Icons.Filled.Construction,
                                                contentDescription = null,
                                                tint = if (peacefulMode) NitroEmerald else BeakAmber,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = stringResource(R.string.lobby_peaceful_toggle),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = FeatherWhite,
                                                    fontSize = 12.sp
                                                )
                                                Text(
                                                    text = if (peacefulMode) "Без урона • Свободная стройка и 9 машин"
                                                    else "100 HP + 3с щит • 9 видов машин",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MutedSteel,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = peacefulMode,
                                            onCheckedChange = { peacefulMode = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = TacticalNavy,
                                                checkedTrackColor = NitroEmerald
                                            ),
                                            modifier = Modifier.testTag("lobby_peaceful_switch")
                                        )
                                    }
                                }

                                MapAndSkinQuickBar(
                                    selectedMap = settings.selectedMap,
                                    selectedSkin = settings.selectedSkin,
                                    selectedWeaponSkin = settings.weaponSkin,
                                    onToggleWeaponSkin = {
                                        onUpdateSettings { s ->
                                            s.copy(weaponSkin = s.weaponSkin.next())
                                        }
                                    },
                                    onOpenPicker = { mapSkinPickerOpen = true }
                                )
                            }
                        }

                        // HUGE PROMINENT "ПУСК" START MATCH BUTTON
                        Button(
                            onClick = {
                                if (isOnlineLobby) {
                                    onStartMatch(emptyList(), false)
                                } else {
                                    onStartMatch(botRoster, peacefulMode)
                                }
                            },
                            enabled = !roomClosed,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NitroEmerald,
                                contentColor = TacticalNavy
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("lobby_start_game_button")
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_start_match),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }
                }
                }
            }
        }

        if (mapSkinPickerOpen) {
            MapAndSkinPickerDialog(
                selectedMap = if (isOnlineLobby) activeOnlineMap else settings.selectedMap,
                selectedSkin = settings.selectedSkin,
                onSelectMap = { map ->
                    if (isOnlineLobby && isLocalHost) {
                        lobbyViewModel?.updateActiveRoomSettings(mapOf("mapId" to map.name))
                    } else if (!isOnlineLobby) {
                        onUpdateSettings { it.copy(selectedMap = map) }
                    }
                },
                onSelectSkin = { skin -> onUpdateSettings { it.copy(selectedSkin = skin) } },
                onDismiss = { mapSkinPickerOpen = false }
            )
        }

        if (hostControlOpen && isOnlineLobby && isLocalHost && liveRoom != null && lobbyViewModel != null) {
            HostRoomControlDialog(
                room = liveRoom,
                players = allOnlinePlayers,
                currentUserId = lobbyViewModel.currentUserId,
                onUpdateSettings = { updates -> lobbyViewModel.updateActiveRoomSettings(updates) },
                onUpdatePlayerPrivileges = { targetUid, godMode, canBuild ->
                    lobbyViewModel.updatePlayerPrivileges(targetUid, godMode, canBuild)
                },
                onKickPlayer = { targetUid -> lobbyViewModel.kickPlayer(targetUid) },
                onDeleteRoom = {
                    hostControlOpen = false
                    lobbyViewModel.deleteOrCloseActiveRoom(onClosed = onLeaveLobby)
                },
                onDismiss = { hostControlOpen = false }
            )
        }
    }
}

@Composable
private fun ParticipantRow(
    name: String,
    roleLabel: String,
    weaponLabel: String,
    accentColor: Color,
    isBot: Boolean,
    removeContentDescription: String? = null,
    onRemove: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = TacticalNavy.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.22f))
                    .border(1.5.dp, accentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isBot) Icons.Filled.SmartToy else Icons.Filled.Person,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = FeatherWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$roleLabel • $weaponLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSteel
                )
            }
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isBot) Icons.Filled.Close else Icons.Filled.PersonRemove,
                        contentDescription = removeContentDescription ?: stringResource(R.string.lobby_remove_bot),
                        tint = CombCrimson,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
