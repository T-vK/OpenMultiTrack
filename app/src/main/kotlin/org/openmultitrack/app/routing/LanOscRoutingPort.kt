package org.openmultitrack.app.routing

import android.content.Context
import org.openmultitrack.mixer.behringer.MixerRoutingPort
import org.openmultitrack.mixer.behringer.RoutingConfirmResult
import org.openmultitrack.mixer.behringer.XAirChannelInputState

/** Runs [MixerRoutingPort] OSC I/O on Android LAN (multicast lock + bound socket). */
class LanOscRoutingPort(
    private val context: Context,
    private val delegate: MixerRoutingPort,
) : MixerRoutingPort {
    override suspend fun probe(timeoutMs: Long): Boolean =
        OscLanSession.withMulticastLock(context) { delegate.probe(timeoutMs) }

    override suspend fun readChannelInput(channelIndex: Int): XAirChannelInputState? =
        OscLanSession.withMulticastLock(context) { delegate.readChannelInput(channelIndex) }

    override suspend fun readAllChannelInputs(): Map<Int, XAirChannelInputState> =
        OscLanSession.withMulticastLock(context) { delegate.readAllChannelInputs() }

    override suspend fun readChannelInputs(channelIndices: Iterable<Int>): Map<Int, XAirChannelInputState> =
        OscLanSession.withMulticastLock(context) { delegate.readChannelInputs(channelIndices) }

    override suspend fun captureAndApplyRouting(
        channelIndices: Iterable<Int>,
        targets: Map<Int, XAirChannelInputState>,
        deferApply: Boolean,
        soundcheck: Boolean,
        probeTimeoutMs: Long,
    ): org.openmultitrack.mixer.behringer.RoutingCaptureApplyResult =
        OscLanSession.withMulticastLock(context) {
            delegate.captureAndApplyRouting(
                channelIndices,
                targets,
                deferApply,
                soundcheck,
                probeTimeoutMs,
            )
        }

    override suspend fun writeChannelInput(channelIndex: Int, state: XAirChannelInputState): Boolean =
        OscLanSession.withMulticastLock(context) { delegate.writeChannelInput(channelIndex, state) }

    override suspend fun writeChannelInputOnly(channelIndex: Int, state: XAirChannelInputState) =
        OscLanSession.withMulticastLock(context) { delegate.writeChannelInputOnly(channelIndex, state) }

    override suspend fun confirmChannelRouting(
        channelIndex: Int,
        target: XAirChannelInputState,
    ): RoutingConfirmResult = OscLanSession.withMulticastLock(context) {
        delegate.confirmChannelRouting(channelIndex, target)
    }

    override suspend fun applyRecordRouting(
        channelIndices: Iterable<Int>,
        liveByChannel: Map<Int, XAirChannelInputState>,
    ): Boolean = OscLanSession.withMulticastLock(context) {
        delegate.applyRecordRouting(channelIndices, liveByChannel)
    }

    override suspend fun applySoundcheckRouting(
        channelIndices: Iterable<Int>,
        liveByChannel: Map<Int, XAirChannelInputState>,
    ): Boolean = OscLanSession.withMulticastLock(context) {
        delegate.applySoundcheckRouting(channelIndices, liveByChannel)
    }

    override suspend fun restoreChannels(
        baseline: Map<Int, XAirChannelInputState>,
        channels: Set<Int>,
        liveByChannel: Map<Int, XAirChannelInputState>,
    ): Boolean = OscLanSession.withMulticastLock(context) {
        delegate.restoreChannels(baseline, channels, liveByChannel)
    }

    override suspend fun loadSnapshot(slot: Int): Boolean =
        OscLanSession.withMulticastLock(context) { delegate.loadSnapshot(slot) }
}
