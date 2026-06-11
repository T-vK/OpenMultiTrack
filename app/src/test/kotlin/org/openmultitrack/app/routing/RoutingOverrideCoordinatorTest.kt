package org.openmultitrack.app.routing

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.openmultitrack.app.data.MixerRoutingAutomationConfig
import org.openmultitrack.app.data.RoutingAutomationLevel
import org.openmultitrack.app.data.RoutingAutomationMethod
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.mixer.behringer.MixerRoutingPort
import org.openmultitrack.mixer.behringer.RoutingConfirmResult
import org.openmultitrack.mixer.behringer.XAirChannelInputState
import org.openmultitrack.mixer.behringer.XAirInputSourceCatalog

class RoutingOverrideCoordinatorTest {
    private val xr18Profile = MixerProfile(
        id = "xr18-test",
        usbDeviceName = null,
        vendorId = 0x1397,
        productId = 0x7508,
        serialNumber = null,
        productName = "XR18",
        displayName = "XR18",
        oscHost = "192.168.1.100",
    )

    private class TestRoutingPort(
        private var reachable: Boolean = true,
        private var channels: MutableMap<Int, XAirChannelInputState> = mutableMapOf(),
        var applyShouldSucceed: Boolean = true,
        var restoreShouldSucceed: Boolean = true,
    ) : MixerRoutingPort {
        override suspend fun probe(timeoutMs: Long): Boolean = reachable

        override suspend fun readChannelInput(channelIndex: Int): XAirChannelInputState? =
            channels[channelIndex]

        override suspend fun readAllChannelInputs(): Map<Int, XAirChannelInputState> =
            channels.toMap()

        override suspend fun readChannelInputs(channelIndices: Iterable<Int>): Map<Int, XAirChannelInputState> =
            channelIndices.mapNotNull { ch -> channels[ch]?.let { ch to it } }.toMap()

        override suspend fun captureAndApplyRouting(
            channelIndices: Iterable<Int>,
            targets: Map<Int, XAirChannelInputState>,
            deferApply: Boolean,
            soundcheck: Boolean,
            probeTimeoutMs: Long,
        ): org.openmultitrack.mixer.behringer.RoutingCaptureApplyResult {
            if (!reachable) {
                return org.openmultitrack.mixer.behringer.RoutingCaptureApplyResult(false, emptyMap(), false)
            }
            val live = readChannelInputs(channelIndices)
            if (deferApply) {
                return org.openmultitrack.mixer.behringer.RoutingCaptureApplyResult(true, live, true)
            }
            val applied = if (soundcheck) {
                applySoundcheckRouting(channelIndices, live)
            } else {
                applyRecordRouting(channelIndices, live)
            }
            return org.openmultitrack.mixer.behringer.RoutingCaptureApplyResult(true, live, applied)
        }

        override suspend fun writeChannelInput(channelIndex: Int, state: XAirChannelInputState): Boolean {
            channels[channelIndex] = state
            return true
        }

        override suspend fun writeChannelInputOnly(channelIndex: Int, state: XAirChannelInputState) {
            channels[channelIndex] = state
        }

        override suspend fun confirmChannelRouting(
            channelIndex: Int,
            target: XAirChannelInputState,
        ): RoutingConfirmResult = RoutingConfirmResult(channelIndex, target, channels[channelIndex])

        override suspend fun applyRecordRouting(
            channelIndices: Iterable<Int>,
            liveByChannel: Map<Int, XAirChannelInputState>,
        ): Boolean {
            if (!applyShouldSucceed) return false
            return channelIndices.all { writeChannelInput(it, XAirInputSourceCatalog.recordTarget(it)) }
        }

        override suspend fun applySoundcheckRouting(
            channelIndices: Iterable<Int>,
            liveByChannel: Map<Int, XAirChannelInputState>,
        ): Boolean {
            if (!applyShouldSucceed) return false
            return channelIndices.all { writeChannelInput(it, XAirInputSourceCatalog.soundcheckTarget(it)) }
        }

        override suspend fun restoreChannels(
            baseline: Map<Int, XAirChannelInputState>,
            channels: Set<Int>,
            liveByChannel: Map<Int, XAirChannelInputState>,
        ): Boolean {
            if (!restoreShouldSucceed) return false
            return channels.all { ch ->
                val st = baseline[ch] ?: return@all true
                writeChannelInput(ch, st)
            }
        }

        override suspend fun loadSnapshot(slot: Int): Boolean = true
    }

    private class MemoryBaselineStore : RoutingPendingStore {
        private var pending: PendingRoutingRestore? = null

        override fun save(restore: PendingRoutingRestore) {
            pending = restore
        }

        override fun load(): PendingRoutingRestore? = pending

        override fun clear() {
            pending = null
        }
    }
    @Test
    fun detectConflicts_findsEngineerChanges() {
        val pending = PendingRoutingRestore(
            transactionId = "t1",
            mixerId = "m1",
            oscHost = "1.2.3.4",
            kind = RoutingOverrideKind.RECORD,
            affectedChannels = setOf(0),
            baselineByChannel = mapOf(0 to XAirChannelInputState(1, 0, 0)),
            overrideByChannel = mapOf(0 to XAirInputSourceCatalog.recordTarget(0)),
            method = RoutingAutomationMethod.PER_CHANNEL,
            capturedAtEpochMs = 0L,
        )
        val live = mapOf(0 to XAirChannelInputState(5, 0, 0))
        val conflicts = RoutingOverrideCoordinator.detectConflicts(pending, live)
        assertThat(conflicts).hasSize(1)
        assertThat(conflicts[0].live.insrc).isEqualTo(5)
    }

