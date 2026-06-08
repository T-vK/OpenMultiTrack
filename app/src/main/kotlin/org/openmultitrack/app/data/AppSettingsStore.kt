package org.openmultitrack.app.data

import android.content.Context
import org.json.JSONObject
import org.openmultitrack.domain.session.AppMode

class AppSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var storageRootPath: String?
        get() = prefs.getString(KEY_STORAGE_ROOT, null)
        set(value) = prefs.edit().putString(KEY_STORAGE_ROOT, value).apply()

    var monitorGainLinear: Float
        get() = prefs.getFloat(KEY_MONITOR_GAIN, 2.5f)
        set(value) = prefs.edit().putFloat(KEY_MONITOR_GAIN, value.coerceIn(0.5f, 8f)).apply()

    var recordWaveformWindowSec: Float
        get() = prefs.getFloat(KEY_RECORD_WAVEFORM_SEC, 15f)
        set(value) = prefs.edit().putFloat(KEY_RECORD_WAVEFORM_SEC, value).apply()

    var playbackWaveformWindowSec: Float
        get() = prefs.getFloat(KEY_PLAYBACK_WAVEFORM_SEC, 300f)
        set(value) = prefs.edit().putFloat(KEY_PLAYBACK_WAVEFORM_SEC, value).apply()

    var usbDetachDebounceMs: Long
        get() = prefs.getLong(KEY_USB_DEBOUNCE_MS, 400L)
        set(value) = prefs.edit().putLong(KEY_USB_DEBOUNCE_MS, value).apply()

    var waveformNormalized: Boolean
        get() = prefs.getBoolean(KEY_WAVEFORM_NORMALIZED, false)
        set(value) = prefs.edit().putBoolean(KEY_WAVEFORM_NORMALIZED, value).apply()

    var debugLogging: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_LOG, true)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_LOG, value).apply()

    var hideArmButton: Boolean
        get() = prefs.getBoolean(KEY_HIDE_ARM, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_ARM, value).apply()

    var hideMonitorButton: Boolean
        get() = prefs.getBoolean(KEY_HIDE_MONITOR, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_MONITOR, value).apply()

    var hideSoloButton: Boolean
        get() = prefs.getBoolean(KEY_HIDE_SOLO, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_SOLO, value).apply()

    var showWaveforms: Boolean
        get() = prefs.getBoolean(KEY_SHOW_WAVEFORMS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_WAVEFORMS, value).apply()

    var stripNumberMode: StripNumberMode
        get() = StripNumberMode.entries.getOrElse(prefs.getInt(KEY_STRIP_NUMBER_MODE, 0)) { StripNumberMode.BOTH }
        set(value) = prefs.edit().putInt(KEY_STRIP_NUMBER_MODE, value.ordinal).apply()

    var stripIconMode: StripIconMode
        get() = StripIconMode.entries.getOrElse(prefs.getInt(KEY_STRIP_ICON_MODE, 0)) { StripIconMode.SHOW }
        set(value) = prefs.edit().putInt(KEY_STRIP_ICON_MODE, value.ordinal).apply()

    var lastActiveMixerId: String?
        get() = prefs.getString(KEY_LAST_ACTIVE_MIXER, null)
        set(value) = prefs.edit().putString(KEY_LAST_ACTIVE_MIXER, value).apply()

    fun appModeForMixer(mixerId: String): AppMode {
        val json = prefs.getString(KEY_APP_MODES_BY_MIXER, null) ?: return AppMode.MULTITRACK_RECORD
        return runCatching {
            val ordinal = JSONObject(json).optInt(mixerId, AppMode.MULTITRACK_RECORD.ordinal)
            AppMode.entries.getOrElse(ordinal) { AppMode.MULTITRACK_RECORD }
        }.getOrDefault(AppMode.MULTITRACK_RECORD)
    }

    fun setAppModeForMixer(mixerId: String, mode: AppMode) {
        val root = runCatching {
            JSONObject(prefs.getString(KEY_APP_MODES_BY_MIXER, "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        root.put(mixerId, mode.ordinal)
        prefs.edit().putString(KEY_APP_MODES_BY_MIXER, root.toString()).apply()
    }

    fun clearAppModeForMixer(mixerId: String) {
        val raw = prefs.getString(KEY_APP_MODES_BY_MIXER, null) ?: return
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        root.remove(mixerId)
        prefs.edit().putString(KEY_APP_MODES_BY_MIXER, root.toString()).apply()
    }

    fun exportJson(): String = prefs.all.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
        """"$k":${if (v is String) "\"$v\"" else v}"""
    }

    companion object {
        private const val PREFS = "omt_settings"
        private const val KEY_STORAGE_ROOT = "storage_root"
        private const val KEY_MONITOR_GAIN = "monitor_gain"
        private const val KEY_RECORD_WAVEFORM_SEC = "record_waveform_sec"
        private const val KEY_PLAYBACK_WAVEFORM_SEC = "playback_waveform_sec"
        private const val KEY_USB_DEBOUNCE_MS = "usb_debounce_ms"
        private const val KEY_WAVEFORM_NORMALIZED = "waveform_normalized"
        private const val KEY_DEBUG_LOG = "debug_log"
        private const val KEY_HIDE_ARM = "hide_arm_button"
        private const val KEY_HIDE_MONITOR = "hide_monitor_button"
        private const val KEY_HIDE_SOLO = "hide_solo_button"
        private const val KEY_SHOW_WAVEFORMS = "show_waveforms"
        private const val KEY_STRIP_NUMBER_MODE = "strip_number_mode"
        private const val KEY_STRIP_ICON_MODE = "strip_icon_mode"
        private const val KEY_LAST_ACTIVE_MIXER = "last_active_mixer_id"
        private const val KEY_APP_MODES_BY_MIXER = "app_modes_by_mixer"
    }
}
