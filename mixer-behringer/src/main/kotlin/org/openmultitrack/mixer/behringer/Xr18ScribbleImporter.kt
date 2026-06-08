package org.openmultitrack.mixer.behringer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
/** One USB audio channel's scribble label resolved via OSC routing. */
data class UsbChannelScribble(
    val usbChannel: Int,
    val sourceLabel: String,
    val name: String?,
    val colorIndex: Int?,
) {
    val colorArgb: Int? get() = XAirScribbleColors.toArgb(colorIndex)
    val stripIndex: Int get() = usbChannel - 1
}

/**
 * Fetches XR18/X18 USB channel names and colors over network OSC.
 *
 * See `docs/xr18-scribble-strip/01-scribble-strip-access.md`.
 */
class Xr18ScribbleImporter(
    private val port: Int = Xr18Mixer.DEFAULT_PORT,
) {
    suspend fun discoverMixerIp(timeoutMs: Long = 3000): String? = withContext(Dispatchers.IO) {
        OscUdpClient.discoverMixer(timeoutMs, port)
    }

    suspend fun fetchUsbLabels(host: String): Result<List<UsbChannelScribble>> = withContext(Dispatchers.IO) {
        runCatching {
            OscUdpClient(host, port).use { client ->
                val replies = client.query(collectLabelPaths())
                (1..USB_CHANNELS).map { usb ->
                    val src = (replies["/routing/usb/${usb.toString().padStart(2, '0')}/src"]?.firstOrNull() as? Int)
                    val (source, name, color) = if (src != null) resolveSrc(replies, src) else Triple("?", null, null)
                    UsbChannelScribble(usb, source, name, color)
                }
            }
        }
    }

    companion object {
        const val USB_CHANNELS = 18

        private val USB_ROUTE_SRC = buildList {
            addAll((1..16).map { "Ch%02d".format(it) })
            add("AuxL")
            add("AuxR")
            addAll((1..4).flatMap { n -> listOf("Fx${n}L", "Fx${n}R") })
            addAll((1..6).map { "Bus$it" })
            addAll((1..4).map { "Send$it" })
            add("L")
            add("R")
        }

        fun collectLabelPaths(): List<String> = buildList {
            for (i in 1..16) {
                val nn = i.toString().padStart(2, '0')
                add("/ch/$nn/config/name")
                add("/ch/$nn/config/color")
            }
            for (i in 1..6) {
                add("/bus/$i/config/name")
                add("/bus/$i/config/color")
            }
            for (i in 1..4) {
                add("/fxsend/$i/config/name")
                add("/fxsend/$i/config/color")
                add("/rtn/$i/config/name")
                add("/rtn/$i/config/color")
            }
            add("/rtn/aux/config/name")
            add("/rtn/aux/config/color")
            add("/lr/config/name")
            add("/lr/config/color")
            for (n in 1..USB_CHANNELS) {
                add("/routing/usb/${n.toString().padStart(2, '0')}/src")
            }
        }

        private fun getLabel(replies: Map<String, List<Any>>, base: String): Pair<String?, Int?> {
            val name = replies["$base/name"]?.firstOrNull() as? String
            val color = replies["$base/color"]?.firstOrNull() as? Int
            return name to color
        }

        internal fun resolveSrc(replies: Map<String, List<Any>>, src: Int): Triple<String, String?, Int?> {
            if (src !in USB_ROUTE_SRC.indices) return Triple("?", null, null)
            val label = USB_ROUTE_SRC[src]

            if (label.startsWith("Ch")) {
                val ch = label.substring(2).toInt()
                val base = "/ch/${ch.toString().padStart(2, '0')}/config"
                val (name, color) = getLabel(replies, base)
                return Triple(label, name, color)
            }

            if (label == "AuxL" || label == "AuxR") {
                val (name, color) = getLabel(replies, "/rtn/aux/config")
                val side = if (label == "AuxL") "L" else "R"
                val display = name?.let { "$it ($side)" }
                return Triple(label, display, color)
            }

            if (label.startsWith("Fx") && (label.endsWith("L") || label.endsWith("R"))) {
                val n = label[2].digitToInt()
                val base = "/rtn/$n/config"
                val (name, color) = getLabel(replies, base)
                val side = if (label.endsWith("R")) "R" else "L"
                val display = name?.let { "$it ($side)" }
                return Triple(label, display, color)
            }

            if (label.startsWith("Bus")) {
                val n = label.removePrefix("Bus").toInt()
                val base = "/bus/$n/config"
                val (name, color) = getLabel(replies, base)
                return Triple(label, name, color)
            }

            if (label.startsWith("Send")) {
                val n = label.removePrefix("Send").toInt()
                val base = "/fxsend/$n/config"
                val (name, color) = getLabel(replies, base)
                return Triple(label, name, color)
            }

            if (label == "L" || label == "R") {
                val (name, color) = getLabel(replies, "/lr/config")
                val display = name?.let { "$it ($label)" }
                return Triple(label, display, color)
            }

            return Triple(label, null, null)
        }
    }
}
