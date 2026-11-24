package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class MultiplayerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun sanitizeNickname(raw: String?): String {
        val cleaned = (raw ?: "Курица").trim().take(30)
        return if (cleaned.isEmpty()) "Курица" else cleaned
    }

    private fun generateRoomCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(6) {
            repeat(6) {
                append(alphabet[Random.nextInt(alphabet.length)])
            }
        }
    }

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Profile stream must match signed-in user." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureProfile(
        nickname: String,
        selectedSkin: String = "CLASSIC",
        weaponSkin: String = "CLASSIC"
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        var path = "users/$uid"
        return try {
            val cleanNickname = sanitizeNickname(nickname)
            val safeSkin = sanitizeCharacterSkin(selectedSkin)
            val safeWeaponSkin = sanitizeWeaponSkin(weaponSkin)
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val curKills: Long
            val curWins: Long
            val curMatches: Long
            val curBest: Long
            val resolvedNickname: String
            if (!snap.exists()) {
                resolvedNickname = cleanNickname
                curKills = 0L
                curWins = 0L
                curMatches = 0L
                curBest = 0L
                val payload = mapOf(
                    "userId" to uid,
                    "nickname" to resolvedNickname,
                    "totalKills" to 0L,
                    "totalWins" to 0L,
                    "coins" to 0L,
                    "totalMatches" to 0L,
                    "bestStreak" to 0L,
                    "selectedSkin" to safeSkin,
                    "weaponSkin" to safeWeaponSkin,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                resolvedNickname = sanitizeNickname(snap.getString("nickname") ?: cleanNickname)
                curKills = (snap.getLong("totalKills") ?: 0L).coerceIn(0L, 1_000_000L)
                curWins = (snap.getLong("totalWins") ?: 0L).coerceIn(0L, 1_000_000L)
                curMatches = (snap.getLong("totalMatches") ?: 0L).coerceIn(0L, 1_000_000L)
                curBest = (snap.getLong("bestStreak") ?: 0L).coerceIn(0L, 10_000L)
            }

            path = "leaderboard/$uid"
            val lbRef = db.collection("leaderboard").document(uid)
            val lbSnap = lbRef.get().await()
            if (!lbSnap.exists()) {
                lbRef.set(
                    mapOf(
                        "userId" to uid,
                        "nickname" to resolvedNickname,
                        "kills" to curKills,
                        "wins" to curWins,
                        "matchesPlayed" to curMatches,
                        "bestStreak" to curBest,
                        "visibility" to "public",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private fun sanitizeCharacterSkin(raw: String?): String = when (raw) {
        "CLASSIC", "SPEC_OPS", "GOLDEN_KING", "CYBER_NEON", "SHADOW_NINJA", "ARCTIC_FROST" -> raw
        else -> "CLASSIC"
    }

    private fun sanitizeWeaponSkin(raw: String?): String = when (raw) {
        "CLASSIC", "TACTICAL" -> raw
        else -> "CLASSIC"
    }

    suspend fun updateProfileCustomization(
        nickname: String? = null,
        selectedSkin: String? = null,
        weaponSkin: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        var path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val cleanNick = nickname?.let { sanitizeNickname(it) }
            val safeCharSkin = selectedSkin?.let { sanitizeCharacterSkin(it) }
            val safeWepSkin = weaponSkin?.let { sanitizeWeaponSkin(it) }

            if (!snap.exists()) {
                val resolvedNick = cleanNick ?: sanitizeNickname(auth.currentUser?.displayName)
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "nickname" to resolvedNick,
                        "totalKills" to 0L,
                        "totalWins" to 0L,
                        "coins" to 0L,
                        "totalMatches" to 0L,
                        "bestStreak" to 0L,
                        "selectedSkin" to (safeCharSkin ?: "CLASSIC"),
                        "weaponSkin" to (safeWepSkin ?: "CLASSIC"),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanNick != null) updates["nickname"] = cleanNick
                if (safeCharSkin != null) updates["selectedSkin"] = safeCharSkin
                if (safeWepSkin != null) updates["weaponSkin"] = safeWepSkin
                if (updates.size > 1) {
                    docRef.update(updates).await()
                }
            }

            if (cleanNick != null) {
                path = "leaderboard/$uid"
                val lbRef = db.collection("leaderboard").document(uid)
                val lbSnap = lbRef.get().await()
                if (lbSnap.exists()) {
                    lbRef.update(
                        mapOf(
                            "nickname" to cleanNick,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordMatchStats(killsAdded: Int, streak: Int, won: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        var path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val curKills = snap.getLong("totalKills") ?: 0L
            val curWins = snap.getLong("totalWins") ?: 0L
            val curMatches = snap.getLong("totalMatches") ?: 0L
            val curBest = snap.getLong("bestStreak") ?: 0L
            val nextKills = (curKills + killsAdded.coerceAtLeast(0)).coerceAtMost(1_000_000L)
            val nextWins = (curWins + if (won) 1L else 0L).coerceAtMost(1_000_000L)
            val nextMatches = (curMatches + 1L).coerceAtMost(1_000_000L)
            val nextStreak = maxOf(curBest, streak.toLong().coerceIn(0L, 10_000L))
            val resolvedNickname = sanitizeNickname(snap.getString("nickname") ?: auth.currentUser?.displayName)

            if (!snap.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "nickname" to resolvedNickname,
                        "totalKills" to nextKills,
                        "totalWins" to nextWins,
                        "coins" to 0L,
                        "totalMatches" to nextMatches,
                        "bestStreak" to nextStreak,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "totalKills" to nextKills,
                        "totalWins" to nextWins,
                        "totalMatches" to nextMatches,
                        "bestStreak" to nextStreak,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            path = "leaderboard/$uid"
            val lbRef = db.collection("leaderboard").document(uid)
            val lbSnap = lbRef.get().await()
            if (!lbSnap.exists()) {
                lbRef.set(
                    mapOf(
                        "userId" to uid,
                        "nickname" to resolvedNickname,
                        "kills" to nextKills,
                        "wins" to nextWins,
                        "matchesPlayed" to nextMatches,
                        "bestStreak" to nextStreak,
                        "visibility" to "public",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                lbRef.update(
                    mapOf(
                        "nickname" to resolvedNickname,
                        "kills" to nextKills,
                        "wins" to nextWins,
                        "matchesPlayed" to nextMatches,
                        "bestStreak" to nextStreak,
                        "visibility" to "public",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeGlobalLeaderboard(): Flow<List<LeaderboardEntry>> = flow {
        val uid = requireUserId()
        val path = "leaderboard"
        emitAll(
            db.collection("leaderboard")
                .whereEqualTo("visibility", "public")
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LeaderboardEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<LeaderboardEntry> { it.kills }
                            .thenByDescending { it.wins }
                            .thenByDescending { it.matchesPlayed }
                            .thenBy { it.nickname.lowercase() }
                    )
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun todayDayKey(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.format(Date())
    }

    fun observeDailyMissions(
        userId: String,
        dayKey: String = todayDayKey()
    ): Flow<List<DailyMission>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Daily missions stream must match signed-in user." }
        val path = "users/$uid/daily_missions"
        emitAll(
            db.collection("users").document(uid)
                .collection("daily_missions")
                .whereEqualTo("userId", uid)
                .whereEqualTo("dayKey", dayKey)
                .limit(3)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DailyMission::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.slotIndex }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureDailyMissions(dayKey: String = todayDayKey()): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/daily_missions"
        return try {
            val col = db.collection("users").document(uid).collection("daily_missions")
            val existingSnap = col
                .whereEqualTo("userId", uid)
                .whereEqualTo("dayKey", dayKey)
                .limit(3)
                .get()
                .await()

            val existingMissions = existingSnap.toObjects(
                DailyMission::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            if (existingMissions.size >= 3) {
                return Result.success(Unit)
            }

            val existingSlots = existingMissions.map { it.slotIndex.toInt() }.toSet()
            val usedTypes = existingMissions.map { it.missionType }.toSet()
            val availableTypes = DailyMissionType.entries
                .filter { it.name !in usedTypes }
                .shuffled(Random)
                .iterator()

            val batch = db.batch()
            var writes = 0
            for (slot in 0..2) {
                if (slot in existingSlots || !availableTypes.hasNext()) continue
                val template = availableTypes.next()
                val docId = "${dayKey}_slot_$slot"
                batch.set(
                    col.document(docId),
                    mapOf(
                        "userId" to uid,
                        "dayKey" to dayKey,
                        "slotIndex" to slot.toLong(),
                        "missionType" to template.name,
                        "title" to template.title,
                        "target" to template.target,
                        "progress" to 0L,
                        "rewardCoins" to template.rewardCoins,
                        "status" to "active",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                writes++
            }
            if (writes > 0) {
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordDailyMissionProgress(
        telemetry: MatchMissionTelemetry,
        dayKey: String = todayDayKey()
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/daily_missions"
        return try {
            ensureDailyMissions(dayKey)
            val col = db.collection("users").document(uid).collection("daily_missions")
            val snap = col
                .whereEqualTo("userId", uid)
                .whereEqualTo("dayKey", dayKey)
                .limit(3)
                .get()
                .await()

            val missions = snap.toObjects(
                DailyMission::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val batch = db.batch()
            var updatedCount = 0

            for (mission in missions) {
                if (mission.status != "active" || mission.id.isBlank()) continue
                val delta = when (mission.resolvedType()) {
                    DailyMissionType.KILLS -> telemetry.kills.coerceAtLeast(0).toLong()
                    DailyMissionType.DRIVE_METERS -> telemetry.driveMeters.coerceAtLeast(0).toLong()
                    DailyMissionType.BUILD_PROPS -> telemetry.propsBuilt.coerceAtLeast(0).toLong()
                    DailyMissionType.DESTROY_PROPS -> telemetry.propsDestroyed.coerceAtLeast(0).toLong()
                    DailyMissionType.ROADKILL -> telemetry.vehicleKills.coerceAtLeast(0).toLong()
                    DailyMissionType.PLAY_MATCHES -> if (telemetry.playedMatch) 1L else 0L
                    DailyMissionType.WIN_MATCH -> if (telemetry.wonMatch) 1L else 0L
                    DailyMissionType.EXPLOSIONS -> telemetry.explosions.coerceAtLeast(0).toLong()
                }
                if (delta <= 0L) continue

                val nextProgress = (mission.progress + delta).coerceIn(mission.progress, mission.target)
                val nextStatus = if (nextProgress >= mission.target) "completed" else "active"
                if (nextProgress == mission.progress && nextStatus == mission.status) continue

                batch.update(
                    col.document(mission.id),
                    mapOf(
                        "progress" to nextProgress,
                        "status" to nextStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                updatedCount++
            }

            if (updatedCount > 0) {
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun claimDailyMissionReward(missionId: String): Result<Long> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/daily_missions/$missionId"
        return try {
            require(missionId.isNotBlank()) { "Mission ID must not be blank" }
            val userRef = db.collection("users").document(uid)
            val missionRef = userRef.collection("daily_missions").document(missionId)

            val earnedCoins = db.runTransaction { tx ->
                val missionSnap = tx.get(missionRef)
                if (!missionSnap.exists()) {
                    throw IllegalStateException("Задание не найдено")
                }
                val status = missionSnap.getString("status") ?: "active"
                val progress = missionSnap.getLong("progress") ?: 0L
                val target = missionSnap.getLong("target") ?: 1L
                val reward = (missionSnap.getLong("rewardCoins") ?: 100L).coerceIn(10L, 1000L)

                if (status == "claimed") {
                    throw IllegalStateException("Награда уже получена")
                }
                if (status != "completed" || progress < target) {
                    throw IllegalStateException("Задание еще не выполнено")
                }

                val userSnap = tx.get(userRef)
                val currentCoins = userSnap.getLong("coins") ?: 0L
                val nextCoins = (currentCoins + reward).coerceAtMost(10_000_000L)

                tx.update(
                    missionRef,
                    mapOf(
                        "status" to "claimed",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (userSnap.exists()) {
                    tx.update(
                        userRef,
                        mapOf(
                            "coins" to nextCoins,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    tx.set(
                        userRef,
                        mapOf(
                            "userId" to uid,
                            "nickname" to sanitizeNickname(auth.currentUser?.displayName),
                            "totalKills" to 0L,
                            "totalWins" to 0L,
                            "coins" to nextCoins,
                            "totalMatches" to 0L,
                            "bestStreak" to 0L,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                reward
            }.await()

            Result.success(earnedCoins)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeOpenRooms(): Flow<List<GameRoom>> = flow {
        val uid = requireUserId()
        val path = "rooms"
        emitAll(
            db.collection("rooms")
                .whereEqualTo("status", "open")
                .limit(40)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GameRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<GameRoom> { it.pinned }
                            .thenByDescending { it.createdAt?.seconds ?: 0L }
                    )
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRoom(roomId: String): Flow<GameRoom?> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId"
        emitAll(
            db.collection("rooms").document(roomId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            GameRoom::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createRoom(roomName: String, displayName: String): Result<GameRoom> =
        createRoom(RoomConfig(name = roomName), displayName)

    suspend fun createRoom(config: RoomConfig, displayName: String): Result<GameRoom> {
        var docPath = "rooms"
        return try {
            val uid = requireUserId()
            val cleanName = config.name.trim().ifBlank { "Арена Куриц" }.take(40)
            val safeName = if (cleanName.length < 2) "Арена" else cleanName
            val safeMaxPlayers = config.maxPlayers.coerceIn(2, 8).toLong()
            val safeMapId = when (config.mapId) {
                "MAIN_ARENA", "TURBO_SPEEDWAY", "TACTICAL_COMPOUND" -> config.mapId
                else -> "MAIN_ARENA"
            }
            val safePassword = if (config.privateRoom) config.password.trim().take(24) else ""
            val safePrivate = config.privateRoom && safePassword.isNotEmpty()
            val code = generateRoomCode()
            val docRef = db.collection("rooms").document()
            docPath = docRef.path

            val roomPayload = mapOf(
                "roomCode" to code,
                "name" to safeName,
                "hostId" to uid,
                "status" to "open",
                "maxPlayers" to safeMaxPlayers,
                "mapId" to safeMapId,
                "gameMode" to config.gameMode.name,
                "privateRoom" to safePrivate,
                "password" to safePassword,
                "pinned" to config.pinned,
                "joinLocked" to config.joinLocked,
                "allowGuestPropSpawn" to config.allowGuestPropSpawn,
                "allowGuestVehicleSpawn" to config.allowGuestVehicleSpawn,
                "friendlyFire" to config.friendlyFire,
                "infiniteAmmo" to config.infiniteAmmo,
                "godMode" to config.godMode,
                "memberUids" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(roomPayload).await()

            upsertPlayerState(
                roomId = docRef.id,
                displayName = sanitizeNickname(displayName),
                x = 0.0,
                y = 2.0,
                z = 18.0,
                yaw = 0.0,
                pitch = 0.0,
                hp = 100,
                kills = 0,
                weapon = "RIFLE",
                drivingVehicle = false,
                shooting = false,
                memberUids = listOf(uid),
                isInitialCreate = true
            )

            Result.success(
                GameRoom(
                    id = docRef.id,
                    roomCode = code,
                    name = safeName,
                    hostId = uid,
                    status = "open",
                    maxPlayers = safeMaxPlayers,
                    mapId = safeMapId,
                    gameMode = config.gameMode.name,
                    privateRoom = safePrivate,
                    password = safePassword,
                    pinned = config.pinned,
                    joinLocked = config.joinLocked,
                    allowGuestPropSpawn = config.allowGuestPropSpawn,
                    allowGuestVehicleSpawn = config.allowGuestVehicleSpawn,
                    friendlyFire = config.friendlyFire,
                    infiniteAmmo = config.infiniteAmmo,
                    godMode = config.godMode,
                    memberUids = listOf(uid)
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun joinRoomById(
        roomId: String,
        displayName: String,
        password: String = ""
    ): Result<GameRoom> {
        val docPath = "rooms/$roomId"
        return try {
            val uid = requireUserId()
            require(roomId.isNotBlank()) { "ID комнаты пуст" }
            val docRef = db.collection("rooms").document(roomId)
            val snap = docRef.get().await()
            val room = snap.toObject(
                GameRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Комната не найдена"))

            if (room.status != "open") {
                return Result.failure(IllegalStateException("Комната уже закрыта"))
            }

            val updatedMembers = if (uid in room.memberUids) {
                room.memberUids
            } else {
                if (room.joinLocked) {
                    return Result.failure(IllegalStateException("Вход в комнату временно закрыт хостом"))
                }
                if (room.privateRoom && room.password.isNotEmpty() && room.password != password.trim()) {
                    return Result.failure(IllegalArgumentException("Неверный пароль комнаты"))
                }
                if (room.memberUids.size >= room.maxPlayers.toInt()) {
                    return Result.failure(IllegalStateException("Комната заполнена"))
                }
                val next = room.memberUids + uid
                docRef.update(
                    mapOf(
                        "memberUids" to next,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                next
            }

            upsertPlayerState(
                roomId = room.id,
                displayName = sanitizeNickname(displayName),
                x = (Random.nextFloat() * 10f - 5f).toDouble(),
                y = 2.0,
                z = (18f + Random.nextFloat() * 4f).toDouble(),
                yaw = 0.0,
                pitch = 0.0,
                hp = 100,
                kills = 0,
                weapon = "RIFLE",
                drivingVehicle = false,
                shooting = false,
                memberUids = updatedMembers,
                isInitialCreate = true
            )

            Result.success(room.copy(memberUids = updatedMembers))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun joinRoomByCode(
        roomCode: String,
        displayName: String,
        password: String = ""
    ): Result<GameRoom> {
        val normalized = roomCode.trim().uppercase()
        if (normalized.length !in 4..12) {
            return Result.failure(IllegalArgumentException("Код комнаты должен содержать 4–12 символов"))
        }
        return try {
            requireUserId()
            val querySnap = db.collection("rooms")
                .whereEqualTo("status", "open")
                .whereEqualTo("roomCode", normalized)
                .limit(1)
                .get()
                .await()
            val doc = querySnap.documents.firstOrNull()
                ?: return Result.failure(IllegalStateException("Открытая комната с кодом $normalized не найдена"))
            joinRoomById(doc.id, displayName, password)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "rooms")
            Result.failure(e)
        }
    }

    suspend fun updateRoomSettings(
        roomId: String,
        updates: Map<String, Any>
    ): Result<Unit> {
        val path = "rooms/$roomId"
        return try {
            val uid = requireUserId()
            val roomRef = db.collection("rooms").document(roomId)
            val snap = roomRef.get().await()
            val room = snap.toObject(
                GameRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Комната не найдена"))
            if (room.hostId != uid) {
                return Result.failure(IllegalStateException("Только хост может менять настройки комнаты"))
            }
            val safeUpdates = HashMap<String, Any>()
            updates.forEach { (key, value) ->
                when (key) {
                    "name" -> {
                        val clean = value.toString().trim().take(40)
                        if (clean.length >= 2) safeUpdates[key] = clean
                    }
                    "maxPlayers" -> {
                        val raw = (value as? Number)?.toLong() ?: room.maxPlayers
                        safeUpdates[key] = raw.coerceIn(room.memberUids.size.coerceAtLeast(2).toLong(), 8L)
                    }
                    "mapId" -> {
                        val map = value.toString()
                        if (map in listOf("MAIN_ARENA", "TURBO_SPEEDWAY", "TACTICAL_COMPOUND")) {
                            safeUpdates[key] = map
                        }
                    }
                    "gameMode" -> {
                        val mode = value.toString()
                        if (mode in listOf("SANDBOX", "PVP", "RACING")) {
                            safeUpdates[key] = mode
                        }
                    }
                    "password" -> {
                        safeUpdates[key] = value.toString().trim().take(24)
                    }
                    "privateRoom", "pinned", "joinLocked",
                    "allowGuestPropSpawn", "allowGuestVehicleSpawn",
                    "friendlyFire", "infiniteAmmo", "godMode" -> {
                        if (value is Boolean) safeUpdates[key] = value
                    }
                }
            }
            if (safeUpdates.isEmpty()) return Result.success(Unit)
            safeUpdates["updatedAt"] = FieldValue.serverTimestamp()
            roomRef.update(safeUpdates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun kickPlayerFromRoom(
        roomId: String,
        targetUid: String
    ): Result<Unit> {
        val path = "rooms/$roomId"
        return try {
            val uid = requireUserId()
            val roomRef = db.collection("rooms").document(roomId)
            val snap = roomRef.get().await()
            val room = snap.toObject(
                GameRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Комната не найдена"))
            if (room.hostId != uid) {
                return Result.failure(IllegalStateException("Только хост может исключать игроков"))
            }
            if (targetUid == room.hostId) {
                return Result.failure(IllegalArgumentException("Нельзя исключить хоста"))
            }
            val remaining = room.memberUids.filter { it != targetUid }
            if (remaining.size != room.memberUids.size && remaining.isNotEmpty()) {
                roomRef.update(
                    mapOf(
                        "memberUids" to remaining,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            try {
                roomRef.collection("players").document(targetUid).delete().await()
            } catch (_: Exception) {
                // Ignore if player doc was already cleaned up
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateRoomPlayerPrivileges(
        roomId: String,
        targetUid: String,
        godMode: Boolean? = null,
        canBuild: Boolean? = null
    ): Result<Unit> {
        val path = "rooms/$roomId/players/$targetUid"
        return try {
            val uid = requireUserId()
            if (godMode == null && canBuild == null) return Result.success(Unit)
            val roomRef = db.collection("rooms").document(roomId)
            val snap = roomRef.get().await()
            val room = snap.toObject(
                GameRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Комната не найдена"))
            if (room.hostId != uid) {
                return Result.failure(IllegalStateException("Только создатель комнаты может менять права игроков"))
            }
            val payload = LinkedHashMap<String, Any>(4)
            if (godMode != null) payload["godMode"] = godMode
            if (canBuild != null) payload["canBuild"] = canBuild
            payload["updatedAt"] = FieldValue.serverTimestamp()
            roomRef.collection("players").document(targetUid).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteOrCloseRoom(roomId: String): Result<Unit> {
        val path = "rooms/$roomId"
        return try {
            val uid = requireUserId()
            val roomRef = db.collection("rooms").document(roomId)
            val snap = roomRef.get().await()
            val room = snap.toObject(
                GameRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.success(Unit)
            if (room.hostId != uid) {
                return Result.failure(IllegalStateException("Только хост может удалить комнату"))
            }
            try {
                roomRef.collection("players").document(uid).delete().await()
            } catch (_: Exception) {
            }
            if (room.status != "closed") {
                try {
                    roomRef.update(
                        mapOf(
                            "status" to "closed",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } catch (_: Exception) {
                }
            }
            roomRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveRoom(roomId: String): Result<Unit> {
        val path = "rooms/$roomId"
        return try {
            val uid = requireUserId()
            val roomRef = db.collection("rooms").document(roomId)
            val snap = roomRef.get().await()
            val room = snap.toObject(
                GameRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            // Remove own player doc first
            try {
                roomRef.collection("players").document(uid).delete().await()
            } catch (_: Exception) {
                // Ignore if already removed
            }
            if (room != null && room.status != "closed") {
                if (room.hostId == uid) {
                    roomRef.update(
                        mapOf(
                            "status" to "closed",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else if (uid in room.memberUids) {
                    val remaining = room.memberUids.filter { it != uid }
                    if (remaining.isNotEmpty()) {
                        roomRef.update(
                            mapOf(
                                "memberUids" to remaining,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeRoomPlayers(roomId: String): Flow<List<RoomPlayer>> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId/players"
        emitAll(
            db.collection("rooms").document(roomId)
                .collection("players")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        RoomPlayer::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRoomProps(roomId: String): Flow<List<RoomProp>> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId/props"
        emitAll(
            db.collection("rooms").document(roomId)
                .collection("props")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        RoomProp::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertPlayerState(
        roomId: String,
        displayName: String,
        x: Double,
        y: Double,
        z: Double,
        yaw: Double,
        pitch: Double,
        hp: Int,
        kills: Int,
        weapon: String,
        drivingVehicle: Boolean,
        shooting: Boolean,
        memberUids: List<String>,
        isInitialCreate: Boolean = false
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "rooms/$roomId/players/$uid"
        return try {
            val safeMembers = if (uid in memberUids) memberUids.take(8) else (memberUids + uid).take(8)
            val safeWeapon = when (weapon) {
                "PISTOL", "RIFLE", "RPG" -> weapon
                else -> "RIFLE"
            }
            val docRef = db.collection("rooms").document(roomId)
                .collection("players").document(uid)

            if (isInitialCreate) {
                val snap = docRef.get().await()
                if (!snap.exists()) {
                    docRef.set(
                        mapOf(
                            "userId" to uid,
                            "displayName" to sanitizeNickname(displayName),
                            "x" to x.coerceIn(-195.0, 195.0),
                            "y" to y.coerceIn(-10.0, 195.0),
                            "z" to z.coerceIn(-195.0, 195.0),
                            "yaw" to yaw.coerceIn(-3600.0, 3600.0),
                            "pitch" to pitch.coerceIn(-89.0, 89.0),
                            "hp" to hp.coerceIn(0, 100).toLong(),
                            "kills" to kills.coerceIn(0, 9999).toLong(),
                            "weapon" to safeWeapon,
                            "drivingVehicle" to drivingVehicle,
                            "shooting" to shooting,
                            "memberUids" to safeMembers,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    return Result.success(Unit)
                }
            }

            docRef.update(
                mapOf(
                    "displayName" to sanitizeNickname(displayName),
                    "x" to x.coerceIn(-195.0, 195.0),
                    "y" to y.coerceIn(-10.0, 195.0),
                    "z" to z.coerceIn(-195.0, 195.0),
                    "yaw" to yaw.coerceIn(-3600.0, 3600.0),
                    "pitch" to pitch.coerceIn(-89.0, 89.0),
                    "hp" to hp.coerceIn(0, 100).toLong(),
                    "kills" to kills.coerceIn(0, 9999).toLong(),
                    "weapon" to safeWeapon,
                    "drivingVehicle" to drivingVehicle,
                    "shooting" to shooting,
                    "memberUids" to safeMembers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun damageRemotePlayer(roomId: String, targetPlayerId: String, newHp: Int): Result<Unit> {
        val path = "rooms/$roomId/players/$targetPlayerId"
        return try {
            requireUserId()
            db.collection("rooms").document(roomId)
                .collection("players").document(targetPlayerId)
                .update(
                    mapOf(
                        "hp" to newHp.coerceIn(0, 100).toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun spawnRoomProp(
        roomId: String,
        propType: String,
        x: Double,
        y: Double,
        z: Double,
        yaw: Double,
        hp: Int,
        memberUids: List<String>
    ): Result<String> {
        var path = "rooms/$roomId/props"
        return try {
            val uid = requireUserId()
            val safeMembers = if (uid in memberUids) memberUids.take(8) else (memberUids + uid).take(8)
            val docRef = db.collection("rooms").document(roomId)
                .collection("props").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "ownerId" to uid,
                    "propType" to propType,
                    "x" to x.coerceIn(-195.0, 195.0),
                    "y" to y.coerceIn(-10.0, 195.0),
                    "z" to z.coerceIn(-195.0, 195.0),
                    "yaw" to yaw.coerceIn(-3600.0, 3600.0),
                    "hp" to hp.coerceIn(0, 500).toLong(),
                    "destroyed" to false,
                    "memberUids" to safeMembers,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun syncPropsMemberUids(roomId: String, propIds: List<String>, memberUids: List<String>): Result<Unit> {
        val path = "rooms/$roomId/props"
        return try {
            requireUserId()
            if (propIds.isEmpty() || memberUids.isEmpty()) return Result.success(Unit)
            val batch = db.batch()
            val collection = db.collection("rooms").document(roomId).collection("props")
            propIds.take(25).forEach { propId ->
                batch.update(
                    collection.document(propId),
                    mapOf(
                        "memberUids" to memberUids.take(8),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePropDestroyed(roomId: String, propId: String): Result<Unit> {
        val path = "rooms/$roomId/props/$propId"
        return try {
            requireUserId()
            db.collection("rooms").document(roomId)
                .collection("props").document(propId)
                .update(
                    mapOf(
                        "hp" to 0L,
                        "destroyed" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeRoomMessages(roomId: String): Flow<List<RoomMessage>> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId/messages"
        emitAll(
            db.collection("rooms").document(roomId)
                .collection("messages")
                .whereArrayContains("memberUids", uid)
                .limit(60)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        RoomMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<RoomMessage>(
                            { it.createdAt?.seconds ?: Long.MAX_VALUE },
                            { it.createdAt?.nanoseconds ?: Int.MAX_VALUE },
                            { it.id }
                        )
                    )
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendRoomMessage(
        roomId: String,
        senderName: String,
        text: String,
        memberUids: List<String>
    ): Result<Unit> {
        val trimmed = text.trim().take(200)
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Сообщение не может быть пустым"))
        }
        var path = "rooms/$roomId/messages"
        return try {
            val uid = requireUserId()
            val safeMembers = if (uid in memberUids) memberUids.take(8) else (memberUids + uid).take(8)
            val docRef = db.collection("rooms").document(roomId)
                .collection("messages").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "senderId" to uid,
                    "senderName" to sanitizeNickname(senderName),
                    "text" to trimmed,
                    "memberUids" to safeMembers,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun syncMessagesMemberUids(
        roomId: String,
        messageIds: List<String>,
        memberUids: List<String>
    ): Result<Unit> {
        val path = "rooms/$roomId/messages"
        return try {
            requireUserId()
            if (messageIds.isEmpty() || memberUids.isEmpty()) return Result.success(Unit)
            val batch = db.batch()
            val collection = db.collection("rooms").document(roomId).collection("messages")
            messageIds.takeLast(40).forEach { msgId ->
                batch.update(
                    collection.document(msgId),
                    mapOf(
                        "memberUids" to memberUids.take(8),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}

