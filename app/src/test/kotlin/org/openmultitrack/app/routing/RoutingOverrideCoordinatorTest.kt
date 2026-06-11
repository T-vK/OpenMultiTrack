package org.openmultitrack.app.routing

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.app.data.RoutingAutomationMethod
import org.openmultitrack.mixer.behringer.XAirChannelInputState
import org.openmultitrack.mixer.behringer.XAirInputSourceCatalog

class RoutingOverrideCoordinatorTest {
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
