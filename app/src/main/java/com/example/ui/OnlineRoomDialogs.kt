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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.GameRoom
import com.example.data.OnlineGameMode
import com.example.data.RoomConfig
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
 * Extended Online Room Creation Dialog (humans-only, 3 maps, 3 modes, password/pin/lock, rules).
 */
@Composable
fun CreateOnlineRoomDialog(
    initialMap: GameMap = GameMap.MAIN_ARENA,
    loading: Boolean = false,
    onCreate: (RoomConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("Арена Куриц") }
    var selectedMap by remember { mutableStateOf(initialMap) }
    var selectedMode by remember { mutableStateOf(OnlineGameMode.SANDBOX) }
    var maxPlayers by remember { mutableIntStateOf(6) }
    var privateRoom by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(false) }
    var joinLocked by remember { mutableStateOf(false) }
    var allowGuestPropSpawn by remember { mutableStateOf(true) }
    var allowGuestVehicleSpawn by remember { mutableStateOf(true) }
    var friendlyFire by remember { mutableStateOf(true) }
    var infiniteAmmo by remember { mutableStateOf(false) }
    var godMode by remember { mutableStateOf(false) }

    val canCreate = !loading &&
        name.trim().length >= 2 &&
        (!privateRoom || password.trim().length >= 2)

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
            border = BorderStroke(1.6.dp, BeakAmber.copy(alpha = 0.72f)),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 780.dp)
                .testTag("create_online_room_dialog")
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
                        Icon(Icons.Filled.Public, contentDescription = null, tint = BeakAmber, modifier = Modifier.size(22.dp))
                        Column {
                            Text(
                                text = "СОЗДАТЬ ОНЛАЙН-КОМНАТУ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = FeatherWhite
                            )
                            Text(
                                text = "Только реальные игроки • боты в онлайне отключены",
                                style = MaterialTheme.typography.labelSmall,
                                color = NitroEmerald
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SteelSurface)
                            .testTag("create_room_dialog_close")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = FeatherWhite)
                    }
                }

                // Body
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left column: Name, Map, Mode, Max Players, Privacy
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { if (it.length <= 32) name = it },
                            label = { Text("Название комнаты") },
                            singleLine = true,
                            colors = hostTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_room_name_input")
                        )

                        Text(
                            text = "КАРТА",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArenaSkyCyan
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            GameMap.entries.forEach { map ->
                                val selected = map == selectedMap
                                Surface(
                                    onClick = { selectedMap = map },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) ArenaSkyCyan.copy(alpha = 0.24f) else TacticalNavy.copy(alpha = 0.65f),
                                    border = BorderStroke(if (selected) 1.6.dp else 1.dp, if (selected) ArenaSkyCyan else SteelSurface),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("create_room_map_${map.name.lowercase()}")
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

                        Text(
                            text = "РЕЖИМ",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = BeakAmber
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OnlineGameMode.entries.forEach { mode ->
                                val selected = mode == selectedMode
                                Surface(
                                    onClick = { selectedMode = mode },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) BeakAmber.copy(alpha = 0.24f) else TacticalNavy.copy(alpha = 0.65f),
                                    border = BorderStroke(if (selected) 1.6.dp else 1.dp, if (selected) BeakAmber else SteelSurface),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("create_room_mode_${mode.name.lowercase()}")
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

                        // Max players stepper
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Group, contentDescription = null, tint = NitroEmerald)
                            Text(
                                text = "Макс. игроков:",
                                style = MaterialTheme.typography.labelLarge,
                                color = FeatherWhite,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                onClick = { if (maxPlayers > 2) maxPlayers-- },
                                shape = RoundedCornerShape(10.dp),
                                color = TacticalNavy,
                                border = BorderStroke(1.dp, SteelSurface),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("create_room_players_minus")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Меньше игроков", tint = FeatherWhite)
                                }
                            }
                            Text(
                                text = "$maxPlayers",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = BeakAmber
                            )
                            Surface(
                                onClick = { if (maxPlayers < 8) maxPlayers++ },
                                shape = RoundedCornerShape(10.dp),
                                color = TacticalNavy,
                                border = BorderStroke(1.dp, SteelSurface),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("create_room_players_plus")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Add, contentDescription = "Больше игроков", tint = FeatherWhite)
                                }
                            }
                        }

                        HostToggleRow(
                            title = "Приватная с паролем",
                            subtitle = "Вход только по паролю (от 2 символов)",
                            checked = privateRoom,
                            accent = ArenaSkyCyan,
                            testTag = "create_room_private_toggle",
                            onCheckedChange = { privateRoom = it }
                        )
                        if (privateRoom) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { if (it.length <= 24) password = it },
                                label = { Text("Пароль комнаты") },
                                singleLine = true,
                                colors = hostTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_room_password_input")
                            )
                        }
                    }

                    // Right column: Pin/Lock + Gameplay Rules + Create Button
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "НАСТРОЙКИ И ПРАВИЛА",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = NitroEmerald
                        )

                        HostToggleRow(
                            title = "Закрепить комнату",
                            subtitle = "Отображать вверху списка",
                            checked = pinned,
                            accent = BeakAmber,
                            testTag = "create_room_pinned_toggle",
                            onCheckedChange = { pinned = it }
                        )
                        HostToggleRow(
                            title = "Закрыть вход (Замок)",
                            subtitle = "Временно запретить вход новых игроков",
                            checked = joinLocked,
                            accent = CombCrimson,
                            testTag = "create_room_lock_toggle",
                            onCheckedChange = { joinLocked = it }
                        )
                        HostToggleRow(
                            title = "Стройка для гостей",
                            subtitle = "Гости могут ставить пропсы",
                            checked = allowGuestPropSpawn,
                            accent = NitroEmerald,
                            testTag = "create_room_props_toggle",
                            onCheckedChange = { allowGuestPropSpawn = it }
                        )
                        HostToggleRow(
                            title = "Спавн машин для гостей",
                            subtitle = "Гости могут вызывать машины",
                            checked = allowGuestVehicleSpawn,
                            accent = NitroEmerald,
                            testTag = "create_room_vehicles_toggle",
                            onCheckedChange = { allowGuestVehicleSpawn = it }
                        )
                        HostToggleRow(
                            title = "Урон по игрокам (PvP)",
                            subtitle = "Перестрелка между участниками",
                            checked = friendlyFire,
                            accent = CombCrimson,
                            testTag = "create_room_pvp_toggle",
                            onCheckedChange = { friendlyFire = it }
                        )
                        HostToggleRow(
                            title = "Бесконечные патроны",
                            subtitle = "Без перезарядки",
                            checked = infiniteAmmo,
                            accent = BeakAmber,
                            testTag = "create_room_ammo_toggle",
                            onCheckedChange = { infiniteAmmo = it }
                        )
                        HostToggleRow(
                            title = "Режим бога",
                            subtitle = "Неуязвимость для всех",
                            checked = godMode,
                            accent = ArenaSkyCyan,
                            testTag = "create_room_god_toggle",
                            onCheckedChange = { godMode = it }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                onCreate(
                                    RoomConfig(
                                        name = name.trim(),
                                        mapId = selectedMap.name,
                                        gameMode = selectedMode,
                                        maxPlayers = maxPlayers,
                                        privateRoom = privateRoom,
                                        password = if (privateRoom) password.trim() else "",
                                        pinned = pinned,
                                        joinLocked = joinLocked,
                                        allowGuestPropSpawn = allowGuestPropSpawn,
                                        allowGuestVehicleSpawn = allowGuestVehicleSpawn,
                                        friendlyFire = friendlyFire,
                                        infiniteAmmo = infiniteAmmo,
                                        godMode = godMode
                                    )
                                )
                            },
                            enabled = canCreate,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BeakAmber,
                                contentColor = TacticalNavy
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("confirm_create_room_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("СОЗДАТЬ КОМНАТУ", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact modal prompting for a password when joining a private room.
 */
@Composable
fun RoomPasswordDialog(
    room: GameRoom,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalNavy.copy(alpha = 0.84f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BunkerSlate,
            border = BorderStroke(1.6.dp, ArenaSkyCyan.copy(alpha = 0.72f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .testTag("room_password_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = ArenaSkyCyan)
                    Column {
                        Text(
                            text = "Приватная комната",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = FeatherWhite
                        )
                        Text(
                            text = "${room.name} • Код ${room.roomCode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedSteel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 24) input = it },
                    label = { Text("Введите пароль") },
                    singleLine = true,
                    colors = hostTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("room_password_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, SteelSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Отмена", color = FeatherWhite)
                    }
                    Button(
                        onClick = { onConfirm(input.trim()) },
                        enabled = input.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NitroEmerald,
                            contentColor = TacticalNavy
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("room_password_submit")
                    ) {
                        Text("ВОЙТИ", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
