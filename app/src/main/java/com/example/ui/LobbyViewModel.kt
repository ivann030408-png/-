package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DailyMission
import com.example.data.GameRoom
import com.example.data.LeaderboardEntry
import com.example.data.MatchMissionTelemetry
import com.example.data.MultiplayerRepository
import com.example.data.RoomConfig
import com.example.data.RoomMessage
import com.example.data.RoomPlayer
import com.example.data.RoomProp
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.engine.LocalNetworkState
import com.example.engine.PropType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LobbyViewModel(
    private val repository: MultiplayerRepository,
    val currentUserId: String,
    val initialDisplayName: String
) : ViewModel() {

    val profileState: StateFlow<UiState<UserProfile?>> = repository.observeProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing user profile", root)
            emit(UiState.Error(root.message ?: "Ошибка загрузки профиля"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val openRoomsState: StateFlow<UiState<List<GameRoom>>> = repository.observeOpenRooms()
        .map<List<GameRoom>, UiState<List<GameRoom>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing open rooms", root)
            emit(UiState.Error(root.message ?: "Ошибка загрузки списка комнат"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val leaderboardState: StateFlow<UiState<List<LeaderboardEntry>>> = repository.observeGlobalLeaderboard()
        .map<List<LeaderboardEntry>, UiState<List<LeaderboardEntry>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing global leaderboard", root)
            emit(UiState.Error(root.message ?: "Ошибка загрузки рейтинга"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private val activeDayKey: String = repository.todayDayKey()

    val dailyMissionsState: StateFlow<UiState<List<DailyMission>>> = repository
        .observeDailyMissions(currentUserId, activeDayKey)
        .map<List<DailyMission>, UiState<List<DailyMission>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing daily missions", root)
            emit(UiState.Error(root.message ?: "Ошибка загрузки заданий"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private val _claimingMissionId = MutableStateFlow<String?>(null)
    val claimingMissionId: StateFlow<String?> = _claimingMissionId.asStateFlow()

    private val _missionFeedback = MutableStateFlow<String?>(null)
    val missionFeedback: StateFlow<String?> = _missionFeedback.asStateFlow()

    private val _activeRoom = MutableStateFlow<GameRoom?>(null)
    val activeRoom: StateFlow<GameRoom?> = _activeRoom.asStateFlow()

    private val _roomPlayers = MutableStateFlow<List<RoomPlayer>>(emptyList())
    val roomPlayers: StateFlow<List<RoomPlayer>> = _roomPlayers.asStateFlow()

    private val _roomProps = MutableStateFlow<List<RoomProp>>(emptyList())
    val roomProps: StateFlow<List<RoomProp>> = _roomProps.asStateFlow()

    private val _roomMessages = MutableStateFlow<List<RoomMessage>>(emptyList())
    val roomMessages: StateFlow<List<RoomMessage>> = _roomMessages.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _actionLoading = MutableStateFlow(false)
    val actionLoading: StateFlow<Boolean> = _actionLoading.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _roomClosedNotice = MutableStateFlow<String?>(null)
    val roomClosedNotice: StateFlow<String?> = _roomClosedNotice.asStateFlow()

    private var roomObserverJob: Job? = null
    private var playersObserverJob: Job? = null
    private var propsObserverJob: Job? = null
    private var messagesObserverJob: Job? = null
    private var lastSyncedMemberUids: List<String> = emptyList()

    init {
        viewModelScope.launch {
            repository.ensureProfile(initialDisplayName)
            repository.ensureDailyMissions(activeDayKey)
        }
    }

    private val _profileSaveFeedback = MutableStateFlow<String?>(null)
    val profileSaveFeedback: StateFlow<String?> = _profileSaveFeedback.asStateFlow()

    fun clearMissionFeedback() {
        _missionFeedback.value = null
    }

    fun clearProfileSaveFeedback() {
        _profileSaveFeedback.value = null
    }

    fun syncSkinsToProfile(selectedSkin: String, weaponSkin: String) {
        viewModelScope.launch {
            repository.updateProfileCustomization(
                selectedSkin = selectedSkin,
                weaponSkin = weaponSkin
            )
        }
    }

    fun saveProfileCustomization(
        nickname: String,
        selectedSkin: String,
        weaponSkin: String
    ) {
        viewModelScope.launch {
            val res = repository.updateProfileCustomization(
                nickname = nickname,
                selectedSkin = selectedSkin,
                weaponSkin = weaponSkin
            )
            res.onSuccess {
                _profileSaveFeedback.value = "Профиль и скины сохранены в облаке"
            }.onFailure { err ->
                _profileSaveFeedback.value = err.localizedMessage ?: "Не удалось сохранить профиль"
            }
        }
    }

    fun claimMissionReward(missionId: String) {
        if (_claimingMissionId.value != null) return
        viewModelScope.launch {
            _claimingMissionId.value = missionId
            val res = repository.claimDailyMissionReward(missionId)
            _claimingMissionId.value = null
            res.onSuccess { earned ->
                _missionFeedback.value = "+$earned монет получено!"
            }.onFailure { err ->
                _missionFeedback.value = err.localizedMessage ?: "Не удалось забрать награду"
            }
        }
    }

    fun clearError() {
        _actionError.value = null
    }

    fun clearChatError() {
        _chatError.value = null
    }

    fun clearRoomClosedNotice() {
        _roomClosedNotice.value = null
    }

    fun createAndEnterRoom(roomName: String, onJoined: (GameRoom) -> Unit) {
        createAndEnterRoom(RoomConfig(name = roomName), onJoined)
    }

    fun createAndEnterRoom(config: RoomConfig, onJoined: (GameRoom) -> Unit) {
        if (_actionLoading.value) return
        viewModelScope.launch {
            _actionLoading.value = true
            _actionError.value = null
            _roomClosedNotice.value = null
            val res = repository.createRoom(config, currentNickname())
            _actionLoading.value = false
            res.onSuccess { room ->
                attachRoomStreams(room)
                onJoined(room)
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Не удалось создать комнату"
            }
        }
    }

    fun joinRoomById(roomId: String, password: String = "", onJoined: (GameRoom) -> Unit) {
        if (_actionLoading.value) return
        viewModelScope.launch {
            _actionLoading.value = true
            _actionError.value = null
            _roomClosedNotice.value = null
            val res = repository.joinRoomById(roomId, currentNickname(), password)
            _actionLoading.value = false
            res.onSuccess { room ->
                attachRoomStreams(room)
                onJoined(room)
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Не удалось войти в комнату"
            }
        }
    }

    fun joinRoomByCode(code: String, password: String = "", onJoined: (GameRoom) -> Unit) {
        if (_actionLoading.value) return
        viewModelScope.launch {
            _actionLoading.value = true
            _actionError.value = null
            _roomClosedNotice.value = null
            val res = repository.joinRoomByCode(code, currentNickname(), password)
            _actionLoading.value = false
            res.onSuccess { room ->
                attachRoomStreams(room)
                onJoined(room)
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Комната не найдена"
            }
        }
    }

    fun updateActiveRoomSettings(updates: Map<String, Any>) {
        val room = _activeRoom.value ?: return
        if (room.hostId != currentUserId) return
        viewModelScope.launch {
            _actionError.value = null
            val res = repository.updateRoomSettings(room.id, updates)
            res.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Не удалось обновить настройки комнаты"
            }
        }
    }

    fun kickPlayer(targetUid: String) {
        val room = _activeRoom.value ?: return
        if (room.hostId != currentUserId || targetUid == currentUserId) return
        viewModelScope.launch {
            _actionError.value = null
            val res = repository.kickPlayerFromRoom(room.id, targetUid)
            res.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Не удалось исключить игрока"
            }
        }
    }

    fun updatePlayerPrivileges(
        targetUid: String,
        godMode: Boolean? = null,
        canBuild: Boolean? = null
    ) {
        val room = _activeRoom.value ?: return
        if (room.hostId != currentUserId || targetUid.isBlank()) return
        viewModelScope.launch {
            _actionError.value = null
            val res = repository.updateRoomPlayerPrivileges(
                roomId = room.id,
                targetUid = targetUid,
                godMode = godMode,
                canBuild = canBuild
            )
            res.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Не удалось обновить права игрока"
            }
        }
    }

    fun deleteOrCloseActiveRoom(onClosed: () -> Unit = {}) {
        val room = clearLocalRoomState()
        if (room == null) {
            onClosed()
            return
        }
        viewModelScope.launch {
            repository.deleteOrCloseRoom(room.id)
            onClosed()
        }
    }

    private fun attachRoomStreams(room: GameRoom) {
        detachRoomStreams()
        _activeRoom.value = room
        _roomClosedNotice.value = null
        lastSyncedMemberUids = room.memberUids

        roomObserverJob = viewModelScope.launch {
            repository.observeRoom(room.id)
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    Log.w(TAG, "Room stream warning", fsError ?: error)
                }
                .collect { updated ->
                    if (updated != null) {
                        val wasMember = currentUserId in lastSyncedMemberUids
                        val stillMember = currentUserId in updated.memberUids
                        val membersChanged = updated.memberUids != lastSyncedMemberUids
                        _activeRoom.value = updated

                        if (updated.status == "closed") {
                            if (updated.hostId != currentUserId) {
                                _roomClosedNotice.value = "Хост закрыл комнату"
                            }
                        } else if (wasMember && !stillMember && updated.hostId != currentUserId) {
                            _roomClosedNotice.value = "Вы были исключены хостом из комнаты"
                        }

                        if (membersChanged) {
                            lastSyncedMemberUids = updated.memberUids
                            // Sync new memberUids onto existing props & messages so newcomers see them immediately
                            val existingPropIds = _roomProps.value.map { it.id }
                            val existingMessageIds = _roomMessages.value.map { it.id }
                            if (updated.hostId == currentUserId && updated.status == "open") {
                                if (existingPropIds.isNotEmpty()) {
                                    launch {
                                        repository.syncPropsMemberUids(updated.id, existingPropIds, updated.memberUids)
                                    }
                                }
                                if (existingMessageIds.isNotEmpty()) {
                                    launch {
                                        repository.syncMessagesMemberUids(updated.id, existingMessageIds, updated.memberUids)
                                    }
                                }
                            }
                        }
                    } else {
                        val prev = _activeRoom.value
                        if (prev != null && prev.hostId != currentUserId) {
                            _roomClosedNotice.value = "Хост удалил комнату"
                        }
                        _activeRoom.value = prev?.copy(status = "closed")
                    }
                }
        }

        playersObserverJob = viewModelScope.launch {
            repository.observeRoomPlayers(room.id)
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    Log.w(TAG, "Players stream warning", fsError ?: error)
                }
                .collect { players ->
                    _roomPlayers.value = players
                }
        }

        propsObserverJob = viewModelScope.launch {
            repository.observeRoomProps(room.id)
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    Log.w(TAG, "Props stream warning", fsError ?: error)
                }
                .collect { props ->
                    _roomProps.value = props
                }
        }

        messagesObserverJob = viewModelScope.launch {
            repository.observeRoomMessages(room.id)
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    Log.w(TAG, "Messages stream warning", fsError ?: error)
                }
                .collect { messages ->
                    _roomMessages.value = messages
                }
        }
    }

    fun sendChatMessage(text: String) {
        val room = _activeRoom.value ?: return
        val cleaned = text.trim().take(200)
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            _chatError.value = null
            val res = repository.sendRoomMessage(
                roomId = room.id,
                senderName = currentNickname(),
                text = cleaned,
                memberUids = room.memberUids
            )
            res.onFailure { err ->
                _chatError.value = err.localizedMessage ?: "Не удалось отправить сообщение"
            }
        }
    }

    fun pushLocalPlayerState(state: LocalNetworkState) {
        val room = _activeRoom.value ?: return
        if (room.status != "open") return
        viewModelScope.launch {
            repository.upsertPlayerState(
                roomId = room.id,
                displayName = currentNickname(),
                x = state.x,
                y = state.y,
                z = state.z,
                yaw = state.yaw,
                pitch = state.pitch,
                hp = state.hp,
                kills = state.kills,
                weapon = state.weapon,
                drivingVehicle = state.drivingVehicle,
                shooting = state.shooting,
                memberUids = room.memberUids,
                isInitialCreate = false
            )
        }
    }

    fun damageRemotePlayer(targetUid: String, newHp: Int) {
        val room = _activeRoom.value ?: return
        viewModelScope.launch {
            repository.damageRemotePlayer(room.id, targetUid, newHp)
        }
    }

    fun spawnSyncedProp(type: PropType, x: Float, y: Float, z: Float, yaw: Float) {
        val room = _activeRoom.value ?: return
        viewModelScope.launch {
            repository.spawnRoomProp(
                roomId = room.id,
                propType = type.firestoreType,
                x = x.toDouble(),
                y = y.toDouble(),
                z = z.toDouble(),
                yaw = yaw.toDouble(),
                hp = type.maxHp,
                memberUids = room.memberUids
            )
        }
    }

    fun markSyncedPropDestroyed(propId: String) {
        val room = _activeRoom.value ?: return
        viewModelScope.launch {
            repository.updatePropDestroyed(room.id, propId)
        }
    }

    fun saveMatchSummary(kills: Int, bestStreak: Int, won: Boolean) {
        viewModelScope.launch {
            repository.recordMatchStats(kills, bestStreak, won)
        }
    }

    fun recordMissionProgress(telemetry: MatchMissionTelemetry) {
        viewModelScope.launch {
            repository.recordDailyMissionProgress(telemetry, activeDayKey)
        }
    }

    suspend fun leaveActiveRoomSuspend() {
        val room = clearLocalRoomState()
        if (room != null) {
            repository.leaveRoom(room.id)
        }
    }

    fun leaveActiveRoom() {
        val room = clearLocalRoomState() ?: return
        viewModelScope.launch {
            repository.leaveRoom(room.id)
        }
    }

    private fun clearLocalRoomState(): GameRoom? {
        val room = _activeRoom.value ?: return null
        detachRoomStreams()
        _activeRoom.value = null
        _roomPlayers.value = emptyList()
        _roomProps.value = emptyList()
        _roomMessages.value = emptyList()
        _chatError.value = null
        return room
    }

    private fun detachRoomStreams() {
        roomObserverJob?.cancel()
        playersObserverJob?.cancel()
        propsObserverJob?.cancel()
        messagesObserverJob?.cancel()
        roomObserverJob = null
        playersObserverJob = null
        propsObserverJob = null
        messagesObserverJob = null
    }

    private fun currentNickname(): String {
        val fromProfile = (profileState.value as? UiState.Success)?.data?.nickname
        return fromProfile?.takeIf { it.isNotBlank() } ?: initialDisplayName
    }

    override fun onCleared() {
        super.onCleared()
        detachRoomStreams()
    }

    private companion object {
        const val TAG = "LobbyVM"
    }
}
