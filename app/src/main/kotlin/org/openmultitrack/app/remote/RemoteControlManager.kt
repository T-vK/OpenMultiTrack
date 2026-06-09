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
    private var lastSoundcheckWaveformRequestKey: String? = null

    fun applyRole(role: RemoteRole) {
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
                discoverHosts()
            }
        }
    }

    fun enterRemoteClientMode() {
        if (_state.value.role == RemoteRole.CLIENT && isRemoteClientConnected()) return
        applyRole(RemoteRole.CLIENT)
        refreshPairingState()
        discoverHosts()
    }

    fun discoverHosts() {
        if (_state.value.role != RemoteRole.CLIENT) return
        scope.launch {
            _state.update { it.copy(connectionState = RemoteConnectionState.DISCOVERING, errorMessage = null) }
            val pairedIds = settings.listPairedRemoteHosts().map { it.hostId }.toSet()
            val hosts = RemoteDiscovery.discoverHosts(appContext, pairedHostIds = pairedIds)
                .sortedWith(compareByDescending<RemoteDiscoveredHost> { it.isPaired }.thenBy { it.name })
            OmtLog.i("Remote", "Discovered ${hosts.size} host(s), paired ids=${pairedIds.size}")
            _state.update {
                it.copy(
                    discoveredHosts = hosts,
                    pairedHosts = settings.listPairedRemoteHosts(),
                    connectionState = RemoteConnectionState.DISCONNECTED,
                    errorMessage = if (hosts.isEmpty()) "No hosts found on LAN" else null,
                )
            }
            val target = hosts.firstOrNull { it.isPaired } ?: hosts.firstOrNull()
            target?.let {
                OmtLog.i("Remote", "Connecting to ${it.name} at ${it.host}:${it.port} paired=${it.isPaired}")
                connectToHost(it)
            }
        }
    }

    fun connectToHost(host: RemoteDiscoveredHost) {
        if (_state.value.role != RemoteRole.CLIENT) return
        val pin = host.hostId?.let { settings.pinForPairedHost(it) }
        connectToAddress(host.host, host.port, host.name, host.hostId, pin)
    }

    fun connectManual(host: String, pin: String, port: Int = RemoteProtocol.HTTP_PORT) {
        if (_state.value.role != RemoteRole.CLIENT) enterRemoteClientMode()
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
            ),
        )
        refreshPairingState()
        if (_state.value.role != RemoteRole.CLIENT) enterRemoteClientMode()
        payload.host?.let { host ->
            connectToAddress(host, payload.port, payload.name, payload.hostId, payload.pin)
        } ?: run {
            discoverHosts()
        }
    }

    fun refreshPairingState() {
        val hostId = settings.remoteHostDeviceId
        val pin = settings.remotePairingPin
        val name = Build.MODEL.ifBlank { "OpenMultiTrack" }
        _state.update {
            it.copy(
                hostDeviceId = hostId,
                pairingPin = pin,
                pairingUri = RemotePairing.buildPairingUri(hostId, pin, name),
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
        _state.update {
            it.copy(
                connectionState = RemoteConnectionState.CONNECTING,
                hostName = name,
                connectedHost = host,
                errorMessage = null,
            )
        }
        client = RemoteClient(listener = clientListener).also {
            it.connect(host, port, pin)
        }
        if (hostId != null && !pin.isNullOrBlank()) {
            settings.savePairedRemoteHost(RemotePairedHost(hostId, name, pin))
        }
    }

    fun disconnect() {
        when (_state.value.role) {
            RemoteRole.CLIENT -> stopClient()
            RemoteRole.HOST -> stopHost()
            RemoteRole.OFF -> Unit
        }
        _state.update {
            it.copy(
                connectionState = RemoteConnectionState.DISCONNECTED,
                connectedHost = null,
                sessionByMixer = emptyMap(),
                mixers = emptyList(),
                activeMixerId = null,
            )
        }
    }

    fun isRemoteClientConnected(): Boolean =
        _state.value.role == RemoteRole.CLIENT &&
            _state.value.connectionState == RemoteConnectionState.CONNECTED

    fun sendCommand(command: String, payload: JSONObject = JSONObject()) {
        if (!isRemoteClientConnected()) return
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
                pairingUri = RemotePairing.buildPairingUri(hostId, pin, hostName),
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
            sendToClient(RemoteJsonCodec.encodeSnapshot(snapshot))
        }

        override fun onClientMessage(json: String, sendToClient: (String) -> Unit) {
            val manager = getManager() ?: return
            val (command, payload) = runCatching { RemoteJsonCodec.decodeCommand(json) }.getOrElse {
                sendToClient(RemoteHostServer.encodeAck("unknown", false, it.message))
                return
            }
            val executor = RemoteCommandExecutor(manager, settings, promoteForeground)
            val result = executor.execute(command, payload)
            result.onSuccess { waveform ->
                waveform?.let {
                    sendToClient(RemoteJsonCodec.encodeWaveformChunk(it.channel, it.startSec, it.peaks))
                }
                sendToClient(RemoteHostServer.encodeAck(command, true))
                pushFullSnapshot()
            }.onFailure { e ->
                sendToClient(RemoteHostServer.encodeAck(command, false, e.message))
            }
        }

        override fun onClientDisconnected() = Unit
    }

    private val clientListener = object : RemoteClient.Listener {
        override fun onConnected() {
            OmtLog.i("Remote", "Client connected to host")
            _state.update { it.copy(connectionState = RemoteConnectionState.CONNECTED, errorMessage = null) }
        }

        override fun onMessage(json: String) {
            val root = runCatching { JSONObject(json) }.getOrNull() ?: return
            when (root.optString("type")) {
                "snapshot" -> applyClientSnapshot(RemoteJsonCodec.decodeSnapshot(json))
                "delta" -> {
                    val delta = RemoteJsonCodec.decodeDelta(json)
                    val base = clientMirror ?: return
                    clientMirror = RemoteSnapshotMapper.applyDelta(base, delta, clientLivePeaks)
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
                    val (channel, startSec, peaks) = RemoteJsonCodec.decodeWaveformChunk(json)
                    val mixerId = _state.value.activeMixerId ?: return
                    val sessionDir = clientMirror?.sessions?.get(mixerId)?.selectedSoundcheckDir ?: return
                    val key = "$mixerId|$sessionDir"
                    val channels = clientSoundcheckPeaks.getOrPut(key) { mutableMapOf() }
                    channels[channel] = peaks
                    mergeSoundcheckWaveforms(mixerId, sessionDir)
                }
            }
        }

        override fun onDisconnected(reason: String?) {
            _state.update {
                it.copy(
                    connectionState = RemoteConnectionState.DISCONNECTED,
                    errorMessage = reason,
                    sessionByMixer = emptyMap(),
                )
            }
            clientMirror = null
            clientLivePeaks.clear()
        }

        override fun onFailure(error: String) {
            OmtLog.e("Remote", "Client connection failed: $error")
            _state.update {
                it.copy(
                    connectionState = RemoteConnectionState.ERROR,
                    errorMessage = error,
                )
            }
        }
    }

    private fun applyClientSnapshot(snapshot: RemoteMirrorSnapshot) {
        val previousDir = clientMirror?.sessions?.get(snapshot.activeMixerId ?: "")?.selectedSoundcheckDir
        clientMirror = snapshot
        val newDir = snapshot.sessions[snapshot.activeMixerId ?: ""]?.selectedSoundcheckDir
        if (previousDir != newDir) {
            lastSoundcheckWaveformRequestKey = null
        }
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
        lastSoundcheckWaveformRequestKey = requestKey
        clientSoundcheckPeaks.remove("$mixerId|$dir")
        requestSoundcheckWaveforms(
            mixerId = mixerId,
            sessionDir = dir,
            channelCount = meta.channelCount.coerceAtLeast(session.captureChannelCount),
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
        publishClientMirror(overviewOverrides = mapOf(mixerId to overview))
    }

    private fun publishClientMirror(overviewOverrides: Map<String, SessionWaveformOverview> = emptyMap()) {
        val mirror = clientMirror ?: return
        val uiSettings = RemoteSnapshotMapper.remoteSettingsToUi(mirror.settings)
        val sessions = mirror.sessions.mapValues { (id, remote) ->
            val session = RemoteSnapshotMapper.remoteToMixerSession(
                remote,
                clientLivePeaks[id] ?: emptyMap(),
            )
            overviewOverrides[id]?.let { overview ->
                session.copy(soundcheckWaveforms = overview, soundcheckWaveformsLoading = false)
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
        _state.update {
            it.copy(
                mixers = mixers,
                activeMixerId = mirror.activeMixerId,
                sessionByMixer = sessions,
                uiSettings = uiSettings,
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
        client?.disconnect()
        client = null
        clientMirror = null
        clientLivePeaks.clear()
        clientSoundcheckPeaks.clear()
        lastSoundcheckWaveformRequestKey = null
    }

    private fun stopAll() {
        stopHost()
        stopClient()
    }

    companion object {
        /** 0 = no socket read timeout; required for long-lived WebSocket clients. */
        private const val NanoTimeoutMs = 0
    }
}
