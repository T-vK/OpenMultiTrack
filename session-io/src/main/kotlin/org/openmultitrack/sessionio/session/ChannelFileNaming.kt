package org.openmultitrack.sessionio.session

object ChannelFileNaming {
    fun channelPrefix(index: Int): String = "channel%02d".format(index + 1)

    fun fileName(index: Int, customLabel: String?): String {
        val prefix = channelPrefix(index)
        val label = customLabel?.trim()?.takeIf { it.isNotEmpty() }
        return if (label != null) "$prefix - $label.wav" else "$prefix.wav"
    }

    fun displayName(index: Int, customLabel: String?): String {
        val num = index + 1
        val label = customLabel?.trim()?.takeIf { it.isNotEmpty() }
        return if (label != null) "#$num $label" else "#$num"
    }
}
