package org.openmultitrack.app.e2e

import android.content.Context
import android.util.Log
import com.google.common.truth.Truth.assertWithMessage
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.data.MixerDeviceStore
import org.openmultitrack.app.data.MixerRoutingAutomationConfig
import org.openmultitrack.app.data.RoutingAutomationLevel
import org.openmultitrack.app.routing.LanOscRoutingPort
import org.openmultitrack.app.routing.OscLanSession
import org.openmultitrack.app.scribble.OscLanDiscovery
import org.openmultitrack.app.service.MixerSessionController
import org.openmultitrack.mixer.behringer.MixerRoutingPort
import org.openmultitrack.mixer.behringer.RoutingConfirmResult
import org.openmultitrack.mixer.behringer.XAirChannelInputState
import org.openmultitrack.mixer.behringer.XAirInputSourceCatalog
import org.openmultitrack.mixer.behringer.Xr18RoutingService

/** Shared XR18 routing OSC setup for on-device e2e (same stack as the app). */
object Xr18RoutingE2eHarness {
    const val TAG = "Xr18RoutingE2e"

    suspend fun discoverOscHost(context: Context): String {
        val prefer = E2eConfig.oscHost
        prefer?.let { saved ->
            OscLanDiscovery.probeMixerAt(context, saved, timeoutMs = 5000)?.let { return it }
            Log.w(TAG, "saved osc_host $saved did not respond to probe — trying discovery")
        }
        return OscLanDiscovery.discoverMixerIp(context, preferHost = prefer, timeoutMs = 15_000)
            ?: error("XR18 not found on LAN — tablet and mixer must share Wi‑Fi (osc_host=$prefer)")
    }

    fun routingPort(context: Context, oscHost: String): MixerRoutingPort =
        LanOscRoutingPort(
            context,
            Xr18RoutingService(
                oscHost,
                socketSetup = OscLanSession.wifiSocketSetup(context),
            ),
        )

    fun logStep(step: String, result: RoutingConfirmResult) {
        Log.i(TAG, "[$step] ${result.report()}")
    }

    fun assertConfirmed(step: String, result: RoutingConfirmResult) {
        logStep(step, result)
        assertWithMessage("[${step}] ${result.report()}")
            .that(result.confirmed)
            .isTrue()
    }

    fun assertReadMatches(
        step: String,
        channelIndex: Int,
        target: XAirChannelInputState,
        live: XAirChannelInputState?,
        replyPaths: Set<String> = emptySet(),
    ) {
        assertConfirmed(
            step,
            RoutingConfirmResult(channelIndex, target, live, replyPaths),
        )
    }

    fun describe(state: XAirChannelInputState): String = state.describe()

    fun usbTarget(channelIndex: Int): XAirChannelInputState =
        XAirInputSourceCatalog.soundcheckTarget(channelIndex)

    fun adTarget(channelIndex: Int): XAirChannelInputState =
        XAirInputSourceCatalog.recordTarget(channelIndex)

    /** Discover OSC, persist host on profile, enable AUTO routing for e2e. */
    suspend fun configureProfileForRouting(context: Context, mixerId: String): Pair<String, MixerRoutingPort> {
        val oscHost = discoverOscHost(context)
        val store = MixerDeviceStore(context)
        val profile = store.listMixers().first { it.id == mixerId }
        store.saveMixer(profile.copy(oscHost = oscHost))
        AppSettingsStore(context).setRoutingAutomationForMixer(
            mixerId,
            MixerRoutingAutomationConfig(level = RoutingAutomationLevel.AUTO),
        )
        Log.i(TAG, "routing AUTO mixer=$mixerId oscHost=$oscHost")
        return oscHost to routingPort(context, oscHost)
    }

    fun routableArmedChannels(ctrl: MixerSessionController): Set<Int> =
        ctrl.state.value.channelStrips
            .filter { it.armed }
            .map { it.index }
            .let { XAirInputSourceCatalog.routableIndices(it) }

    suspend fun assertChannelsConfirmed(
        port: MixerRoutingPort,
        channels: Set<Int>,
        targetFor: (Int) -> XAirChannelInputState,
        step: String,
    ) {
        for (ch in channels.sorted()) {
            val target = targetFor(ch)
            val result = port.confirmChannelRouting(ch, target)
            assertConfirmed("$step CH${ch + 1}", result)
        }
    }
}