    @Test
    fun detectConflicts_noneWhenStillAtOverride() {
        val applied = XAirInputSourceCatalog.soundcheckTarget(0)
        val pending = PendingRoutingRestore(
            transactionId = "t1",
            mixerId = "m1",
            oscHost = "1.2.3.4",
            kind = RoutingOverrideKind.SOUNDCHECK,
            affectedChannels = setOf(0),
            baselineByChannel = mapOf(0 to XAirChannelInputState(1, 0, 0)),
            overrideByChannel = mapOf(0 to applied),
            method = RoutingAutomationMethod.PER_CHANNEL,
            capturedAtEpochMs = 0L,
        )
        val conflicts = RoutingOverrideCoordinator.detectConflicts(pending, mapOf(0 to applied))
        assertThat(conflicts).isEmpty()
    }

    @Test
    fun applyConfirmed_clearsPendingWhenApplyFails() = runBlocking {
        val store = MemoryBaselineStore()
        val port = TestRoutingPort(
            channels = mutableMapOf(0 to XAirChannelInputState(0, 0, 1)),
            applyShouldSucceed = false,
        )
        val coordinator = RoutingOverrideCoordinator(store) { port }
        val config = MixerRoutingAutomationConfig(level = RoutingAutomationLevel.AUTO)

        val outcome = coordinator.applyConfirmed(
            xr18Profile,
            config,
            RoutingOverrideKind.RECORD,
            setOf(0),
        )

        assertThat(outcome).isInstanceOf(RoutingApplyOutcome.Failed::class.java)
        assertThat(store.load()).isNull()
    }

    @Test
    fun applyConfirmed_succeedsAndKeepsPendingUntilRestore() = runBlocking {
        val store = MemoryBaselineStore()
        val port = TestRoutingPort(
            channels = mutableMapOf(0 to XAirChannelInputState(0, 0, 1)),
        )
        val coordinator = RoutingOverrideCoordinator(store) { port }
        val config = MixerRoutingAutomationConfig(level = RoutingAutomationLevel.AUTO)

        val outcome = coordinator.applyConfirmed(
            xr18Profile,
            config,
            RoutingOverrideKind.RECORD,
            setOf(0),
        )

        assertThat(outcome).isInstanceOf(RoutingApplyOutcome.Applied::class.java)
        val pending = store.load()
        assertThat(pending).isNotNull()
        assertThat(pending!!.affectedChannels).containsExactly(0)
        assertThat(port.readChannelInput(0)?.usesUsbReturn).isFalse()
    }

    @Test
    fun restoreConfirmed_clearsPendingOnSuccess() = runBlocking {
        val store = MemoryBaselineStore()
        val port = TestRoutingPort(
            channels = mutableMapOf(0 to XAirInputSourceCatalog.recordTarget(0)),
        )
        val coordinator = RoutingOverrideCoordinator(store) { port }
        val baseline = XAirChannelInputState(0, 0, 1)
        val pending = PendingRoutingRestore(
            transactionId = "t1",
            mixerId = xr18Profile.id,
            oscHost = xr18Profile.oscHost!!,
            kind = RoutingOverrideKind.RECORD,
            affectedChannels = setOf(0),
            baselineByChannel = mapOf(0 to baseline),
            overrideByChannel = mapOf(0 to XAirInputSourceCatalog.recordTarget(0)),
            method = RoutingAutomationMethod.PER_CHANNEL,
            capturedAtEpochMs = 0L,
        )
        store.save(pending)
        val config = MixerRoutingAutomationConfig(level = RoutingAutomationLevel.AUTO)

        val outcome = coordinator.restoreConfirmed(config, pending)

        assertThat(outcome).isEqualTo(RoutingRestoreOutcome.Restored)
        assertThat(store.load()).isNull()
        assertThat(port.readChannelInput(0)).isEqualTo(baseline)
    }

    @Test
    fun applyConfirmed_ignoresNonRoutableChannels() = runBlocking {
        val store = MemoryBaselineStore()
        val port = TestRoutingPort(
            channels = mutableMapOf(
                0 to XAirChannelInputState(0, 0, 1),
                16 to XAirChannelInputState(0, 0, 1),
            ),
        )
        val coordinator = RoutingOverrideCoordinator(store) { port }
        val config = MixerRoutingAutomationConfig(level = RoutingAutomationLevel.AUTO)

        val outcome = coordinator.applyConfirmed(
            xr18Profile,
            config,
            RoutingOverrideKind.RECORD,
            setOf(0, 16, 17),
        )

        assertThat(outcome).isInstanceOf(RoutingApplyOutcome.Applied::class.java)
        val pending = store.load()
        assertThat(pending?.affectedChannels).containsExactly(0)
        assertThat(port.readChannelInput(0)?.usesUsbReturn).isFalse()
    }

    @Test
    fun pendingRoundTrip_json() {
        val pending = PendingRoutingRestore(
            transactionId = "abc",
            mixerId = "mix",
            oscHost = "10.0.0.1",
            kind = RoutingOverrideKind.RECORD,
            affectedChannels = setOf(1, 2),
            baselineByChannel = mapOf(1 to XAirChannelInputState(2, 1, 0)),
            overrideByChannel = mapOf(1 to XAirInputSourceCatalog.recordTarget(1)),
            method = RoutingAutomationMethod.PER_CHANNEL,
            capturedAtEpochMs = 123L,
        )
        val json = pending.toJson().toString()
        val restored = PendingRoutingRestore.fromJson(json)
        assertThat(restored?.transactionId).isEqualTo("abc")
        assertThat(restored?.affectedChannels).containsExactly(1, 2)
    }
}
