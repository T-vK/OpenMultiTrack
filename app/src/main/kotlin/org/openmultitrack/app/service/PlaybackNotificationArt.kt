package org.openmultitrack.app.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.content.ContextCompat
import org.openmultitrack.app.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * Album art for the playback media notification — dark studio gradient with subtle lane lines
 * and a soft waveform accent (matches the app icon palette).
 */
object PlaybackNotificationArt {
    private const val SIZE_PX = 512
    private var cached: Bitmap? = null

    fun albumArt(context: Context): Bitmap {
        cached?.let { if (!it.isRecycled) return it }
        return createBitmap(context).also { cached = it }
    }

    fun createBitmap(context: Context, size: Int = SIZE_PX): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = size.toFloat()
        val h = size.toFloat()

        val top = ContextCompat.getColor(context, R.color.notification_playback_art_top)
        val bottom = ContextCompat.getColor(context, R.color.notification_playback_art_bottom)
        val lane = ContextCompat.getColor(context, R.color.notification_playback_art_lane)
        val wave = ContextCompat.getColor(context, R.color.notification_playback_art_wave)

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w * 0.2f, h, top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, bg)

        val lanePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lane
            strokeWidth = size * 0.0025f
        }
        val laneCount = 6
        for (i in 1 until laneCount) {
            val y = h * (i.toFloat() / laneCount)
            canvas.drawLine(w * 0.06f, y, w * 0.94f, y, lanePaint)
        }

        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = wave
            style = Paint.Style.STROKE
            strokeWidth = size * 0.012f
            strokeCap = Paint.Cap.ROUND
        }
        val path = Path()
        val midY = h * 0.52f
        val amplitude = h * 0.11f
        val steps = 64
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = w * (0.08f + 0.84f * t)
            val y = midY + amplitude * sin(t * PI * 3.2).toFloat() * (0.35f + 0.65f * sin(t * PI * 1.1).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, wavePaint)

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = wave
            alpha = 40
            style = Paint.Style.STROKE
            strokeWidth = size * 0.028f
        }
        canvas.drawPath(path, glowPaint)

        return bitmap
    }

    fun invalidateCache() {
        cached?.recycle()
        cached = null
    }
}
