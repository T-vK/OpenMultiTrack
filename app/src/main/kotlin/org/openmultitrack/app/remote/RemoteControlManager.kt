package org.openmultitrack.app.remote

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.openmultitrack.app.audio.LiveWaveformSnapshot
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.data.MixerDeviceStore
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.app.service.MultiMixerSessionManager
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.remote.RemoteConnectionState
import org.openmultitrack.domain.remote.RemotePairedHost
import org.openmultitrack.domain.remote.RemotePairing
import org.openmultitrack.domain.remote.RemoteProtocol
import org.openmultitrack.domain.remote.RemoteRole
import org.openmultitrack.remote.RemoteClient
import org.openmultitrack.remote.RemoteDiscoveredHost
import org.openmultitrack.remote.RemoteDiscovery
import org.openmultitrack.remote.RemoteHostServer
import org.openmultitrack.remote.RemoteJsonCodec
import org.openmultitrack.remote.RemoteMirrorSnapshot
import org.openmultitrack.sessionio.wav.SessionWaveformOverview

data class RemoteControlUiState(
    val role: RemoteRole = RemoteRole.OFF,
    val connectionState: RemoteConnectionState = RemoteConnectionState.DISCONNECTED,
    val hostName: String? = null,
    val localHostIp: String? = null,
    val connectedHost: String? = null,
    val discoveredHosts: List<RemoteDiscoveredHost> = emptyList(),
    val pairedHosts: List<RemotePairedHost> = emptyList(),
    val hostDeviceId: String? = null,
    val pairingPin: String? = null,
    val pairingUri: String? = null,
    val errorMessage: String? = null,
    val mixers: List<MixerProfile> = emptyList(),
    val activeMixerId: String? = null,
    val sessionByMixer: Map<String, MixerSessionUiState> = emptyMap(),
    val uiSettings: RemoteUiSettings? = null,
    val connectedClientCount: Int = 0,
)

