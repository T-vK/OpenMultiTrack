package org.openmultitrack.mixer.behringer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches XR18 snapshot slot names over network OSC.
 *
 * Uses a plain UDP client (no Android Wi‑Fi socket bind) — same pattern as [Xr18ScribbleImporter].
 */
class Xr18SnapshotNameImporter(
    private val port: Int = Xr18Mixer.DEFAULT_PORT,
) {
    suspend fun fetchSnapshotNames(
        host: String,
        onProgress: (List<MixerSnapshotOption>, scannedSlots: Int) -> Unit = { _, _ -> },
    ): List<MixerSnapshotOption> = withContext(Dispatchers.IO) {
        OscUdpClient(host, port).use { client ->
            Xr18RoutingOsc.readAllSnapshotNames(client, onProgress)
        }
    }
}
