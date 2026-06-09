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
import org.openmultitrack.remote.RemoteDiscoveredHost

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

    suspend fun waitForRemoteClient(timeoutMs: Long = 120_000) {
        E2eWait.untilRemoteState(remote.state, timeoutMs) { it.connectedClientCount >= 1 }
    }

    suspend fun connectClient(hostIp: String): RemoteControlManager {
        configureTestPin()
        val settings = AppSettingsStore(context)
        settings.remoteRole = RemoteRole.CLIENT
        remote.applyRole(RemoteRole.CLIENT)

        val host = discoverOrManualHost(hostIp)
        settings.savePairedRemoteHost(
            RemotePairedHost(
                hostId = host.hostId ?: host.host,
                displayName = host.name,
                pin = E2eConfig.pairingPin,
            ),
        )
        remote.connectToHost(host)
        E2eWait.untilRemoteConnected(remote.state)
        return remote
    }

    private suspend fun discoverOrManualHost(hostIp: String): RemoteDiscoveredHost {
        repeat(10) {
            val hosts = RemoteDiscovery.discoverHosts(context, timeoutMs = 2_000)
            hosts.firstOrNull { it.host == hostIp }?.let { return it }
            delay(1_000)
        }
        return RemoteDiscoveredHost(
            name = "E2E Host",
            host = hostIp,
            port = org.openmultitrack.domain.remote.RemoteProtocol.HTTP_PORT,
            protocolVersion = org.openmultitrack.domain.remote.RemoteProtocol.VERSION,
            hostId = null,
            isPaired = true,
        )
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
