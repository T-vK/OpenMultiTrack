package org.openmultitrack.app.data

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import org.openmultitrack.domain.remote.RemotePairedHost
import org.openmultitrack.domain.remote.RemotePairing
import org.openmultitrack.domain.remote.RemoteRole
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
        get() = prefs.getFloat(KEY_PLAYBACK_WAVEFORM_SEC, 180f)
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

    var hideRoutingBadges: Boolean
        get() = prefs.getBoolean(KEY_HIDE_ROUTING_BADGES, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_ROUTING_BADGES, value).apply()

    var promptLoadSoundcheckAfterRecord: Boolean
        get() = prefs.getBoolean(KEY_PROMPT_SOUNDCHECK_AFTER_RECORD, true)
        set(value) = prefs.edit().putBoolean(KEY_PROMPT_SOUNDCHECK_AFTER_RECORD, value).apply()

    var showRecordingStorageInfoButton: Boolean
        get() = prefs.getBoolean(KEY_SHOW_RECORDING_STORAGE_INFO, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_RECORDING_STORAGE_INFO, value).apply()

    var autoShowRecordingStorageTooltip: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SHOW_RECORDING_STORAGE_TOOLTIP, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SHOW_RECORDING_STORAGE_TOOLTIP, value).apply()

    fun lastSelectedSoundcheckSession(mixerId: String): String? {
        val json = prefs.getString(KEY_LAST_SELECTED_SOUNDCHECK_BY_MIXER, null) ?: return null
        return runCatching {
            JSONObject(json).optString(mixerId).takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun setLastSelectedSoundcheckSession(mixerId: String, sessionDir: String) {
        val root = runCatching {
            JSONObject(prefs.getString(KEY_LAST_SELECTED_SOUNDCHECK_BY_MIXER, "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        root.put(mixerId, sessionDir)
        prefs.edit().putString(KEY_LAST_SELECTED_SOUNDCHECK_BY_MIXER, root.toString()).apply()
    }

    var showWaveforms: Boolean
        get() = prefs.getBoolean(KEY_SHOW_WAVEFORMS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_WAVEFORMS, value).apply()

    var showVuMeters: Boolean
        get() = prefs.getBoolean(KEY_SHOW_VU_METERS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_VU_METERS, value).apply()

    var stripNumberMode: StripNumberMode
        get() = StripNumberMode.entries.getOrElse(prefs.getInt(KEY_STRIP_NUMBER_MODE, 1)) {
            StripNumberMode.HIDE_WHEN_LABELED
        }
        set(value) = prefs.edit().putInt(KEY_STRIP_NUMBER_MODE, value.ordinal).apply()

    var stripIconMode: StripIconMode
        get() = StripIconMode.entries.getOrElse(prefs.getInt(KEY_STRIP_ICON_MODE, 0)) { StripIconMode.SHOW }
        set(value) = prefs.edit().putInt(KEY_STRIP_ICON_MODE, value.ordinal).apply()

    var lastActiveMixerId: String?
        get() = prefs.getString(KEY_LAST_ACTIVE_MIXER, null)
        set(value) = prefs.edit().putString(KEY_LAST_ACTIVE_MIXER, value).apply()

    var remoteRole: RemoteRole
        get() = RemoteRole.entries.getOrElse(prefs.getInt(KEY_REMOTE_ROLE, 0)) { RemoteRole.OFF }
        set(value) = prefs.edit().putInt(KEY_REMOTE_ROLE, value.ordinal).apply()

    var remoteAuthToken: String?
        get() = prefs.getString(KEY_REMOTE_AUTH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REMOTE_AUTH_TOKEN, value).apply()

    var remoteHostDeviceId: String
        get() = RemotePairing.ensureHostDeviceId(prefs.getString(KEY_REMOTE_HOST_DEVICE_ID, null))
        set(value) = prefs.edit().putString(KEY_REMOTE_HOST_DEVICE_ID, value).apply()

    var remotePairingPin: String
        get() {
            val existing = prefs.getString(KEY_REMOTE_PAIRING_PIN, null)
            if (existing != null) return existing
            val pin = RemotePairing.generatePin()
            prefs.edit().putString(KEY_REMOTE_PAIRING_PIN, pin).apply()
            return pin
        }
        set(value) = prefs.edit().putString(KEY_REMOTE_PAIRING_PIN, value).apply()

    fun listPairedRemoteHosts(): List<RemotePairedHost> {
        val raw = prefs.getString(KEY_REMOTE_PAIRED_HOSTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                RemotePairedHost(
                    hostId = obj.getString("hostId"),
                    displayName = obj.optString("displayName", "OpenMultiTrack"),
                    pin = obj.getString("pin"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun savePairedRemoteHost(host: RemotePairedHost) {
        val updated = listPairedRemoteHosts()
            .filter { it.hostId != host.hostId } + host
        val arr = JSONArray()
        updated.forEach { h ->
            arr.put(
                JSONObject().apply {
                    put("hostId", h.hostId)
                    put("displayName", h.displayName)
                    put("pin", h.pin)
                },
            )
        }
        prefs.edit().putString(KEY_REMOTE_PAIRED_HOSTS, arr.toString()).apply()
    }

    fun removePairedRemoteHost(hostId: String) {
        val updated = listPairedRemoteHosts().filter { it.hostId != hostId }
        val arr = JSONArray()
        updated.forEach { h ->
            arr.put(
                JSONObject().apply {
                    put("hostId", h.hostId)
                    put("displayName", h.displayName)
                    put("pin", h.pin)
                },
            )
        }
        prefs.edit().putString(KEY_REMOTE_PAIRED_HOSTS, arr.toString()).apply()
    }

    fun pinForPairedHost(hostId: String): String? =
        listPairedRemoteHosts().firstOrNull { it.hostId == hostId }?.pin

    val activeRecordingMixerId: String?
        get() = prefs.getString(KEY_ACTIVE_RECORDING_MIXER, null)

    val activeRecordingSessionDir: String?
        get() = prefs.getString(KEY_ACTIVE_RECORDING_DIR, null)

    fun setActiveRecording(mixerId: String, sessionDir: String) {
        prefs.edit()
            .putString(KEY_ACTIVE_RECORDING_MIXER, mixerId)
            .putString(KEY_ACTIVE_RECORDING_DIR, sessionDir)
            .commit()
    }

    fun clearActiveRecording() {
        prefs.edit()
            .remove(KEY_ACTIVE_RECORDING_MIXER)
            .remove(KEY_ACTIVE_RECORDING_DIR)
            .commit()
    }

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
        private const val KEY_HIDE_ROUTING_BADGES = "hide_routing_badges"
        private const val KEY_PROMPT_SOUNDCHECK_AFTER_RECORD = "prompt_soundcheck_after_record"
        private const val KEY_SHOW_RECORDING_STORAGE_INFO = "show_recording_storage_info_button"
        private const val KEY_AUTO_SHOW_RECORDING_STORAGE_TOOLTIP = "auto_show_recording_storage_tooltip"
        private const val KEY_LAST_SELECTED_SOUNDCHECK_BY_MIXER = "last_selected_soundcheck_by_mixer"
        private const val KEY_SHOW_WAVEFORMS = "show_waveforms"
        private const val KEY_SHOW_VU_METERS = "show_vu_meters"
        private const val KEY_STRIP_NUMBER_MODE = "strip_number_mode"
        private const val KEY_STRIP_ICON_MODE = "strip_icon_mode"
        private const val KEY_LAST_ACTIVE_MIXER = "last_active_mixer_id"
        private const val KEY_ACTIVE_RECORDING_MIXER = "active_recording_mixer_id"
        private const val KEY_ACTIVE_RECORDING_DIR = "active_recording_session_dir"
        private const val KEY_APP_MODES_BY_MIXER = "app_modes_by_mixer"
        private const val KEY_REMOTE_ROLE = "remote_role"
        private const val KEY_REMOTE_AUTH_TOKEN = "remote_auth_token"
        private const val KEY_REMOTE_HOST_DEVICE_ID = "remote_host_device_id"
        private const val KEY_REMOTE_PAIRING_PIN = "remote_pairing_pin"
        private const val KEY_REMOTE_PAIRED_HOSTS = "remote_paired_hosts"
    }
}
