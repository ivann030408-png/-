package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameRoom
import com.example.data.OnlineGameMode
import com.example.data.RoomPlayer
import com.example.engine.GameMap
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
 * Live Host Control Panel used both in the Online Waiting Lobby and mid-match in GameScreen.
 * Allows the room creator to edit map, mode, access, gameplay rules, kick players, or delete the room.
 */
@Composable
fun HostRoomControlDialog(
    room: GameRoom,
    players: List<RoomPlayer>,
    currentUserId: String,
    onUpdateSettings: (Map<String, Any>) -> Unit,
    onUpdatePlayerPrivileges: (targetUid: String, godMode: Boolean?, canBuild: Boolean?) -> Unit = { _, _, _ -> },
    onKickPlayer: (String) -> Unit,
    onDeleteRoom: () -> Unit,
    onDismiss: () -> Unit
) {
    var nameDraft by remember(room.id, room.name) { mutableStateOf(room.name) }
    var passwordDraft by remember(room.id, room.password) { mutableStateOf(room.password) }
    var confirmDelete by remember { mutableStateOf(false) }

    val activeMap = GameMap.fromNameSafe(room.mapId)
    val activeMode = room.resolvedGameMode()
    val kickablePlayers = remember(players, currentUserId) {
        players.filter { it.userId.isNotBlank() && it.userId != currentUserId }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalNavy.copy(alpha = 0.86f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BunkerSlate,
            border = BorderStroke(1.6.dp, ArenaSkyCyan.copy(alpha = 0.72f)),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 780.dp)
                .testTag("host_room_control_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalNavy.copy(alpha = 0.78f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.AdminPanelSettings,
                            contentDescription = null,
                            tint = ArenaSkyCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "УПРАВЛЕНИЕ КОМНАТОЙ (ХОСТ)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = FeatherWhite
                            )
                            Text(
                                text = "Код: ${room.roomCode} • ${room.memberUids.size}/${room.maxPlayers} игроков • без ботов",
                                style = MaterialTheme.typography.labelSmall,
                                color = ArenaSkyCyan
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SteelSurface)
                            .testTag("host_control_close_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = FeatherWhite)
                    }
                }

                // Body (2-column landscape layout)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // LEFT COLUMN: Room Info, Map, Mode, Access & Password
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Room Name live rename
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = nameDraft,
                                onValueChange = { if (it.length <= 32) nameDraft = it },
                                label = { Text("Название комнаты") },
                                singleLine = true,
                                colors = hostTextFieldColors(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("host_room_name_input")
                            )
                            IconButton(
                                onClick = {
                                    val trimmed = nameDraft.trim()
                                    if (trimmed.length >= 2 && trimmed != room.name) {
                                        onUpdateSettings(mapOf("name" to trimmed))
                                    }
                                },
                                enabled = nameDraft.trim().length >= 2 && nameDraft.trim() != room.name,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NitroEmerald.copy(alpha = 0.22f))
                                    .testTag("host_save_name_button")
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = "Сохранить название", tint = NitroEmerald)
                            }
                        }

                        // Map Selector (3 Maps)
                        Text(
                            text = "КАРТА (СМЕНА НА ЛЕТУ)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = BeakAmber
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            GameMap.entries.forEach { map ->
                                val selected = map == activeMap
                                Surface(
                                    onClick = {
                                        if (!selected) onUpdateSettings(mapOf("mapId" to map.name))
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) ArenaSkyCyan.copy(alpha = 0.24f) else TacticalNavy.copy(alpha = 0.65f),
                                    border = BorderStroke(if (selected) 1.6.dp else 1.dp, if (selected) ArenaSkyCyan else SteelSurface),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("host_map_${map.name.lowercase()}")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = map.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) ArenaSkyCyan else FeatherWhite,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Mode Selector
                        Text(
                            text = "РЕЖИМ ИГРЫ",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = BeakAmber
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OnlineGameMode.entries.forEach { mode ->
                                val selected = mode == activeMode
                                Surface(
                                    onClick = {
                                        if (!selected) onUpdateSettings(mapOf("gameMode" to mode.name))
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) BeakAmber.copy(alpha = 0.24f) else TacticalNavy.copy(alpha = 0.65f),
                                    border = BorderStroke(if (selected) 1.6.dp else 1.dp, if (selected) BeakAmber else SteelSurface),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("host_mode_${mode.name.lowercase()}")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) BeakAmber else FeatherWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Max Players stepper
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Макс. игроков:",
                                style = MaterialTheme.typography.labelLarge,
                                color = FeatherWhite,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                onClick = {
                                    val next = (room.maxPlayers - 1L).coerceAtLeast(room.memberUids.size.toLong().coerceAtLeast(2L))
                                    if (next != room.maxPlayers) onUpdateSettings(mapOf("maxPlayers" to next))
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = TacticalNavy,
                                border = BorderStroke(1.dp, SteelSurface),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("host_max_players_minus")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Уменьшить лимит", tint = FeatherWhite)
                                }
                            }
                            Text(
                                text = "${room.maxPlayers}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = BeakAmber
                            )
                            Surface(
                                onClick = {
                                    val next = (room.maxPlayers + 1L).coerceAtMost(8L)
                                    if (next != room.maxPlayers) onUpdateSettings(mapOf("maxPlayers" to next))
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = TacticalNavy,
                                border = BorderStroke(1.dp, SteelSurface),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("host_max_players_plus")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Add, contentDescription = "Увеличить лимит", tint = FeatherWhite)
                                }
                            }
                        }

                        // Access Toggles: Pin, Lock Entry, Private Password
                        HostToggleRow(
                            title = "Закрепить комнату в топе",
                            subtitle = "Показывать выше остальных в списке",
                            checked = room.pinned,
                            accent = BeakAmber,
                            testTag = "host_toggle_pinned",
                            onCheckedChange = { onUpdateSettings(mapOf("pinned" to it)) }
                        )
                        HostToggleRow(
                            title = "Закрыть вход (Замок)",
                            subtitle = "Новые игроки не смогут зайти",
                            checked = room.joinLocked,
                            accent = CombCrimson,
                            testTag = "host_toggle_join_locked",
                            onCheckedChange = { onUpdateSettings(mapOf("joinLocked" to it)) }
                        )
                        HostToggleRow(
                            title = "Приватная с паролем",
                            subtitle = "Запрашивать пароль при входе",
                            checked = room.privateRoom,
                            accent = ArenaSkyCyan,
                            testTag = "host_toggle_private",
                            onCheckedChange = { checked ->
                                onUpdateSettings(
                                    mapOf(
                                        "privateRoom" to checked,
                                        "password" to if (checked) passwordDraft.trim() else ""
                                    )
                                )
                            }
                        )
                        if (room.privateRoom) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = passwordDraft,
                                    onValueChange = { if (it.length <= 24) passwordDraft = it },
                                    label = { Text("Пароль комнаты") },
                                    singleLine = true,
                                    colors = hostTextFieldColors(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("host_password_input")
                                )
                                IconButton(
                                    onClick = {
                                        onUpdateSettings(
                                            mapOf(
                                                "privateRoom" to true,
                                                "password" to passwordDraft.trim()
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ArenaSkyCyan.copy(alpha = 0.22f))
                                        .testTag("host_save_password_button")
                                ) {
                                    Icon(Icons.Filled.Lock, contentDescription = "Сохранить пароль", tint = ArenaSkyCyan)
                                }
                            }
                        }
                    }

                    // RIGHT COLUMN: Gameplay Rules, Kick Players, Delete Room
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ПРАВИЛА МАТЧА",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = NitroEmerald
                        )

                        HostToggleRow(
                            title = "Стройка для гостей",
                            subtitle = "Гости могут ставить пропсы и блоки",
                            checked = room.allowGuestPropSpawn,
                            accent = NitroEmerald,
                            testTag = "host_toggle_guest_props",
                            onCheckedChange = { onUpdateSettings(mapOf("allowGuestPropSpawn" to it)) }
                        )
                        HostToggleRow(
                            title = "Спавн машин для гостей",
                            subtitle = "Гости могут вызывать транспорт",
                            checked = room.allowGuestVehicleSpawn,
                            accent = NitroEmerald,
                            testTag = "host_toggle_guest_vehicles",
                            onCheckedChange = { onUpdateSettings(mapOf("allowGuestVehicleSpawn" to it)) }
                        )
                        HostToggleRow(
                            title = "Урон по игрокам (PvP)",
                            subtitle = "Выключи для мирной совместной игры",
                            checked = room.friendlyFire,
                            accent = CombCrimson,
                            testTag = "host_toggle_friendly_fire",
                            onCheckedChange = { onUpdateSettings(mapOf("friendlyFire" to it)) }
                        )
                        HostToggleRow(
                            title = "Бесконечные патроны",
                            subtitle = "Стрельба без перезарядки",
                            checked = room.infiniteAmmo,
                            accent = BeakAmber,
                            testTag = "host_toggle_infinite_ammo",
                            onCheckedChange = { onUpdateSettings(mapOf("infiniteAmmo" to it)) }
                        )
                        HostToggleRow(
                            title = "Режим бога (Без урона)",
                            subtitle = "Неуязвимость для всех участников",
                            checked = room.godMode,
                            accent = ArenaSkyCyan,
                            testTag = "host_toggle_god_mode",
                            onCheckedChange = { onUpdateSettings(mapOf("godMode" to it)) }
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ИГРОКИ В КОМНАТЕ (${kickablePlayers.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = FeatherWhite
                        )

                        if (kickablePlayers.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = TacticalNavy.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Других игроков пока нет",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedSteel,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        } else {
                            val defaultGuestBuild = room.allowGuestPropSpawn || room.allowGuestVehicleSpawn
                            kickablePlayers.forEach { player ->
                                val effectiveCanBuild = player.canBuild ?: defaultGuestBuild
                                val hasGod = player.godMode
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = TacticalNavy.copy(alpha = 0.72f),
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            hasGod -> ArenaSkyCyan.copy(alpha = 0.75f)
                                            !effectiveCanBuild -> CombCrimson.copy(alpha = 0.55f)
                                            else -> SteelSurface
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = player.displayName,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = FeatherWhite,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val statusParts = buildList {
                                                    add("HP ${player.hp}")
                                                    add("Киллы ${player.kills}")
                                                    if (hasGod) add("БОГ")
                                                    if (!effectiveCanBuild) add("БЕЗ СТРОЙКИ")
                                                }
                                                Text(
                                                    text = statusParts.joinToString(" • "),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (hasGod) ArenaSkyCyan else MutedSteel,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            IconButton(
                                                onClick = { onKickPlayer(player.userId) },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("host_kick_${player.userId}")
                                            ) {
                                                Icon(
                                                    Icons.Filled.PersonRemove,
                                                    contentDescription = "Исключить ${player.displayName}",
                                                    tint = CombCrimson
                                                )
                                            }
                                        }

                                        // Per-player live privilege toggles: God Mode & Building/Spawning
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                onClick = {
                                                    onUpdatePlayerPrivileges(player.userId, !hasGod, null)
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (hasGod) ArenaSkyCyan.copy(alpha = 0.24f) else BunkerSlate.copy(alpha = 0.85f),
                                                border = BorderStroke(
                                                    width = if (hasGod) 1.4.dp else 1.dp,
                                                    color = if (hasGod) ArenaSkyCyan else SteelSurface
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("host_player_god_${player.userId}")
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Shield,
                                                        contentDescription = "Режим бога для ${player.displayName}",
                                                        tint = if (hasGod) ArenaSkyCyan else MutedSteel,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(
                                                        text = if (hasGod) "Бог: ВКЛ" else "Режим бога",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (hasGod) ArenaSkyCyan else FeatherWhite,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            Surface(
                                                onClick = {
                                                    onUpdatePlayerPrivileges(player.userId, null, !effectiveCanBuild)
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (effectiveCanBuild) NitroEmerald.copy(alpha = 0.22f) else CombCrimson.copy(alpha = 0.16f),
                                                border = BorderStroke(
                                                    width = 1.2.dp,
                                                    color = if (effectiveCanBuild) NitroEmerald.copy(alpha = 0.8f) else CombCrimson.copy(alpha = 0.7f)
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("host_player_build_${player.userId}")
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Build,
                                                        contentDescription = "Строительство для ${player.displayName}",
                                                        tint = if (effectiveCanBuild) NitroEmerald else CombCrimson,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(
                                                        text = if (effectiveCanBuild) "Стройка: ВКЛ" else "Стройка: ВЫКЛ",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (effectiveCanBuild) NitroEmerald else CombCrimson,
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

                        Spacer(modifier = Modifier.height(6.dp))

                        // Delete / Close Room with inline confirmation
                        if (!confirmDelete) {
                            OutlinedButton(
                                onClick = { confirmDelete = true },
                                border = BorderStroke(1.4.dp, CombCrimson),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("host_delete_room_button")
                            ) {
                                Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = CombCrimson)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Закрыть и удалить комнату", color = CombCrimson, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { confirmDelete = false },
                                    border = BorderStroke(1.dp, SteelSurface),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("Отмена", color = FeatherWhite)
                                }
                                Button(
                                    onClick = onDeleteRoom,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CombCrimson,
                                        contentColor = FeatherWhite
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(48.dp)
                                        .testTag("host_confirm_delete_room_button")
                                ) {
                                    Icon(Icons.Filled.DeleteForever, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("УДАЛИТЬ", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HostToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (checked) accent.copy(alpha = 0.14f) else TacticalNavy.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, if (checked) accent.copy(alpha = 0.65f) else SteelSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = FeatherWhite,
                    fontSize = 12.sp
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSteel,
                    fontSize = 10.sp
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TacticalNavy,
                    checkedTrackColor = accent
                ),
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@Composable
internal fun hostTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ArenaSkyCyan,
    unfocusedBorderColor = SteelSurface,
    focusedTextColor = FeatherWhite,
    unfocusedTextColor = FeatherWhite,
    focusedLabelColor = ArenaSkyCyan,
    unfocusedLabelColor = MutedSteel,
    cursorColor = ArenaSkyCyan
)
