package org.openmultitrack.sessionio.wav

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Converts USB packed PCM (24- or 32-bit subframes) to 24-bit little-endian WAV samples. */
object PcmFormatConversion {
  fun interleavedToWav24(
      src: ByteArray,
      frames: Int,
      channels: Int,
      srcBytesPerFrame: Int,
      dest: ByteArray,
      destOffset: Int = 0,
  ) {
      val subframeBytes = srcBytesPerFrame / channels.coerceAtLeast(1)
      var di = destOffset
      for (f in 0 until frames) {
          val frameBase = f * srcBytesPerFrame
          for (ch in 0 until channels) {
              val int24 = when (subframeBytes) {
                  4 -> {
                      val sample = ByteBuffer.wrap(src, frameBase + ch * 4, 4)
                          .order(ByteOrder.LITTLE_ENDIAN)
                          .int
                      sample shr 8
                  }
                  3 -> {
                      val p = frameBase + ch * 3
                      var v = src[p].toInt() and 0xFF or
                          ((src[p + 1].toInt() and 0xFF) shl 8) or
                          ((src[p + 2].toInt() and 0xFF) shl 16)
                      if (v and 0x800000 != 0) v = v or -0x1000000
                      v
                  }
                  2 -> {
                      val sample = ByteBuffer.wrap(src, frameBase + ch * 2, 2)
                          .order(ByteOrder.LITTLE_ENDIAN)
                          .short
                          .toInt()
                      sample shl 8
                  }
                  else -> 0
              }
              dest[di++] = (int24 and 0xFF).toByte()
              dest[di++] = ((int24 shr 8) and 0xFF).toByte()
              dest[di++] = ((int24 shr 16) and 0xFF).toByte()
          }
      }
  }

  fun wav24ByteLength(frames: Int, channels: Int): Int = frames * channels * 3
}
