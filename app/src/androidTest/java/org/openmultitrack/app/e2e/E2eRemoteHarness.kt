package org.openmultitrack.app.e2e

import android.util.Log
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.remote.RemoteControlManager
import org.openmultitrack.app.service.AudioSessionClient
import org.openmultitrack.app.service.MixerSessionController
import org.openmultitrack.app.service.MultiMixerSessionManager
import org.openmultitrack.domain.remote.RemoteConnectionState
import org.openmultitrack.domain.remote.RemotePairedHost
import org.openmultitrack.domain.remote.RemoteRole
import org.openmultitrack.remote.RemoteDiscovery

class E2eRemoteHarness(
    private val appRule: E2eAppRule,
) {
    val context get() = appRule.appContext
    private val client = AudioSessionClient(context)
    private lateinit var manager: MultiMixerSessionManager
    private lateinit var remote: RemoteControlManager

    suspend fun bindSession(): Pair<MultiMixerSessionManager, RemoteControlManager> {
        val ready = CompletableDeferred<MultiMixerSessionManager>()
        client.whenReady { ready.complete(it) }
        client.bind()
        manager = withTimeout(20_000) { ready.await() }
        remote = client.getRemoteControl() ?: error("RemoteControlManager unavailable")
        client.promoteForeground("E2E remote test")
        return manager to remote
    }

    fun configureTestPin() {
        val settings = AppSettingsStore(context)
        settings.remotePairingPin = E2eConfig.pairingPin
        settings.remoteAuthToken = E2eConfig.pairingPin
        settings.listPairedRemoteHosts().forEach { settings.removePairedRemoteHost(it.hostId) }
    }

    fun ensureHostDisplayDefaults() {
        val settings = AppSettingsStore(context)
        settings.showWaveforms = true
        settings.showVuMeters = true
    }

    suspend fun startHost(): String {
        configureTestPin()
        remote.applyRole(RemoteRole.HOST)
        E2eWait.untilRemoteState(remote.state, 30_000) {
            it.role == RemoteRole.HOST && it.connectionState == RemoteConnectionState.CONNECTED
        }
        val ip = RemoteDiscovery.localIpv4(context)
        assertThat(ip).isNotNull()
        Log.i(E2eConfig.TAG, "HOST_READY ip=$ip pin=${E2eConfig.pairingPin} hostId=${remote.state.value.hostDeviceId}")
        return ip!!
    }

    suspend fun waitForRemoteClient(timeoutMs: Long = 180_000) {
        E2eWait.untilRemoteState(remote.state, timeoutMs) { it.connectedClientCount >= 1 }
    }

    suspend fun connectClient(hostIp: String): RemoteControlManager {
        configureTestPin()
        val settings = AppSettingsStore(context)
        settings.remoteRole = RemoteRole.CLIENT
        settings.savePairedRemoteHost(
            RemotePairedHost(
                hostId = hostIp,
                displayName = "E2E Host",
                pin = E2eConfig.pairingPin,
                lastHost = hostIp,
                lastPort = org.openmultitrack.domain.remote.RemoteProtocol.HTTP_PORT,
            ),
        )
        if (remote.state.value.role != RemoteRole.CLIENT) {
            remote.applyRole(RemoteRole.CLIENT, autoConnect = false)
        }
        remote.connectDirect(
            host = hostIp,
            port = org.openmultitrack.domain.remote.RemoteProtocol.HTTP_PORT,
            name = "E2E Host",
            hostId = hostIp,
            pin = E2eConfig.pairingPin,
        )
        E2eWait.untilRemoteConnected(remote.state, timeoutMs = 120_000)
        return remote
    }

    fun state() = remote.state

    fun sendRemote(command: String, payload: JSONObject = JSONObject()) {
        remote.sendCommand(command, payload)
    }

    fun disconnectClient() {
        remote.disconnect()
    }

    suspend fun reconnectClient(hostIp: String) {
        remote.exitRemoteClientMode()
        delay(1_500)
        connectClient(hostIp)
    }

    fun shutdown() {
        remote.shutdown()
        client.unbind()
    }
}
