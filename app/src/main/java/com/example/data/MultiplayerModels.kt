package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val nickname: String = "Боец",
    val totalKills: Long = 0L,
    val totalWins: Long = 0L,
    val coins: Long = 0L,
    val totalMatches: Long = 0L,
    val bestStreak: Long = 0L,
    val selectedSkin: String = "CLASSIC",
    val weaponSkin: String = "CLASSIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class OnlineGameMode(val label: String, val subtitle: String) {
    SANDBOX("Песочница", "Стройка, машины и полная свобода без ботов"),
    PVP("Перестрелка", "Бой только между игроками — без ботов"),
    RACING("Гонки и трюки", "Заезды на машинах без урона от оружия");

    companion object {
        fun fromRaw(raw: String?): OnlineGameMode =
            entries.firstOrNull { it.name == raw } ?: SANDBOX
    }
}

data class RoomConfig(
    val name: String = "Арена Куриц",
    val mapId: String = "MAIN_ARENA",
    val gameMode: OnlineGameMode = OnlineGameMode.SANDBOX,
    val maxPlayers: Int = 6,
    val privateRoom: Boolean = false,
    val password: String = "",
    val pinned: Boolean = false,
    val joinLocked: Boolean = false,
    val allowGuestPropSpawn: Boolean = true,
    val allowGuestVehicleSpawn: Boolean = true,
    val friendlyFire: Boolean = true,
    val infiniteAmmo: Boolean = false,
    val godMode: Boolean = false
)

data class OnlineRoomRules(
    val mapId: String = "MAIN_ARENA",
    val gameMode: OnlineGameMode = OnlineGameMode.SANDBOX,
    val allowGuestPropSpawn: Boolean = true,
    val allowGuestVehicleSpawn: Boolean = true,
    val friendlyFire: Boolean = true,
    val infiniteAmmo: Boolean = false,
    val godMode: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class GameRoom(
    @DocumentId val id: String = "",
    val roomCode: String = "",
    val name: String = "",
    val hostId: String = "",
    val status: String = "open",
    val maxPlayers: Long = 6L,
    val mapId: String = "MAIN_ARENA",
    val gameMode: String = OnlineGameMode.SANDBOX.name,
    val privateRoom: Boolean = false,
    val password: String = "",
    val pinned: Boolean = false,
    val joinLocked: Boolean = false,
    val allowGuestPropSpawn: Boolean = true,
    val allowGuestVehicleSpawn: Boolean = true,
    val friendlyFire: Boolean = true,
    val infiniteAmmo: Boolean = false,
    val godMode: Boolean = false,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedGameMode(): OnlineGameMode = OnlineGameMode.fromRaw(gameMode)

    fun toRoomRules(): OnlineRoomRules = OnlineRoomRules(
        mapId = mapId.ifBlank { "MAIN_ARENA" },
        gameMode = resolvedGameMode(),
        allowGuestPropSpawn = allowGuestPropSpawn,
        allowGuestVehicleSpawn = allowGuestVehicleSpawn,
        friendlyFire = friendlyFire,
        infiniteAmmo = infiniteAmmo,
        godMode = godMode
    )
}

@Keep
@IgnoreExtraProperties
data class RoomPlayer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Курица",
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val yaw: Double = 0.0,
    val pitch: Double = 0.0,
    val hp: Long = 100L,
    val kills: Long = 0L,
    val weapon: String = "RIFLE",
    val drivingVehicle: Boolean = false,
    val shooting: Boolean = false,
    val godMode: Boolean = false,
    val canBuild: Boolean? = null,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class RoomProp(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val propType: String = "CRATE",
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val yaw: Double = 0.0,
    val hp: Long = 100L,
    val destroyed: Boolean = false,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class RoomMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "Курица",
    val text: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LeaderboardEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val nickname: String = "Боец",
    val kills: Long = 0L,
    val wins: Long = 0L,
    val matchesPlayed: Long = 0L,
    val bestStreak: Long = 0L,
    val visibility: String = "public",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class DailyMissionType(
    val title: String,
    val target: Long,
    val rewardCoins: Long,
    val unitSuffix: String = ""
) {
    KILLS("Устрани 5 врагов", 5L, 150L),
    DRIVE_METERS("Проедь 500 м на машине", 500L, 120L, " м"),
    BUILD_PROPS("Построй 6 объектов", 6L, 100L),
    DESTROY_PROPS("Разрушь 5 ящиков или бочек", 5L, 110L),
    ROADKILL("Сбей 2 врагов машиной", 2L, 180L),
    PLAY_MATCHES("Сыграй 2 матча", 2L, 130L),
    WIN_MATCH("Выиграй 1 боевой матч", 1L, 200L),
    EXPLOSIONS("Устрой 4 взрыва", 4L, 140L);

    companion object {
        fun fromRaw(raw: String): DailyMissionType =
            entries.firstOrNull { it.name == raw } ?: KILLS
    }
}

@Keep
@IgnoreExtraProperties
data class DailyMission(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dayKey: String = "",
    val slotIndex: Long = 0L,
    val missionType: String = DailyMissionType.KILLS.name,
    val title: String = "",
    val target: Long = 1L,
    val progress: Long = 0L,
    val rewardCoins: Long = 100L,
    val status: String = "active",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedType(): DailyMissionType = DailyMissionType.fromRaw(missionType)
}

data class MatchMissionTelemetry(
    val kills: Int = 0,
    val driveMeters: Int = 0,
    val propsBuilt: Int = 0,
    val propsDestroyed: Int = 0,
    val vehicleKills: Int = 0,
    val explosions: Int = 0,
    val playedMatch: Boolean = false,
    val wonMatch: Boolean = false
)