class RemoteControlManager(
    private val appContext: Context,
    private val settings: AppSettingsStore,
    private val mixerStore: MixerDeviceStore,
    private val getManager: () -> MultiMixerSessionManager?,
    private val promoteForeground: (String) -> Boolean,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(RemoteControlUiState())
    val state: StateFlow<RemoteControlUiState> = _state.asStateFlow()

    private var hostServer: RemoteHostServer? = null
    private var announcer: RemoteDiscovery.HostAnnouncer? = null
    private var client: RemoteClient? = null
    private var syncJob: Job? = null
    private var lastHostSnapshot: RemoteMirrorSnapshot? = null
    private val liveWaveformGen = mutableMapOf<String, MutableMap<Int, Int>>()
    private val liveWaveformPrevTails = mutableMapOf<String, MutableMap<Int, ByteArray>>()
    private var clientMirror: RemoteMirrorSnapshot? = null
    private val clientLivePeaks = mutableMapOf<String, MutableMap<Int, LiveWaveformSnapshot>>()
    private val clientSoundcheckPeaks = mutableMapOf<String, MutableMap<Int, FloatArray>>()
    private val pendingSoundcheckChannelCount = mutableMapOf<String, Int>()
    private var lastSoundcheckWaveformRequestKey: String? = null
    private var lastSoundcheckLoadingByMixer = mutableMapOf<String, Boolean>()
    private var snapshotWaitJob: Job? = null
    private var lastConnectHostId: String? = null
    private var lastConnectPort: Int = RemoteProtocol.HTTP_PORT
    private var reconnectJob: Job? = null
    @Volatile
    private var userInitiatedDisconnect = false
    @Volatile
    private var skipRememberedHostOnce = false
    @Volatile
    private var clientSocketOpen = false

    fun applyRole(role: RemoteRole, autoConnect: Boolean = true) {
        if (_state.value.role == role) return
        stopAll()
        settings.remoteRole = role
        _state.update {
            it.copy(
                role = role,
                connectionState = RemoteConnectionState.DISCONNECTED,
                errorMessage = null,
                discoveredHosts = emptyList(),
            )
        }
        when (role) {
            RemoteRole.OFF -> Unit
            RemoteRole.HOST -> startHost()
            RemoteRole.CLIENT -> {
                refreshPairingState()
                if (autoConnect) discoverHosts()
            }
        }
    }

    fun enterRemoteClientMode() {
        if (_state.value.role == RemoteRole.CLIENT && isRemoteClientConnected()) return
        userInitiatedDisconnect = false
        settings.remoteRole = RemoteRole.CLIENT
        if (_state.value.role != RemoteRole.CLIENT) {
            applyRole(RemoteRole.CLIENT)
        } else {
            refreshPairingState()
            discoverHosts()
        }
    }

    fun discoverHosts() {
        if (_state.value.role != RemoteRole.CLIENT) return
        if (isRemoteClientConnected()) return
        reconnectJob?.cancel()
        scope.launch {
            _state.update {
                it.copy(
                    connectionState = RemoteConnectionState.DISCOVERING,
                    errorMessage = null,
                    pairedHosts = settings.listPairedRemoteHosts(),
                )
            }
            val paired = settings.listPairedRemoteHosts()
            val pairedIds = paired.map { it.hostId }.toSet()
            val remembered = paired.firstOrNull { !it.lastHost.isNullOrBlank() }
            if (remembered != null && !skipRememberedHostOnce) {
                OmtLog.i(
                    "Remote",
                    "Connecting to remembered host ${remembered.displayName} at ${remembered.lastHost}:${remembered.lastPort ?: RemoteProtocol.HTTP_PORT}",
                )
                connectToAddress(
                    host = remembered.lastHost!!,
                    port = remembered.lastPort ?: RemoteProtocol.HTTP_PORT,
                    name = remembered.displayName,
                    hostId = remembered.hostId,
                    pin = remembered.pin,
                )
                return@launch
            }
            skipRememberedHostOnce = false
            val hosts = RemoteDiscovery.discoverHosts(appContext, pairedHostIds = pairedIds)
                .sortedWith(compareByDescending<RemoteDiscoveredHost> { it.isPaired }.thenBy { it.name })
            OmtLog.i("Remote", "Discovered ${hosts.size} host(s), paired ids=${pairedIds.size}")
            if (isRemoteClientConnected()) return@launch
            val target = hosts.firstOrNull { it.isPaired } ?: hosts.firstOrNull()
            val pin = target?.hostId?.let { settings.pinForPairedHost(it) }
                ?: paired.firstOrNull()?.pin
            _state.update {
                it.copy(
                    discoveredHosts = hosts,
                    pairedHosts = settings.listPairedRemoteHosts(),
                    connectionState = RemoteConnectionState.DISCONNECTED,
                    errorMessage = when {
                        paired.isNotEmpty() && hosts.isEmpty() ->
                            "Host not on LAN — open remote on the host device or scan its QR code"
                        hosts.isEmpty() -> "No hosts found on LAN"
                        target != null && pin.isNullOrBlank() ->
                            "Pair with QR to connect to ${target.name}"
                        else -> null
                    },
                )
            }
            if (target != null && !pin.isNullOrBlank()) {
                OmtLog.i("Remote", "Connecting to ${target.name} at ${target.host}:${target.port} paired=${target.isPaired}")
                connectToHost(target)
            }
        }
    }

    fun connectDirect(
        host: String,
        port: Int = RemoteProtocol.HTTP_PORT,
        name: String = host,
        hostId: String? = null,
        pin: String? = null,
    ) {
        connectToAddress(host, port, name, hostId, pin)
    }

    fun connectToHost(host: RemoteDiscoveredHost) {
        if (_state.value.role != RemoteRole.CLIENT) return
        val pin = host.hostId?.let { settings.pinForPairedHost(it) }
            ?: settings.listPairedRemoteHosts().firstOrNull()?.pin
        if (pin.isNullOrBlank()) {
            _state.update {
                it.copy(
                    errorMessage = "Enter the host pairing PIN to connect to ${host.name}",
                    connectionState = RemoteConnectionState.DISCONNECTED,
                )
            }
            return
        }
        connectToAddress(host.host, host.port, host.name, host.hostId, pin)
    }

    fun connectManual(host: String, pin: String, port: Int = RemoteProtocol.HTTP_PORT) {
        if (_state.value.role != RemoteRole.CLIENT) {
            applyRole(RemoteRole.CLIENT)
        }
        connectToAddress(host.trim(), port, host.trim(), hostId = null, pin = pin.trim())
    }

    fun pairFromQr(uri: String) {
        val payload = RemotePairing.parsePairingUri(uri) ?: run {
            _state.update { it.copy(errorMessage = "Invalid pairing QR code") }
            return
        }
        settings.savePairedRemoteHost(
            RemotePairedHost(
                hostId = payload.hostId,
                displayName = payload.name,
                pin = payload.pin,
                lastHost = payload.host,
                lastPort = payload.port,
            ),
        )
        if (_state.value.role == RemoteRole.HOST) stopHost()
        settings.remoteRole = RemoteRole.CLIENT
        userInitiatedDisconnect = false
        reconnectJob?.cancel()
        refreshPairingState()
        _state.update {
            it.copy(
                role = RemoteRole.CLIENT,
                connectionState = RemoteConnectionState.CONNECTING,
                errorMessage = null,
                discoveredHosts = emptyList(),
            )
        }
        val directHost = payload.host
            ?: settings.listPairedRemoteHosts().firstOrNull { it.hostId == payload.hostId }?.lastHost
        if (!directHost.isNullOrBlank()) {
            connectToAddress(directHost, payload.port, payload.name, payload.hostId, payload.pin)
        } else {
            discoverHosts()
        }
    }

    fun unpairHost(hostId: String) {
        settings.removePairedRemoteHost(hostId)
        refreshPairingState()
        if (lastConnectHostId == hostId && !isRemoteClientConnected()) {
            stopClient()
            _state.update {
                it.copy(
                    connectionState = RemoteConnectionState.DISCONNECTED,
                    errorMessage = null,
                    connectedHost = null,
                )
            }
        }
    }

    fun stopHosting() {
        if (_state.value.role != RemoteRole.HOST) return
        settings.remoteRole = RemoteRole.OFF
        stopHost()
        _state.update {
            it.copy(
                role = RemoteRole.OFF,
                connectionState = RemoteConnectionState.DISCONNECTED,
                connectedClientCount = 0,
                localHostIp = null,
            )
        }
    }

    fun refreshPairingState() {
        val hostId = settings.remoteHostDeviceId
        val pin = settings.remotePairingPin
        val name = Build.MODEL.ifBlank { "OpenMultiTrack" }
        val localIp = RemoteDiscovery.localIpv4(appContext)
        _state.update {
            it.copy(
                hostDeviceId = hostId,
                pairingPin = pin,
                pairingUri = RemotePairing.buildPairingUri(
                    hostId = hostId,
                    pin = pin,
                    name = name,
                    host = localIp,
                ),
                localHostIp = localIp ?: it.localHostIp,
                pairedHosts = settings.listPairedRemoteHosts(),
            )
        }
    }

    private fun connectToAddress(
        host: String,
        port: Int,
        name: String,
        hostId: String?,
        pin: String?,
    ) {
        stopClient()
        lastConnectHostId = hostId
        lastConnectPort = port
        userInitiatedDisconnect = false
        reconnectJob?.cancel()
        _state.update {
            it.copy(
                connectionState = RemoteConnectionState.CONNECTING,
                hostName = name,
                connectedHost = host,
                errorMessage = null,
                sessionByMixer = emptyMap(),
                mixers = emptyList(),
                activeMixerId = null,
                uiSettings = null,
            )
        }
        client = RemoteClient(listener = clientListener).also {
            it.connect(host, port, pin)
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        when (_state.value.role) {
            RemoteRole.CLIENT -> {
                stopClient()
                settings.remoteRole = RemoteRole.OFF
            }
            RemoteRole.HOST -> stopHost()
            RemoteRole.OFF -> Unit
        }
        _state.update {
            it.copy(
                role = if (it.role == RemoteRole.CLIENT) RemoteRole.OFF else it.role,
                connectionState = RemoteConnectionState.DISCONNECTED,
                connectedHost = null,
                sessionByMixer = emptyMap(),
                mixers = emptyList(),
                activeMixerId = null,
                uiSettings = null,
                connectedClientCount = 0,
            )
        }
    }

    fun exitRemoteClientMode() {
        OmtLog.i("Remote", "Exiting remote client mode")
        userInitiatedDisconnect = true
        disconnect()
    }

    fun isRemoteClientConnected(): Boolean =
        _state.value.role == RemoteRole.CLIENT && clientSocketOpen && clientMirror != null

    fun sendCommand(command: String, payload: JSONObject = JSONObject()) {
        if (!isRemoteClientConnected()) {
            OmtLog.w("Remote", "Dropping command $command — client not synced yet")
            return
        }
        OmtLog.i("Remote", "Sending command $command payload=$payload")
        client?.send(RemoteJsonCodec.encodeCommand(command, payload))
    }

    fun requestSoundcheckWaveforms(mixerId: String, sessionDir: String, channelCount: Int, startSec: Float, windowSec: Float) {
        if (!isRemoteClientConnected()) return
        for (ch in 0 until channelCount) {
            client?.send(
                RemoteJsonCodec.encodeWaveformRequest(mixerId, sessionDir, ch, startSec, windowSec),
            )
        }
    }

    fun shutdown() {
        stopAll()
    }

    private fun startHost() {
        val hostName = Build.MODEL.ifBlank { "OpenMultiTrack" }
        val localIp = RemoteDiscovery.localIpv4(appContext)
        val hostId = settings.remoteHostDeviceId
        val pin = settings.remotePairingPin
        settings.remoteAuthToken = pin
        val server = RemoteHostServer(
            authToken = pin,
            hostId = hostId,
            hostName = hostName,
            listener = hostListener,
        )
        runCatching {
            server.start(NanoTimeoutMs, false)
        }.onFailure { e ->
            OmtLog.e("Remote", "Host server start failed", e)
            _state.update {
                it.copy(
                    role = RemoteRole.OFF,
                    connectionState = RemoteConnectionState.ERROR,
                    errorMessage = e.message,
                )
            }
            return
        }
        hostServer = server
        announcer = RemoteDiscovery.HostAnnouncer(appContext, hostName, hostId).also { it.start() }
        _state.update {
            it.copy(
                role = RemoteRole.HOST,
                connectionState = RemoteConnectionState.CONNECTED,
                hostName = hostName,
                localHostIp = localIp,
                hostDeviceId = hostId,
                pairingPin = pin,
                pairingUri = RemotePairing.buildPairingUri(hostId, pin, hostName, host = localIp),
                connectedClientCount = 0,
            )
        }
        startHostSyncLoop()
        OmtLog.i("Remote", "Host listening on $localIp:${RemoteProtocol.HTTP_PORT}")
    }

    private fun startHostSyncLoop() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                val manager = getManager()
                if (manager != null) {
                    val sessions = manager.mixerIds().associateWith { id ->
                        manager.getOrCreate(id).state.value
                    }
                    val snapshot = RemoteSnapshotMapper.buildSnapshot(
                        hostName = _state.value.hostName ?: "OpenMultiTrack",
                        settings = settings,
                        mixers = mixerStore.listMixers(),
                        activeMixerId = manager.activeMixerId.value,
                        sessions = sessions,
                    )
                    if ((hostServer?.clientCount() ?: 0) > 0) {
                        val live = RemoteSnapshotMapper.liveWaveformTails(
                            sessions,
                            liveWaveformGen,
                            liveWaveformPrevTails,
                        )
                        val delta = RemoteSnapshotMapper.buildDelta(lastHostSnapshot, snapshot, live)
                        if (lastHostSnapshot == null) {
                            hostServer?.broadcast(RemoteJsonCodec.encodeSnapshot(snapshot))
                        } else {
                            delta?.let { hostServer?.broadcast(RemoteJsonCodec.encodeDelta(it)) }
                        }
                    }
                    lastHostSnapshot = snapshot
                }
                delay(RemoteProtocol.DELTA_INTERVAL_MS)
            }
        }
    }

    private fun pushFullSnapshot() {
        val manager = getManager() ?: return
        val sessions = manager.mixerIds().associateWith { id -> manager.getOrCreate(id).state.value }
        val snapshot = RemoteSnapshotMapper.buildSnapshot(
            hostName = _state.value.hostName ?: "OpenMultiTrack",
            settings = settings,
            mixers = mixerStore.listMixers(),
            activeMixerId = manager.activeMixerId.value,
            sessions = sessions,
        )
        lastHostSnapshot = snapshot
        hostServer?.broadcast(RemoteJsonCodec.encodeSnapshot(snapshot))
    }

    private val hostListener = object : RemoteHostServer.Listener {
        override fun onClientConnected(sendToClient: (String) -> Unit) {
            val manager = getManager() ?: run {
                OmtLog.e("Remote", "Client connected but session manager unavailable")
                return
            }
            val sessions = manager.mixerIds().associateWith { id -> manager.getOrCreate(id).state.value }
            val snapshot = RemoteSnapshotMapper.buildSnapshot(
                hostName = _state.value.hostName ?: "OpenMultiTrack",
                settings = settings,
                mixers = mixerStore.listMixers(),
                activeMixerId = manager.activeMixerId.value,
                sessions = sessions,
            )
            lastHostSnapshot = snapshot
            val payload = RemoteJsonCodec.encodeSnapshot(snapshot)
            OmtLog.i(
                "Remote",
                "Sending snapshot to client: mixers=${snapshot.mixers.size} sessions=${snapshot.sessions.size}",
            )
            sendToClient(payload)
            updateConnectedClientCount()
        }

        override fun onClientMessage(json: String, sendToClient: (String) -> Unit) {
            val manager = getManager() ?: return
            val (command, payload) = runCatching { RemoteJsonCodec.decodeCommand(json) }.getOrElse {
                sendToClient(RemoteHostServer.encodeAck("unknown", false, it.message))
                return
            }
            if (command == "request_snapshot") {
                val sessions = manager.mixerIds().associateWith { id -> manager.getOrCreate(id).state.value }
                val snapshot = RemoteSnapshotMapper.buildSnapshot(
                    hostName = _state.value.hostName ?: "OpenMultiTrack",
                    settings = settings,
                    mixers = mixerStore.listMixers(),
                    activeMixerId = manager.activeMixerId.value,
                    sessions = sessions,
                )
                lastHostSnapshot = snapshot
                sendToClient(RemoteJsonCodec.encodeSnapshot(snapshot))
                sendToClient(RemoteHostServer.encodeAck(command, true))
                return
            }
            val executor = RemoteCommandExecutor(manager, settings, promoteForeground)
            val result = executor.execute(command, payload)
            result.onSuccess { waveform ->
                waveform?.let {
                    sendToClient(
                        RemoteJsonCodec.encodeWaveformChunk(
                            it.mixerId,
                            it.sessionDir,
                            it.channel,
                            it.startSec,
                            it.peaks,
                        ),
                    )
                }
                sendToClient(RemoteHostServer.encodeAck(command, true))
                pushFullSnapshot()
            }.onFailure { e ->
                OmtLog.w("Remote", "Command failed: $command — ${e.message}")
                sendToClient(RemoteHostServer.encodeAck(command, false, e.message))
            }
        }

        override fun onClientDisconnected() {
            updateConnectedClientCount()
        }
    }

    private fun updateConnectedClientCount() {
        val count = hostServer?.clientCount() ?: 0
        _state.update { it.copy(connectedClientCount = count) }
        OmtLog.i("Remote", "Connected remote clients: $count")
    }

    private val clientListener = object : RemoteClient.Listener {
        override fun onConnected() {
            OmtLog.i("Remote", "WebSocket open, awaiting host snapshot")
            _state.update { it.copy(connectionState = RemoteConnectionState.CONNECTING, errorMessage = null) }
            snapshotWaitJob?.cancel()
            snapshotWaitJob = scope.launch {
                delay(300)
                if (clientMirror == null) {
                    OmtLog.i("Remote", "Requesting snapshot from host")
                    client?.send(RemoteJsonCodec.encodeCommand("request_snapshot"))
                }
                delay(4000)
                if (clientMirror == null) {
                    OmtLog.e("Remote", "No snapshot received — check PIN or host state")
                    failClientConnection("Could not sync with host. Check the PIN and try again.")
                }
            }
        }

        override fun onMessage(json: String) {
            val root = runCatching { JSONObject(json) }.getOrNull() ?: return
            val type = root.optString("type")
            OmtLog.d("Remote", "Client message type=$type bytes=${json.length}")
            when (type) {
                "snapshot" -> {
                    val snapshot = runCatching { RemoteJsonCodec.decodeSnapshot(json) }.getOrElse { e ->
                        OmtLog.e("Remote", "Snapshot decode failed", e)
                        _state.update { it.copy(errorMessage = "Host state sync failed: ${e.message}") }
                        return
                    }
                    applyClientSnapshot(snapshot)
                }
                "delta" -> {
                    val delta = RemoteJsonCodec.decodeDelta(json)
                    val base = clientMirror ?: return
                    clientMirror = RemoteSnapshotMapper.applyDelta(base, delta, clientLivePeaks)
                    checkSoundcheckLoadingTransitions(clientMirror?.sessions.orEmpty())
                    publishClientMirror()
                    maybeRequestSoundcheckWaveforms()
                }
                "command_ack" -> {
                    val ack = JSONObject(json)
                    if (!ack.optBoolean("ok", true)) {
                        val error = ack.optString("error").ifBlank { "Remote command failed" }
                        _state.update { it.copy(errorMessage = error) }
                    }
                }
                "waveform_chunk" -> {
                    val chunk = RemoteJsonCodec.decodeWaveformChunk(json)
                    val key = "${chunk.mixerId}|${chunk.sessionDir}"
                    val channels = clientSoundcheckPeaks.getOrPut(key) { mutableMapOf() }
                    channels[chunk.channel] = chunk.peaks
                    OmtLog.d(
                        "Remote",
                        "Soundcheck chunk mixer=${chunk.mixerId} ch=${chunk.channel} " +
                            "points=${chunk.peaks.size} total=${channels.size}",
                    )
                    mergeSoundcheckWaveforms(chunk.mixerId, chunk.sessionDir)
                }
            }
        }

        override fun onDisconnected(reason: String?) {
            clientSocketOpen = false
            clientMirror = null
            clientLivePeaks.clear()
            if (userInitiatedDisconnect || _state.value.role != RemoteRole.CLIENT) {
                _state.update {
                    it.copy(
                        connectionState = RemoteConnectionState.DISCONNECTED,
                        errorMessage = reason,
                        sessionByMixer = emptyMap(),
                        mixers = emptyList(),
                        activeMixerId = null,
                        uiSettings = null,
                    )
                }
                return
            }
            _state.update {
                it.copy(
                    connectionState = RemoteConnectionState.DISCONNECTED,
                    errorMessage = reason?.takeIf { r -> r.isNotBlank() } ?: "Connection lost",
                    sessionByMixer = emptyMap(),
                    mixers = emptyList(),
                    activeMixerId = null,
                    uiSettings = null,
                )
            }
            scheduleReconnect()
        }

        override fun onFailure(error: String) {
            failClientConnection(error)
        }
    }

    private fun failClientConnection(error: String) {
        snapshotWaitJob?.cancel()
        snapshotWaitJob = null
        clientSocketOpen = false
        stopClient()
        OmtLog.e("Remote", "Client connection failed: $error")
        if (userInitiatedDisconnect || _state.value.role != RemoteRole.CLIENT) return
        val needsRepair = error.contains("unauthorized", ignoreCase = true) ||
            error.contains("policy violation", ignoreCase = true) ||
            error.contains("Could not sync", ignoreCase = true)
        val friendly = when {
            needsRepair -> "Pairing expired — scan the host QR code again or unpair and re-pair"
            error.contains("broken pipe", ignoreCase = true) -> "Connection lost — reconnecting…"
            error.contains("connection reset", ignoreCase = true) -> "Connection lost — reconnecting…"
            else -> error
        }
        _state.update {
            it.copy(
                role = RemoteRole.CLIENT,
                connectionState = RemoteConnectionState.DISCONNECTED,
                errorMessage = friendly,
                sessionByMixer = emptyMap(),
                mixers = emptyList(),
                activeMixerId = null,
                uiSettings = null,
            )
        }
        if (needsRepair) {
            skipRememberedHostOnce = true
            return
        }
        scheduleReconnect()
    }

    private fun scheduleReconnect(delayMs: Long = 2500) {
        if (userInitiatedDisconnect || _state.value.role != RemoteRole.CLIENT) return
        if (isRemoteClientConnected()) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (userInitiatedDisconnect || _state.value.role != RemoteRole.CLIENT) return@launch
            if (!isRemoteClientConnected()) {
                OmtLog.i("Remote", "Auto-reconnecting to paired host")
                discoverHosts()
            }
        }
    }

    private fun applyClientSnapshot(snapshot: RemoteMirrorSnapshot) {
        snapshotWaitJob?.cancel()
        snapshotWaitJob = null
        clientSocketOpen = true
        val hostName = _state.value.hostName ?: snapshot.hostName
        val pin = lastConnectHostId?.let { settings.pinForPairedHost(it) }
            ?: settings.listPairedRemoteHosts().firstOrNull()?.pin
        lastConnectHostId?.let { hostId ->
            val connectedHost = _state.value.connectedHost
            if (!pin.isNullOrBlank() && !connectedHost.isNullOrBlank()) {
                settings.savePairedRemoteHost(
                    RemotePairedHost(
                        hostId = hostId,
                        displayName = hostName,
                        pin = pin,
                        lastHost = connectedHost,
                        lastPort = lastConnectPort,
                    ),
                )
                refreshPairingState()
            }
        }
        val previousDir = clientMirror?.sessions?.get(snapshot.activeMixerId ?: "")?.selectedSoundcheckDir
        clientMirror = snapshot
        val newDir = snapshot.sessions[snapshot.activeMixerId ?: ""]?.selectedSoundcheckDir
        if (previousDir != newDir) {
            lastSoundcheckWaveformRequestKey = null
        }
        checkSoundcheckLoadingTransitions(snapshot.sessions)
        publishClientMirror()
        maybeRequestSoundcheckWaveforms()
    }

    private fun maybeRequestSoundcheckWaveforms() {
        val mirror = clientMirror ?: return
        val mixerId = mirror.activeMixerId ?: return
        val session = mirror.sessions[mixerId] ?: return
        val meta = session.soundcheckWaveformMeta ?: return
        val dir = session.selectedSoundcheckDir ?: return
        val requestKey = listOf(
            mixerId,
            dir,
            session.soundcheckViewStartSec,
            session.soundcheckViewWindowSec,
            meta.channelCount,
        ).joinToString("|")
        if (requestKey == lastSoundcheckWaveformRequestKey) return
        if (meta.loading) {
            OmtLog.d("Remote", "Soundcheck waveforms still loading on host for $mixerId")
            return
        }
        lastSoundcheckWaveformRequestKey = requestKey
        val key = "$mixerId|$dir"
        clientSoundcheckPeaks.remove(key)
        val channelCount = meta.channelCount.coerceAtLeast(session.captureChannelCount)
        pendingSoundcheckChannelCount[key] = channelCount
        requestSoundcheckWaveforms(
            mixerId = mixerId,
            sessionDir = dir,
            channelCount = channelCount,
            startSec = session.soundcheckViewStartSec,
            windowSec = session.soundcheckViewWindowSec,
        )
    }

    private fun mergeSoundcheckWaveforms(mixerId: String, sessionDir: String) {
        val mirror = clientMirror ?: return
        val remote = mirror.sessions[mixerId] ?: return
        val meta = remote.soundcheckWaveformMeta ?: return
        val key = "$mixerId|$sessionDir"
        val peaks = clientSoundcheckPeaks[key] ?: return
        val expected = pendingSoundcheckChannelCount[key]
            ?: meta.channelCount.coerceAtLeast(1)
        if (peaks.size < expected) {
            OmtLog.d("Remote", "Waiting for soundcheck chunks ${peaks.size}/$expected for $key")
            return
        }
        val overview = SessionWaveformOverview(
            peaksByChannel = peaks.mapValues { it.value.copyOf() },
            peaksPerSec = meta.peaksPerSec.toFloat(),
            durationSec = meta.durationSec,
        )
        val updatedRemote = remote.copy(
            soundcheckWaveformMeta = meta.copy(loading = false, progress = 1f),
        )
        clientMirror = mirror.copy(
            sessions = mirror.sessions + (mixerId to updatedRemote),
        )
        pendingSoundcheckChannelCount.remove(key)
        publishClientMirror(overviewOverrides = mapOf(mixerId to overview))
    }

    private fun checkSoundcheckLoadingTransitions(sessions: Map<String, org.openmultitrack.remote.RemoteMixerSnapshot>) {
        sessions.forEach { (mixerId, session) ->
            val meta = session.soundcheckWaveformMeta ?: return@forEach
            val wasLoading = lastSoundcheckLoadingByMixer[mixerId] == true
            lastSoundcheckLoadingByMixer[mixerId] = meta.loading
            if (!wasLoading && meta.loading) {
                session.selectedSoundcheckDir?.let { dir ->
                    val key = "$mixerId|$dir"
                    clientSoundcheckPeaks.remove(key)
                    pendingSoundcheckChannelCount.remove(key)
                }
                lastSoundcheckWaveformRequestKey = null
            }
            if (wasLoading && !meta.loading) {
                OmtLog.i("Remote", "Host finished loading soundcheck waveforms for $mixerId — re-requesting")
                lastSoundcheckWaveformRequestKey = null
            }
        }
    }

    private fun cachedSoundcheckOverview(
        mixerId: String,
        remote: org.openmultitrack.remote.RemoteMixerSnapshot,
    ): SessionWaveformOverview? {
        val dir = remote.selectedSoundcheckDir ?: return null
        val meta = remote.soundcheckWaveformMeta ?: return null
        val key = "$mixerId|$dir"
        val peaks = clientSoundcheckPeaks[key] ?: return null
        val expected = pendingSoundcheckChannelCount[key] ?: meta.channelCount.coerceAtLeast(1)
        if (peaks.size < expected) return null
        return SessionWaveformOverview(
            peaksByChannel = peaks.mapValues { it.value.copyOf() },
            peaksPerSec = meta.peaksPerSec.toFloat(),
            durationSec = meta.durationSec,
        )
    }

    private fun publishClientMirror(overviewOverrides: Map<String, SessionWaveformOverview> = emptyMap()) {
        val mirror = clientMirror ?: return
        val uiSettings = RemoteSnapshotMapper.remoteSettingsToUi(mirror.settings)
        val sessions = mirror.sessions.mapValues { (id, remote) ->
            val session = RemoteSnapshotMapper.remoteToMixerSession(
                remote,
                clientLivePeaks[id] ?: emptyMap(),
            )
            val overview = overviewOverrides[id] ?: cachedSoundcheckOverview(id, remote)
            overview?.let {
                session.copy(soundcheckWaveforms = it, soundcheckWaveformsLoading = false)
            } ?: session
        }
        val mixers = mirror.mixers.map { profile ->
            MixerProfile(
                id = profile.id,
                usbDeviceName = null,
                vendorId = 0,
                productId = 0,
                serialNumber = null,
                productName = null,
                displayName = profile.displayName,
            )
        }
        val activeMixerId = mirror.activeMixerId
            ?: mixers.firstOrNull()?.id
            ?: _state.value.activeMixerId
        OmtLog.d(
            "Remote",
            "Mirror updated: mixers=${mixers.size} sessions=${sessions.size} active=$activeMixerId",
        )
        _state.update {
            it.copy(
                mixers = mixers,
                activeMixerId = activeMixerId,
                sessionByMixer = sessions,
                uiSettings = uiSettings,
                connectionState = RemoteConnectionState.CONNECTED,
            )
        }
    }

    private fun stopHost() {
        syncJob?.cancel()
        syncJob = null
        announcer?.close()
        announcer = null
        hostServer?.stop()
        hostServer = null
        lastHostSnapshot = null
        liveWaveformGen.clear()
        liveWaveformPrevTails.clear()
    }

    private fun stopClient() {
        snapshotWaitJob?.cancel()
        snapshotWaitJob = null
        clientSocketOpen = false
        client?.disconnect()
        client = null
        clientMirror = null
        clientLivePeaks.clear()
        clientSoundcheckPeaks.clear()
        pendingSoundcheckChannelCount.clear()
        lastSoundcheckWaveformRequestKey = null
        lastSoundcheckLoadingByMixer.clear()
    }

    private fun stopAll() {
        reconnectJob?.cancel()
        reconnectJob = null
        stopHost()
        stopClient()
    }

    companion object {
        /** 0 = no socket read timeout; required for long-lived WebSocket clients. */
        private const val NanoTimeoutMs = 0
    }
}
