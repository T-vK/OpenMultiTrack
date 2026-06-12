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

    /** Seconds of live peak history retained in memory (limits max zoom-out). */
    var recordWaveformHistorySec: Float
        get() = prefs.getFloat(KEY_RECORD_WAVEFORM_HISTORY_SEC, 120f)
        set(value) = prefs.edit().putFloat(KEY_RECORD_WAVEFORM_HISTORY_SEC, value).apply()

    var playbackWaveformWindowSec: Float
        get() = prefs.getFloat(KEY_PLAYBACK_WAVEFORM_SEC, 180f)
        set(value) = prefs.edit().putFloat(KEY_PLAYBACK_WAVEFORM_SEC, value).apply()

    var usbDetachDebounceMs: Long
        get() = prefs.getLong(KEY_USB_DEBOUNCE_MS, 400L)
        set(value) = prefs.edit().putLong(KEY_USB_DEBOUNCE_MS, value).apply()

    var recordWaveformNormalized: Boolean
        get() = prefs.getBoolean(
            KEY_RECORD_WAVEFORM_NORMALIZED,
            prefs.getBoolean(KEY_WAVEFORM_NORMALIZED, true),
        )
        set(value) = prefs.edit().putBoolean(KEY_RECORD_WAVEFORM_NORMALIZED, value).apply()

    var playbackWaveformNormalized: Boolean
        get() = prefs.getBoolean(
            KEY_PLAYBACK_WAVEFORM_NORMALIZED,
            prefs.getBoolean(KEY_WAVEFORM_NORMALIZED, false),
        )
        set(value) = prefs.edit().putBoolean(KEY_PLAYBACK_WAVEFORM_NORMALIZED, value).apply()

    var additionalLibraryRoots: List<String>
        get() {
            val raw = prefs.getString(KEY_ADDITIONAL_LIBRARY_ROOTS, null) ?: return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    arr.optString(i).takeIf { it.isNotBlank() }
                }
            }.getOrDefault(emptyList())
        }
        set(value) {
            val arr = JSONArray()
            value.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { arr.put(it) }
            prefs.edit().putString(KEY_ADDITIONAL_LIBRARY_ROOTS, arr.toString()).apply()
        }

    var autoScanRemovableMedia: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SCAN_REMOVABLE_MEDIA, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SCAN_REMOVABLE_MEDIA, value).apply()

    var alwaysIncludeOpenMultiTrackFolders: Boolean
        get() = prefs.getBoolean(KEY_ALWAYS_INCLUDE_OMT_FOLDERS, true)
        set(value) = prefs.edit().putBoolean(KEY_ALWAYS_INCLUDE_OMT_FOLDERS, value).apply()

    var redundantRecordingRoots: List<String>
        get() {
            val raw = prefs.getString(KEY_REDUNDANT_RECORDING_ROOTS, null) ?: return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    arr.optString(i).takeIf { it.isNotBlank() }
                }
            }.getOrDefault(emptyList())
        }
        set(value) {
            val arr = JSONArray()
            value.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { arr.put(it) }
            prefs.edit().putString(KEY_REDUNDANT_RECORDING_ROOTS, arr.toString()).apply()
        }

    var localSpillBufferEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCAL_SPILL_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_LOCAL_SPILL_ENABLED, value).apply()

    var localSpillBufferMinutes: Int
        get() = prefs.getInt(KEY_LOCAL_SPILL_MINUTES, 5).coerceIn(1, 60)
        set(value) = prefs.edit().putInt(KEY_LOCAL_SPILL_MINUTES, value.coerceIn(1, 60)).apply()

    /** Stop writing to a volume when free space drops below this (0 = disabled). */
    var minFreeStorageBytes: Long
        get() = prefs.getLong(KEY_MIN_FREE_STORAGE_BYTES, 0L).coerceAtLeast(0L)
        set(value) = prefs.edit().putLong(KEY_MIN_FREE_STORAGE_BYTES, value.coerceAtLeast(0L)).apply()

    var debugLogging: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_LOG, true)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_LOG, value).apply()

    /** Continuously flush the in-app log buffer to disk for crash recovery. */
    var devLogAutoPersist: Boolean
        get() = prefs.getBoolean(KEY_DEV_LOG_AUTO_PERSIST, true)
        set(value) = prefs.edit().putBoolean(KEY_DEV_LOG_AUTO_PERSIST, value).apply()

    var devLogHideTimestamps: Boolean
        get() = prefs.getBoolean(KEY_DEV_LOG_HIDE_TIMESTAMPS, true)
        set(value) = prefs.edit().putBoolean(KEY_DEV_LOG_HIDE_TIMESTAMPS, value).apply()

    var devLogShowMenuBar: Boolean
        get() = prefs.getBoolean(KEY_DEV_LOG_SHOW_MENU_BAR, false)
        set(value) = prefs.edit().putBoolean(KEY_DEV_LOG_SHOW_MENU_BAR, value).apply()

    var devLogColoredLevels: Boolean
        get() = prefs.getBoolean(KEY_DEV_LOG_COLORED_LEVELS, true)
        set(value) = prefs.edit().putBoolean(KEY_DEV_LOG_COLORED_LEVELS, value).apply()

    var devLogWordWrap: Boolean
        get() = prefs.getBoolean(KEY_DEV_LOG_WORD_WRAP, true)
        set(value) = prefs.edit().putBoolean(KEY_DEV_LOG_WORD_WRAP, value).apply()

    /** Bitmask of enabled levels — see [org.openmultitrack.app.util.DevLogLevelMask]. */
    var devLogLevelFilterMask: Int
        get() = prefs.getInt(KEY_DEV_LOG_LEVEL_FILTER_MASK, org.openmultitrack.app.util.DevLogLevelMask.ALL)
        set(value) = prefs.edit().putInt(KEY_DEV_LOG_LEVEL_FILTER_MASK, value).apply()

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

    var postRecordBehavior: PostRecordBehavior
        get() {
            if (prefs.contains(KEY_POST_RECORD_BEHAVIOR)) {
                return PostRecordBehavior.fromOrdinal(prefs.getInt(KEY_POST_RECORD_BEHAVIOR, 0))
            }
            return if (prefs.getBoolean(KEY_PROMPT_SOUNDCHECK_AFTER_RECORD, true)) {
                PostRecordBehavior.FULL_PROMPT
            } else {
                PostRecordBehavior.NOTHING
            }
        }
        set(value) = prefs.edit().putInt(KEY_POST_RECORD_BEHAVIOR, value.ordinal).apply()

    var showRecordingStorageInfoButton: Boolean
        get() = prefs.getBoolean(KEY_SHOW_RECORDING_STORAGE_INFO, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_RECORDING_STORAGE_INFO, value).apply()

    var autoShowRecordingStorageTooltip: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SHOW_RECORDING_STORAGE_TOOLTIP, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SHOW_RECORDING_STORAGE_TOOLTIP, value).apply()

    var chapterSupportEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHAPTER_SUPPORT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CHAPTER_SUPPORT_ENABLED, value).apply()

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
                    lastHost = obj.optString("lastHost").takeIf { it.isNotBlank() },
                    lastPort = obj.optInt("lastPort").takeIf { it > 0 },
                )
            }
        }.getOrDefault(emptyList())
    }

    fun savePairedRemoteHost(host: RemotePairedHost) {
        val existing = listPairedRemoteHosts().firstOrNull { it.hostId == host.hostId }
        val merged = host.copy(
            lastHost = host.lastHost ?: existing?.lastHost,
            lastPort = host.lastPort ?: existing?.lastPort,
        )
        val updated = listPairedRemoteHosts()
            .filter { it.hostId != merged.hostId } + merged
        val arr = JSONArray()
        updated.forEach { h ->
            arr.put(
                JSONObject().apply {
                    put("hostId", h.hostId)
                    put("displayName", h.displayName)
                    put("pin", h.pin)
                    h.lastHost?.let { put("lastHost", it) }
                    h.lastPort?.let { put("lastPort", it) }
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
                    h.lastHost?.let { put("lastHost", it) }
                    h.lastPort?.let { put("lastPort", it) }
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

    fun routingAutomationForMixer(mixerId: String): MixerRoutingAutomationConfig {
        val json = prefs.getString(KEY_ROUTING_AUTOMATION_BY_MIXER, null) ?: return MixerRoutingAutomationConfig()
        return runCatching {
            val o = JSONObject(json).optJSONObject(mixerId) ?: return MixerRoutingAutomationConfig()
            MixerRoutingAutomationConfig(
                level = RoutingAutomationLevel.entries.getOrElse(o.optInt("level")) {
                    RoutingAutomationLevel.PROMPT
                },
                method = RoutingAutomationMethod.entries.getOrElse(o.optInt("method")) {
                    RoutingAutomationMethod.PER_CHANNEL
                },
                restorePolicy = RoutingRestorePolicy.entries.getOrElse(o.optInt("restorePolicy")) {
                    RoutingRestorePolicy.RESPECT_LIVE
                },
                recordSnapshotSlot = o.optInt("recordSnapshotSlot", 0),
                soundcheckSnapshotSlot = o.optInt("soundcheckSnapshotSlot", 0),
                forceRestoreOnConflict = o.optBoolean("forceRestoreOnConflict", false),
            )
        }.getOrDefault(MixerRoutingAutomationConfig())
    }

    fun setRoutingAutomationForMixer(mixerId: String, config: MixerRoutingAutomationConfig) {
        val root = runCatching {
            JSONObject(prefs.getString(KEY_ROUTING_AUTOMATION_BY_MIXER, "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        root.put(
            mixerId,
            JSONObject()
                .put("level", config.level.ordinal)
                .put("method", config.method.ordinal)
                .put("restorePolicy", config.restorePolicy.ordinal)
                .put("recordSnapshotSlot", config.recordSnapshotSlot)
                .put("soundcheckSnapshotSlot", config.soundcheckSnapshotSlot)
                .put("forceRestoreOnConflict", config.forceRestoreOnConflict),
        )
        prefs.edit().putString(KEY_ROUTING_AUTOMATION_BY_MIXER, root.toString()).apply()
    }

    fun exportJson(): String = prefs.all.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
        """"$k":${if (v is String) "\"$v\"" else v}"""
    }

    companion object {
        private const val PREFS = "omt_settings"
        private const val KEY_STORAGE_ROOT = "storage_root"
        private const val KEY_MONITOR_GAIN = "monitor_gain"
        private const val KEY_RECORD_WAVEFORM_SEC = "record_waveform_sec"
        private const val KEY_RECORD_WAVEFORM_HISTORY_SEC = "record_waveform_history_sec"
        private const val KEY_PLAYBACK_WAVEFORM_SEC = "playback_waveform_sec"
        private const val KEY_USB_DEBOUNCE_MS = "usb_debounce_ms"
        private const val KEY_WAVEFORM_NORMALIZED = "waveform_normalized"
        private const val KEY_RECORD_WAVEFORM_NORMALIZED = "record_waveform_normalized"
        private const val KEY_PLAYBACK_WAVEFORM_NORMALIZED = "playback_waveform_normalized"
        private const val KEY_ADDITIONAL_LIBRARY_ROOTS = "additional_library_roots"
        private const val KEY_AUTO_SCAN_REMOVABLE_MEDIA = "auto_scan_removable_media"
        private const val KEY_ALWAYS_INCLUDE_OMT_FOLDERS = "always_include_omt_folders"
        private const val KEY_REDUNDANT_RECORDING_ROOTS = "redundant_recording_roots"
        private const val KEY_LOCAL_SPILL_ENABLED = "local_spill_enabled"
        private const val KEY_LOCAL_SPILL_MINUTES = "local_spill_minutes"
        private const val KEY_MIN_FREE_STORAGE_BYTES = "min_free_storage_bytes"
        private const val KEY_DEBUG_LOG = "debug_log"
        private const val KEY_DEV_LOG_AUTO_PERSIST = "dev_log_auto_persist"
        private const val KEY_DEV_LOG_HIDE_TIMESTAMPS = "dev_log_hide_timestamps"
        private const val KEY_DEV_LOG_SHOW_MENU_BAR = "dev_log_show_menu_bar"
        private const val KEY_DEV_LOG_COLORED_LEVELS = "dev_log_colored_levels"
        private const val KEY_DEV_LOG_WORD_WRAP = "dev_log_word_wrap"
        private const val KEY_DEV_LOG_LEVEL_FILTER_MASK = "dev_log_level_filter_mask"
        private const val KEY_HIDE_ARM = "hide_arm_button"
        private const val KEY_HIDE_MONITOR = "hide_monitor_button"
        private const val KEY_HIDE_SOLO = "hide_solo_button"
        private const val KEY_HIDE_ROUTING_BADGES = "hide_routing_badges"
        private const val KEY_PROMPT_SOUNDCHECK_AFTER_RECORD = "prompt_soundcheck_after_record"
        private const val KEY_POST_RECORD_BEHAVIOR = "post_record_behavior"
        private const val KEY_SHOW_RECORDING_STORAGE_INFO = "show_recording_storage_info_button"
        private const val KEY_AUTO_SHOW_RECORDING_STORAGE_TOOLTIP = "auto_show_recording_storage_tooltip"
        private const val KEY_CHAPTER_SUPPORT_ENABLED = "chapter_support_enabled"
        private const val KEY_LAST_SELECTED_SOUNDCHECK_BY_MIXER = "last_selected_soundcheck_by_mixer"
        private const val KEY_SHOW_WAVEFORMS = "show_waveforms"
        private const val KEY_SHOW_VU_METERS = "show_vu_meters"
        private const val KEY_STRIP_NUMBER_MODE = "strip_number_mode"
        private const val KEY_STRIP_ICON_MODE = "strip_icon_mode"
        private const val KEY_LAST_ACTIVE_MIXER = "last_active_mixer_id"
        private const val KEY_ACTIVE_RECORDING_MIXER = "active_recording_mixer_id"
        private const val KEY_ACTIVE_RECORDING_DIR = "active_recording_session_dir"
        private const val KEY_APP_MODES_BY_MIXER = "app_modes_by_mixer"
        private const val KEY_ROUTING_AUTOMATION_BY_MIXER = "routing_automation_by_mixer"
        private const val KEY_REMOTE_ROLE = "remote_role"
        private const val KEY_REMOTE_AUTH_TOKEN = "remote_auth_token"
        private const val KEY_REMOTE_HOST_DEVICE_ID = "remote_host_device_id"
        private const val KEY_REMOTE_PAIRING_PIN = "remote_pairing_pin"
        private const val KEY_REMOTE_PAIRED_HOSTS = "remote_paired_hosts"
    }
}
