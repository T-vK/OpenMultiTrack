package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.max
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openmultitrack.app.DawUiState
import org.openmultitrack.app.hasNewerRecordingThanSelected
import org.openmultitrack.app.device.PrerequisiteKind
import org.openmultitrack.app.scribble.Flow8BleScribbleImporter
import org.openmultitrack.app.scribble.ScribbleImportSupport
import org.openmultitrack.app.data.StripIconMode
import org.openmultitrack.app.data.StripNumberMode
import org.openmultitrack.app.ui.settings.SettingsScreen
import org.openmultitrack.app.ui.settings.SettingsUiState
import org.openmultitrack.app.audio.LiveWaveformSnapshot
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.mixer.MixerRoutingConfig
import org.openmultitrack.domain.remote.RemoteConnectionState
import org.openmultitrack.domain.remote.RemoteRole
import org.openmultitrack.domain.remote.RemoteProtocol
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.TransportState
import org.openmultitrack.usb.MixerUsbChannelCounts
import org.openmultitrack.domain.session.isPlaybackMode
import org.openmultitrack.remote.RemoteDiscoveredHost
import org.openmultitrack.usb.LabeledAudioDevice

private val RecordRed = Color(0xFFE53935)
private val MonitorBlue = Color(0xFF1E88E5)
private val SoloAmber = Color(0xFFFFB300)
private val MinStripHeight = 44.dp
private val ScrollStripHeight = 72.dp
private val ToolbarShape = RoundedCornerShape(8.dp)
private val ToolbarControlHeight = 36.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DawMainScreen(
    state: DawUiState,
    monitorGain: Float,
    recordWaveformWindowSec: Float,
    recordWaveformHistorySec: Float,
    recordWaveformNormalized: Boolean,
    playbackWaveformNormalized: Boolean,
    onAddMixer: () -> Unit,
    onSelectMixer: (String) -> Unit,
    onRemoveMixer: (String) -> Unit,
    onAddMixerDevice: (UsbAudioDeviceDescriptor) -> Unit,
    onAddVirtualDemoMixer: () -> Unit,
    onDismissAddMixer: () -> Unit,
    onToggleArm: (String, Int) -> Unit,
    onToggleMonitor: (String, Int) -> Unit,
    onToggleSolo: (String, Int) -> Unit,
    onToggleMute: (String, Int) -> Unit = { _, _ -> },
    onSetMonitorOutput: (String, Int) -> Unit,
    onStartMonitor: (String) -> Unit,
    onStopMonitor: (String) -> Unit,
    onStartRecord: (String) -> Unit,
    onStopRecord: (String) -> Unit,
    onSetAppMode: (String, AppMode) -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onOpenLog: () -> Unit,
    onCloseLog: () -> Unit,
    onMonitorGainChange: (Float) -> Unit,
    onLoadScribbleStrip: (String) -> Unit,
    playbackWaveformWindowSec: Float,
    onSelectSoundcheckSession: (String, String) -> Unit,
    onToggleSoundcheckPlayback: (String) -> Unit,
    onStopSoundcheck: (String) -> Unit,
    onSeekSoundcheck: (String, Float) -> Unit,
    onPanSoundcheckView: (String, Float) -> Unit,
    onZoomSoundcheckView: (String, Float, Float) -> Unit,
    onSetSoundcheckView: (String, Float, Float) -> Unit,
    onZoomRecordView: (String, Float, Float) -> Unit = { _, _, _ -> },
    onSetRecordView: (String, Float, Float) -> Unit = { _, _, _ -> },
    onSetSoundcheckLoopRegion: (String, Float, Float) -> Unit,
    onToggleSoundcheckLoop: (String) -> Unit,
    onSetSoundcheckLoopIn: (String) -> Unit,
    onSetSoundcheckLoopOut: (String) -> Unit,
    onOpenSessionPicker: () -> Unit = {},
    onConfirmFlow8PairingImport: () -> Unit,
    onDismissFlow8PairingDialog: () -> Unit,
    onHideArmChange: (Boolean) -> Unit,
    onHideMonitorChange: (Boolean) -> Unit,
    onHideSoloChange: (Boolean) -> Unit,
    onHideRoutingBadgesChange: (Boolean) -> Unit,
    onShowWaveformsChange: (Boolean) -> Unit,
    onShowVuMetersChange: (Boolean) -> Unit,
    onStripNumberModeChange: (StripNumberMode) -> Unit,
    onStripIconModeChange: (StripIconMode) -> Unit,
    onRecordWaveformWindowChange: (Float) -> Unit,
    onRecordWaveformHistoryChange: (Float) -> Unit,
    onPlaybackWaveformWindowChange: (Float) -> Unit,
    onDismissStatusToast: () -> Unit,
    onFinalizeIncompleteRecording: (String) -> Unit,
    onOpenMixerPicker: () -> Unit = {},
    onOpenMixerSettings: (String) -> Unit = {},
    onCloseMixerSettings: () -> Unit = {},
    onSaveMixerRouting: (String, MixerRoutingConfig) -> Unit = { _, _ -> },
    onOpenRemoteControl: () -> Unit = {},
    onCloseRemoteControl: () -> Unit = {},
    onEnterRemoteClientMode: () -> Unit = {},
    onDiscoverRemoteHosts: () -> Unit = {},
    onConnectRemoteHost: (RemoteDiscoveredHost) -> Unit = {},
    onConnectRemoteManual: (String, String) -> Unit = { _, _ -> },
    onDisconnectRemote: () -> Unit = {},
    onExitRemoteMode: () -> Unit = {},
    onScanRemoteQr: () -> Unit = {},
    onEnableRemoteHosting: () -> Unit = {},
    onStopRemoteHosting: () -> Unit = {},
    onUnpairRemoteHost: (String) -> Unit = {},
    mixerRoutingById: Map<String, MixerRoutingConfig> = emptyMap(),
    showMixerPicker: Boolean = false,
    onCloseMixerPicker: () -> Unit = {},
    mixerSettingsMixerId: String? = null,
    showRemoteControlSheet: Boolean = false,
    remotePairingUri: String? = null,
    remotePairingPin: String? = null,
    onUpdateChannelInput: (String, Int, Int) -> Unit = { _, _, _ -> },
    onUpdateChannelOutput: (String, Int, Int) -> Unit = { _, _, _ -> },
    onSetChannelHidden: (String, Int, Boolean, Boolean) -> Unit = { _, _, _, _ -> },
    onPrerequisiteAction: (PrerequisiteKind) -> Unit = {},
    onLoadRecordingIntoSoundcheck: (String, String) -> Unit = { _, _ -> },
    onLoadRecordingIntoSimplePlay: (String, String) -> Unit = { _, _ -> },
    onDismissSoundcheckLoadPrompt: () -> Unit = {},
    onPostRecordBehaviorChange: (org.openmultitrack.app.data.PostRecordBehavior) -> Unit = {},
    onShowRecordingStorageInfoButtonChange: (Boolean) -> Unit = {},
    onAutoShowRecordingStorageTooltipChange: (Boolean) -> Unit = {},
    onRecordWaveformNormalizedChange: (Boolean) -> Unit = {},
    onPlaybackWaveformNormalizedChange: (Boolean) -> Unit = {},
    onSetStorageRootPath: (String?) -> Unit = {},
    onAddAdditionalLibraryRoot: (String) -> Unit = {},
    onRemoveAdditionalLibraryRoot: (String) -> Unit = {},
    onAutoScanRemovableMediaChange: (Boolean) -> Unit = {},
    onAddRedundantRecordingRoot: (String) -> Unit = {},
    onRemoveRedundantRecordingRoot: (String) -> Unit = {},
    onAlwaysIncludeOpenMultiTrackFoldersChange: (Boolean) -> Unit = {},
    onLocalSpillBufferEnabledChange: (Boolean) -> Unit = {},
    onLocalSpillBufferMinutesChange: (Int) -> Unit = {},
    onMinFreeStorageBytesChange: (Long) -> Unit = {},
    onOpenBatterySettings: () -> Unit = {},
    onRoutingAutomationConfigChange: (org.openmultitrack.app.data.MixerRoutingAutomationConfig) -> Unit = {},
    onOpenInputSources: () -> Unit = {},
    onCloseInputSources: () -> Unit = {},
    onRefreshInputSources: () -> Unit = {},
    onConfirmRoutingApply: () -> Unit = {},
    onCancelRoutingApply: () -> Unit = {},
    onConfirmRoutingRestore: () -> Unit = {},
    onCancelRoutingRestore: () -> Unit = {},
    onRenameSoundcheckSession: (String, String, String) -> Unit = { _, _, _ -> },
    onDeleteSoundcheckSession: (String, String) -> Unit = { _, _ -> },
    lastSelectedSoundcheckSession: (String) -> String? = { null },
    onCloseSessionPicker: () -> Unit = {},
    onChapterSupportEnabledChange: (Boolean) -> Unit = {},
    onPreviousTrackmark: (String) -> Unit = {},
    onNextTrackmark: (String) -> Unit = {},
    onAddTrackmark: (String, String, Float) -> Unit = { _, _, _ -> },
) {
    val activeId = state.activeMixerId
    val session = activeId?.let { state.sessionByMixer[it] }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    var storageTooltipOpen by remember { mutableStateOf(false) }
    var wasRecording by remember { mutableStateOf(false) }
    var playbackStripOverlay by remember { mutableStateOf<Int?>(null) }
    var showAddTrackmarkDialog by remember { mutableStateOf(false) }
    var showTrackmarkListDialog by remember { mutableStateOf(false) }
    val isRemoteClient = state.remoteRole == RemoteRole.CLIENT &&
        state.remoteConnectionState == RemoteConnectionState.CONNECTED
    val isRemoteHost = state.remoteRole == RemoteRole.HOST
    val isRemoteSyncing = state.remoteRole == RemoteRole.CLIENT &&
        (state.remoteConnectionState == RemoteConnectionState.CONNECTING ||
            state.remoteConnectionState == RemoteConnectionState.DISCOVERING) &&
        state.mixers.isEmpty()

    val activeProfile = activeId?.let { id -> state.mixers.firstOrNull { it.id == id } }
    val supportsOscRouting = activeProfile?.let {
        org.openmultitrack.app.scribble.ScribbleImportSupport.supportsOsc(it) &&
            !it.oscHost.isNullOrBlank()
    } == true
    val activeRouting = activeId?.let { mixerRoutingById[it] } ?: MixerRoutingConfig()
    val isRecording = session?.isRecording == true
    LaunchedEffect(isRecording, state.autoShowRecordingStorageTooltip, state.showRecordingStorageInfoButton) {
        if (isRecording && !wasRecording && state.autoShowRecordingStorageTooltip && state.showRecordingStorageInfoButton) {
            storageTooltipOpen = true
        }
        wasRecording = isRecording
    }

    if (state.showLogViewer) {
        LogViewerScreen(onDismiss = onCloseLog)
        return
    }

    if (state.showSettings) {
        SettingsScreen(
            state = SettingsUiState(
                hideArmIcon = state.hideArmButton,
                hideMonitorIcon = state.hideMonitorButton,
                hideSoloIcon = state.hideSoloButton,
                hideRoutingBadges = state.hideRoutingBadges,
                showWaveforms = state.showWaveforms,
                showVuMeters = state.showVuMeters,
                recordWaveformWindowSec = recordWaveformWindowSec,
                recordWaveformHistorySec = recordWaveformHistorySec,
                playbackWaveformWindowSec = playbackWaveformWindowSec,
                stripNumberMode = state.stripNumberMode,
                stripIconMode = state.stripIconMode,
                postRecordBehavior = state.postRecordBehavior,
                showRecordingStorageInfoButton = state.showRecordingStorageInfoButton,
                autoShowRecordingStorageTooltip = state.autoShowRecordingStorageTooltip,
                recordWaveformNormalized = state.recordWaveformNormalized,
                playbackWaveformNormalized = state.playbackWaveformNormalized,
                storageRootPath = state.storageRootPath,
                effectiveStorageRootPath = state.effectiveStorageRootPath,
                additionalLibraryRoots = state.additionalLibraryRoots,
                autoScanRemovableMedia = state.autoScanRemovableMedia,
                storageVolumeOptions = state.storageVolumeOptions,
                redundantRecordingRoots = state.redundantRecordingRoots,
                alwaysIncludeOpenMultiTrackFolders = state.alwaysIncludeOpenMultiTrackFolders,
                localSpillBufferEnabled = state.localSpillBufferEnabled,
                localSpillBufferMinutes = state.localSpillBufferMinutes,
                minFreeStorageBytes = state.minFreeStorageBytes,
                batteryOptimizationIgnored = state.batteryOptimizationIgnored,
                chapterSupportEnabled = state.chapterSupportEnabled,
                showOscRoutingSettings = supportsOscRouting,
                routingAutomationConfig = state.routingAutomationConfig,
            ),
            monitorGain = monitorGain,
            onMonitorGainChange = onMonitorGainChange,
            onDismiss = onCloseSettings,
            onHideArmChange = onHideArmChange,
            onHideMonitorChange = onHideMonitorChange,
            onHideSoloChange = onHideSoloChange,
            onHideRoutingBadgesChange = onHideRoutingBadgesChange,
            onShowWaveformsChange = onShowWaveformsChange,
            onShowVuMetersChange = onShowVuMetersChange,
            onRecordWaveformWindowChange = onRecordWaveformWindowChange,
            onRecordWaveformHistoryChange = onRecordWaveformHistoryChange,
            onPlaybackWaveformWindowChange = onPlaybackWaveformWindowChange,
            onStripNumberModeChange = onStripNumberModeChange,
            onStripIconModeChange = onStripIconModeChange,
            onPostRecordBehaviorChange = onPostRecordBehaviorChange,
            onShowRecordingStorageInfoButtonChange = onShowRecordingStorageInfoButtonChange,
            onAutoShowRecordingStorageTooltipChange = onAutoShowRecordingStorageTooltipChange,
            onRecordWaveformNormalizedChange = onRecordWaveformNormalizedChange,
            onPlaybackWaveformNormalizedChange = onPlaybackWaveformNormalizedChange,
            onSetStorageRootPath = onSetStorageRootPath,
            onAddAdditionalLibraryRoot = onAddAdditionalLibraryRoot,
            onRemoveAdditionalLibraryRoot = onRemoveAdditionalLibraryRoot,
            onAutoScanRemovableMediaChange = onAutoScanRemovableMediaChange,
            onAddRedundantRecordingRoot = onAddRedundantRecordingRoot,
            onRemoveRedundantRecordingRoot = onRemoveRedundantRecordingRoot,
            onAlwaysIncludeOpenMultiTrackFoldersChange = onAlwaysIncludeOpenMultiTrackFoldersChange,
            onLocalSpillBufferEnabledChange = onLocalSpillBufferEnabledChange,
            onLocalSpillBufferMinutesChange = onLocalSpillBufferMinutesChange,
            onMinFreeStorageBytesChange = onMinFreeStorageBytesChange,
            onOpenBatterySettings = onOpenBatterySettings,
            onChapterSupportEnabledChange = onChapterSupportEnabledChange,
            onRoutingAutomationConfigChange = { config ->
                state.activeMixerId?.let { id ->
                    onRoutingAutomationConfigChange(config)
                }
            },
        )
        return
    }

    mixerSettingsMixerId?.let { settingsMixerId ->
        val profile = state.mixers.firstOrNull { it.id == settingsMixerId }
        val sessionForSettings = state.sessionByMixer[settingsMixerId]
        if (profile != null) {
            MixerSettingsScreen(
                mixerName = profile.displayName,
                channelCount = sessionForSettings?.channelStrips?.size
                    ?: profile.channelStrips.size,
                usbChannelCount = sessionForSettings?.captureChannelCount ?: 0,
                usbPlaybackChannelCount = MixerUsbChannelCounts.playbackChannelsForUi(
                    profile = profile,
                    sessionPlaybackCount = sessionForSettings?.playbackChannelCount ?: 0,
                    probe = sessionForSettings?.probe,
                ),
                strips = sessionForSettings?.channelStrips ?: profile.channelStrips,
                config = mixerRoutingById[settingsMixerId] ?: MixerRoutingConfig(),
                appMode = sessionForSettings?.appMode,
                isMonitoring = sessionForSettings?.isMonitoring == true,
                monitorEnabled = sessionForSettings?.probe != null,
                outputDevices = state.outputDevices,
                onStartMonitor = { onStartMonitor(settingsMixerId) },
                onStopMonitor = { onStopMonitor(settingsMixerId) },
                onSetMonitorOutput = { onSetMonitorOutput(settingsMixerId, it) },
                onDismiss = onCloseMixerSettings,
                onSave = { onSaveMixerRouting(settingsMixerId, it) },
            )
            return
        }
    }

    if (state.showSessionPicker) {
        val pickerSession = session
        val pickerMixerId = activeId
        if (pickerSession != null && pickerMixerId != null) {
            SoundcheckSessionPickerScreen(
                sessions = pickerSession.soundcheckSessions,
                selectedDir = pickerSession.selectedSoundcheckDir,
                onDismiss = onCloseSessionPicker,
                onSelectSession = { dir ->
                    onSelectSoundcheckSession(pickerMixerId, dir)
                },
                onRenameSession = { dir, title ->
                    onRenameSoundcheckSession(pickerMixerId, dir, title)
                },
                onDeleteSession = { dir ->
                    onDeleteSoundcheckSession(pickerMixerId, dir)
                },
            )
            return
        }
    }

    if (showMixerPicker) {
        MixerPickerScreen(
            mixers = state.mixers,
            activeMixerId = state.activeMixerId,
            onDismiss = onCloseMixerPicker,
            onSelectMixer = onSelectMixer,
            onAddNewDevice = onAddMixer,
            onLoadChannelNames = { mixerId ->
                onCloseMixerPicker()
                onLoadScribbleStrip(mixerId)
            },
            onRemoveMixer = onRemoveMixer,
        )
        return
    }

    if (showRemoteControlSheet) {
        RemoteControlScreen(
            role = state.remoteRole,
            connectionState = state.remoteConnectionState,
            hostName = state.remoteHostName,
            connectedHost = state.remoteConnectedHost,
            localHostIp = state.remoteLocalIp,
            pairingUri = remotePairingUri,
            pairingPin = remotePairingPin,
            discoveredHosts = state.remoteDiscoveredHosts,
            pairedHosts = state.remotePairedHosts,
            errorMessage = state.remoteError,
            onDismiss = onCloseRemoteControl,
            onEnterRemoteMode = onEnterRemoteClientMode,
            onDisconnect = onDisconnectRemote,
            onScanLan = onDiscoverRemoteHosts,
            onConnectHost = onConnectRemoteHost,
            onConnectManual = onConnectRemoteManual,
            onScanQr = onScanRemoteQr,
            onEnableHosting = onEnableRemoteHosting,
            onStopHosting = onStopRemoteHosting,
            onUnpairHost = onUnpairRemoteHost,
            connectedClientCount = state.remoteConnectedClientCount,
        )
        return
    }

    val showOpenSessionHint = activeId?.let { mixerId ->
        session?.let { s ->
            hasNewerRecordingThanSelected(
                s.soundcheckSessions,
                lastSelectedSoundcheckSession(mixerId),
            )
        }
    } == true

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val toolbarLayout = computeToolbarLayout(
            barWidth = maxWidth,
            appMode = session?.appMode,
            showRecordingStorageInfoButton = state.showRecordingStorageInfoButton,
        )
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DawNavigationDrawer(
                    drawerState = drawerState,
                    layout = toolbarLayout,
                    appMode = session?.appMode,
                    session = session,
                    isRemoteClient = isRemoteClient,
                    isRemoteHost = isRemoteHost,
                    remoteConnectedClientCount = state.remoteConnectedClientCount,
                    remoteConnectionState = state.remoteConnectionState,
                    showOpenSessionHint = showOpenSessionHint,
                    showRecordingStorageInfoButton = state.showRecordingStorageInfoButton,
                    mixerSettingsEnabled = activeId != null,
                    onOpenMixerSettings = { activeId?.let(onOpenMixerSettings) },
                    onSetAppMode = { mode -> activeId?.let { id -> onSetAppMode(id, mode) } },
                    onOpenSessionPicker = onOpenSessionPicker,
                    onOpenRemoteControl = onOpenRemoteControl,
                    onOpenSettings = onOpenSettings,
                    onStorageTooltipOpenChange = { storageTooltipOpen = it },
                    onOpenLog = onOpenLog,
                    onOpenInputSources = onOpenInputSources,
                    showInputSourcesMenu = supportsOscRouting,
                )
            },
        ) {
            Scaffold(
                topBar = {
                    Column {
                        DawTopBar(
                            layout = toolbarLayout,
                            activeMixer = activeProfile,
                            appMode = session?.appMode,
                            session = session,
                            showMixerCluster = state.mixers.isNotEmpty(),
                            isRemoteClient = isRemoteClient,
                            remoteHostLabel = state.remoteHostName ?: state.remoteConnectedHost,
                            remoteConnectedClientCount = state.remoteConnectedClientCount,
                            isRemoteHost = isRemoteHost,
                            remoteConnectionState = state.remoteConnectionState,
                            showOpenSessionHint = showOpenSessionHint,
                            onExitRemoteMode = onExitRemoteMode,
                            onOpenMixerPicker = onOpenMixerPicker,
                            onOpenMixerSettings = { activeId?.let(onOpenMixerSettings) },
                            onSetAppMode = { mode ->
                                activeId?.let { id -> onSetAppMode(id, mode) }
                            },
                            onStartRecord = { activeId?.let(onStartRecord) },
                            onStopRecord = { activeId?.let(onStopRecord) },
                            onToggleSoundcheckPlayback = { activeId?.let(onToggleSoundcheckPlayback) },
                            onStopSoundcheck = { activeId?.let(onStopSoundcheck) },
                            onToggleSoundcheckLoop = { activeId?.let(onToggleSoundcheckLoop) },
                            onSetSoundcheckLoopIn = { activeId?.let(onSetSoundcheckLoopIn) },
                            onSetSoundcheckLoopOut = { activeId?.let(onSetSoundcheckLoopOut) },
                            showRecordingStorageInfoButton = state.showRecordingStorageInfoButton,
                            storageTooltipOpen = storageTooltipOpen,
                            onStorageTooltipOpenChange = { storageTooltipOpen = it },
                            chaptersEnabled = state.chapterSupportEnabled,
                            onPreviousTrackmark = { activeId?.let(onPreviousTrackmark) },
                            onNextTrackmark = { activeId?.let(onNextTrackmark) },
                            onAddTrackmark = { showAddTrackmarkDialog = true },
                            onShowTrackmarkList = { showTrackmarkListDialog = true },
                            onOpenSessionPicker = onOpenSessionPicker,
                            onOpenSettings = onOpenSettings,
                            onOpenRemoteControl = onOpenRemoteControl,
                            onOpenMenu = { drawerScope.launch { drawerState.open() } },
                        )
                    }
                },
            ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .then(
                    if (storageTooltipOpen) {
                        Modifier.pointerInput(storageTooltipOpen) {
                            detectTapGestures { storageTooltipOpen = false }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.prerequisites.isNotEmpty() && !isRemoteClient) {
                    PrerequisiteBanners(
                        items = state.prerequisites,
                        onAction = onPrerequisiteAction,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                if (isRemoteSyncing) {
                    RemoteSyncingPrompt(
                        hostLabel = state.remoteHostName ?: state.remoteConnectedHost,
                        errorMessage = state.remoteError,
                    )
                } else if (state.mixers.isEmpty()) {
                    WelcomeEmptyState(
                        pairedHostName = state.remotePairedHosts.firstOrNull()?.displayName,
                        onAddMixer = onAddMixer,
                        onScanRemoteQr = onScanRemoteQr,
                        onReconnectRemote = onEnterRemoteClientMode,
                        onOpenRemoteControl = onOpenRemoteControl,
                    )
                } else {
                    activeId?.let { id ->
                        val showInterrupted = state.interruptedRecordings[id] != null &&
                            session?.isRecording != true
                        if (showInterrupted) {
                            IncompleteRecordingBanner(
                                message = state.interruptedRecordingRecovery[id]
                                    ?: "Recording was interrupted. Resuming automatically…",
                                onStopAndKeep = { onFinalizeIncompleteRecording(id) },
                            )
                        }
                    }
                    session?.warningMessage?.let { WarningBanner(it) }
                    when {
                        session?.appMode?.isPlaybackMode == true -> session?.let { s ->
                            SoundcheckPanel(
                                session = s,
                                playbackChannelCount = MixerUsbChannelCounts.playbackChannelsForUi(
                                    profile = activeProfile ?: state.mixers.first { it.id == activeId },
                                    sessionPlaybackCount = s.playbackChannelCount,
                                    probe = s.probe,
                                ),
                                showTrackmarks = state.chapterSupportEnabled,
                                routing = activeRouting,
                                normalized = playbackWaveformNormalized,
                                showWaveforms = state.showWaveforms,
                                numberMode = state.stripNumberMode,
                                iconMode = state.stripIconMode,
                                hideArm = state.hideArmButton,
                                hideMonitor = state.hideMonitorButton,
                                hideSolo = state.hideSoloButton,
                                hideRoutingBadges = state.hideRoutingBadges ||
                                    s.appMode == AppMode.SIMPLE_PLAY,
                                onSeek = { onSeekSoundcheck(activeId!!, it) },
                                onPanView = { onPanSoundcheckView(activeId!!, it) },
                                onZoomView = { scale, focal -> onZoomSoundcheckView(activeId!!, scale, focal) },
                                onSetView = { start, window -> onSetSoundcheckView(activeId!!, start, window) },
                                onSetLoopRegion = { start, end ->
                                    onSetSoundcheckLoopRegion(activeId!!, start, end)
                                },
                                onOpenStripControls = { playbackStripOverlay = it },
                                onToggleMute = { onToggleMute(s.mixerId, it) },
                                onToggleSolo = { onToggleSolo(s.mixerId, it) },
                            )
                        }
                        else -> session?.let { s ->
                            ChannelStripList(
                                mixerId = s.mixerId,
                                strips = s.channelStrips,
                                routing = activeRouting,
                                usbChannelCount = s.captureChannelCount.coerceAtLeast(s.channelStrips.size),
                                soundcheckMode = false,
                                normalized = recordWaveformNormalized,
                                waveformPeaks = s.waveformPeaks,
                                recordWaveformWindowSec = recordWaveformWindowSec,
                                recordWaveformHistorySec = recordWaveformHistorySec,
                                recordViewStartSec = s.recordViewStartSec,
                                recordViewWindowSec = s.recordViewWindowSec
                                    .takeIf { it > 0f } ?: recordWaveformWindowSec,
                                recordElapsedSec = s.recordElapsedSec,
                                captureMeterLevels = s.captureMeterLevels,
                                isMonitoring = s.isMonitoring,
                                isRecording = s.isRecording,
                                isVuMetering = s.isVuMetering,
                                showVuMeters = state.showVuMeters,
                                showWaveforms = state.showWaveforms,
                                hideArm = state.hideArmButton,
                                hideMonitor = state.hideMonitorButton,
                                hideSolo = state.hideSoloButton,
                                hideRoutingBadges = state.hideRoutingBadges,
                                numberMode = state.stripNumberMode,
                                iconMode = state.stripIconMode,
                                onArm = { onToggleArm(s.mixerId, it) },
                                onMonitor = { onToggleMonitor(s.mixerId, it) },
                                onSolo = { onToggleSolo(s.mixerId, it) },
                                onUpdateInput = { ch, usb -> onUpdateChannelInput(s.mixerId, ch, usb) },
                                onUpdateOutput = { ch, usb -> onUpdateChannelOutput(s.mixerId, ch, usb) },
                                onSetHiddenRecord = { ch, hidden ->
                                    onSetChannelHidden(s.mixerId, ch, false, hidden)
                                },
                                onSetHiddenSoundcheck = { ch, hidden ->
                                    onSetChannelHidden(s.mixerId, ch, true, hidden)
                                },
                                onZoomRecordView = { scale, focal ->
                                    onZoomRecordView(s.mixerId, scale, focal)
                                },
                                onSetRecordView = { start, window ->
                                    onSetRecordView(s.mixerId, start, window)
                                },
                            )
                        }
                    }
                }
            }
            StatusToastHost(
                toast = state.statusToast,
                onDismiss = onDismissStatusToast,
            )
        }
            }
        }
    }

    if (state.showAddMixerDialog) {
        AddMixerDialog(
            devices = state.addableUsbDevices,
            alreadyAddedCount = state.mixers.size,
            hasVirtualDemoMixer = state.mixers.any {
                org.openmultitrack.domain.mixer.VirtualMixer.isDemoMixer(it)
            },
            onAdd = onAddMixerDevice,
            onAddVirtualDemo = onAddVirtualDemoMixer,
            onDismiss = onDismissAddMixer,
        )
    }

    state.routingApplyPrompt?.let { prompt ->
        org.openmultitrack.app.ui.routing.RoutingApplyDialog(
            prompt = prompt,
            onConfirm = onConfirmRoutingApply,
            onDismiss = onCancelRoutingApply,
        )
    }

    state.routingRestorePrompt?.let { prompt ->
        org.openmultitrack.app.ui.routing.RoutingRestoreDialog(
            prompt = prompt,
            onConfirm = onConfirmRoutingRestore,
            onDismiss = onCancelRoutingRestore,
        )
    }

    if (state.showInputSources) {
        org.openmultitrack.app.ui.routing.MixerInputSourcesScreen(
            mixerName = activeProfile?.displayName ?: "Mixer",
            channels = state.inputSourcesByChannel,
            loading = state.inputSourcesLoading,
            error = state.inputSourcesError,
            onDismiss = onCloseInputSources,
            onRefresh = onRefreshInputSources,
        )
    }

    state.soundcheckLoadPrompt?.let { prompt ->
        val promptSession = state.sessionByMixer[prompt.mixerId]
        SoundcheckPostRecordDialog(
            prompt = prompt,
            session = promptSession,
            onDismiss = onDismissSoundcheckLoadPrompt,
            onLoadSoundcheck = {
                onLoadRecordingIntoSoundcheck(prompt.mixerId, prompt.sessionDir)
            },
            onLoadSimplePlay = {
                onLoadRecordingIntoSimplePlay(prompt.mixerId, prompt.sessionDir)
            },
            onRename = { title ->
                onRenameSoundcheckSession(prompt.mixerId, prompt.sessionDir, title)
            },
        )
    }

    state.flow8PairingDialog?.let {
        AlertDialog(
            onDismissRequest = onDismissFlow8PairingDialog,
            title = { Text(Flow8BleScribbleImporter.PAIRING_DIALOG_TITLE) },
            text = { Text(Flow8BleScribbleImporter.PAIRING_DIALOG_MESSAGE) },
            confirmButton = {
                Button(onClick = onConfirmFlow8PairingImport) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissFlow8PairingDialog) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showAddTrackmarkDialog) {
        val s = session
        val id = activeId
        if (s != null && id != null) {
            val nextIndex = (s.trackmarks.maxOfOrNull { it.index } ?: 0) + 1
            val defaultTitle = "Track ${nextIndex.toString().padStart(2, '0')}"
            AddTrackmarkDialog(
                initialPositionSec = s.playbackPositionSec,
                defaultTitle = defaultTitle,
                durationSec = s.playbackDurationSec,
                onDismiss = { showAddTrackmarkDialog = false },
                onConfirm = { title, startSec ->
                    onAddTrackmark(id, title, startSec)
                    showAddTrackmarkDialog = false
                },
            )
        } else {
            showAddTrackmarkDialog = false
        }
    }

    if (showTrackmarkListDialog) {
        val s = session
        val id = activeId
        if (s != null && id != null) {
            TrackmarkListDialog(
                trackmarks = s.trackmarks,
                durationSec = s.playbackDurationSec,
                currentPositionSec = s.playbackPositionSec,
                onDismiss = { showTrackmarkListDialog = false },
                onSelect = { sec ->
                    onSeekSoundcheck(id, sec)
                    showTrackmarkListDialog = false
                },
            )
        } else {
            showTrackmarkListDialog = false
        }
    }

    playbackStripOverlay?.let { index ->
        val s = session ?: return@let
        val profile = activeProfile ?: return@let
        val strip = s.channelStrips.firstOrNull { it.index == index } ?: return@let
        ChannelStripControlDialog(
            strip = strip,
            routing = activeRouting,
            usbChannelCount = s.captureChannelCount.coerceAtLeast(s.channelStrips.size),
            usbPlaybackChannelCount = MixerUsbChannelCounts.playbackChannelsForUi(
                profile = profile,
                sessionPlaybackCount = s.playbackChannelCount,
                probe = s.probe,
            ),
            controlMode = StripControlMode.PLAYBACK,
            hideArm = true,
            hideMonitor = true,
            hideSolo = state.hideSoloButton,
            hidePlaybackRouting = s.appMode == AppMode.SIMPLE_PLAY,
            onDismiss = { playbackStripOverlay = null },
            onSolo = { onToggleSolo(s.mixerId, strip.index) },
            onMute = { onToggleMute(s.mixerId, strip.index) },
            onInputSourceChange = { onUpdateChannelInput(s.mixerId, strip.index, it) },
            onOutputTargetChange = { onUpdateChannelOutput(s.mixerId, strip.index, it) },
            onHiddenRecordChange = { hidden ->
                onSetChannelHidden(s.mixerId, strip.index, false, hidden)
            },
            onHiddenSoundcheckChange = { hidden ->
                onSetChannelHidden(s.mixerId, strip.index, true, hidden)
            },
        )
    }
}

@Composable
private fun RemoteSyncingPrompt(hostLabel: String?, errorMessage: String?) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Syncing from host…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            hostLabel?.let { "Loading mixer state from $it" }
                ?: "Waiting for the host to send mixer state",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        errorMessage?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun WarningBanner(message: String) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
        Text(
            message,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun IncompleteRecordingBanner(
    message: String,
    onStopAndKeep: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onStopAndKeep) {
                Text("Stop & keep")
            }
        }
    }
}

@Composable
private fun ChannelStripList(
    mixerId: String,
    strips: List<ChannelStripState>,
    routing: MixerRoutingConfig,
    usbChannelCount: Int,
    soundcheckMode: Boolean,
    normalized: Boolean,
    waveformPeaks: Map<Int, LiveWaveformSnapshot>,
    recordWaveformWindowSec: Float,
    recordWaveformHistorySec: Float,
    recordViewStartSec: Float,
    recordViewWindowSec: Float,
    recordElapsedSec: Float,
    captureMeterLevels: Map<Int, Float>,
    isMonitoring: Boolean,
    isRecording: Boolean,
    isVuMetering: Boolean,
    showVuMeters: Boolean,
    showWaveforms: Boolean,
    hideArm: Boolean,
    hideMonitor: Boolean,
    hideSolo: Boolean,
    hideRoutingBadges: Boolean,
    numberMode: StripNumberMode,
    iconMode: StripIconMode,
    onArm: (Int) -> Unit,
    onMonitor: (Int) -> Unit,
    onSolo: (Int) -> Unit,
    onUpdateInput: (Int, Int) -> Unit,
    onUpdateOutput: (Int, Int) -> Unit,
    onSetHiddenRecord: (Int, Boolean) -> Unit,
    onSetHiddenSoundcheck: (Int, Boolean) -> Unit,
    onZoomRecordView: (Float, Float) -> Unit = { _, _ -> },
    onSetRecordView: (Float, Float) -> Unit = { _, _ -> },
) {
    var overlayIndex by remember { mutableStateOf<Int?>(null) }
    val visibleStrips = strips.filter { !routing.isHidden(it.index, soundcheckMode) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        val count = visibleStrips.size.coerceAtLeast(1)
        val gap = 2.dp
        val showTimeline = showWaveforms
        val rulerAndGap = if (showTimeline) WaveformTimeRulerHeight + gap else 0.dp
        val totalGaps = gap * (count - 1).coerceAtLeast(0)
        val naturalHeight = (maxHeight - rulerAndGap - totalGaps) / count
        val useScroll = naturalHeight < MinStripHeight
        val stripHeight = if (useScroll) ScrollStripHeight else naturalHeight
        val innerPad = (stripHeight * 0.12f).coerceIn(3.dp, 10.dp)
        val labelGap = innerPad / 2
        val labelFontSize = (stripHeight.value * 0.30f).coerceIn(10f, 16f)
        val labelColumnWidth = stripLabelColumnWidth(
            visibleStrips,
            numberMode,
            iconMode,
            labelFontSize,
            stripHeight,
            hideArm,
            hideMonitor,
            hideSolo,
        )
        val waveformAreaStart = innerPad + labelColumnWidth + labelGap
        var waveformWidthPx by remember { mutableFloatStateOf(0f) }
        var gestureActive by remember { mutableStateOf(false) }
        var gestureViewWindow by remember { mutableFloatStateOf(recordViewWindowSec) }
        val elapsedState by rememberUpdatedState(recordElapsedSec)
        val historyWindowState by rememberUpdatedState(recordWaveformHistorySec)
        val transformState = rememberTransformableState { zoomChange, _, _ ->
            if (waveformWidthPx <= 0f) return@rememberTransformableState
            if (zoomChange == 1f) return@rememberTransformableState
            gestureActive = true
            val history = historyWindowState.coerceIn(
                RecordViewLayout.MIN_HISTORY_SEC,
                RecordViewLayout.MAX_HISTORY_SEC,
            )
            gestureViewWindow = RecordViewLayout.clampWindow(
                gestureViewWindow / zoomChange,
                history,
            )
        }
        LaunchedEffect(transformState.isTransformInProgress) {
            if (!transformState.isTransformInProgress && gestureActive) {
                gestureActive = false
                onSetRecordView(
                    RecordViewLayout.anchoredStartSec(elapsedState, gestureViewWindow),
                    gestureViewWindow,
                )
            }
        }
        LaunchedEffect(recordViewWindowSec) {
            if (!gestureActive && !transformState.isTransformInProgress) {
                gestureViewWindow = recordViewWindowSec
            }
        }
        val displayViewWindow = if (gestureActive || transformState.isTransformInProgress) {
            gestureViewWindow
        } else {
            recordViewWindowSec
        }
        val displayViewStart = RecordViewLayout.anchoredStartSec(recordElapsedSec, displayViewWindow)

        if (visibleStrips.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No channels yet",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Allow USB access if prompted. The app will reconnect automatically when the mixer is plugged in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            return@BoxWithConstraints
        }

        val listModifier = if (useScroll) {
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxSize()
        }
        Box(modifier = listModifier) {
        Column(Modifier.fillMaxSize()) {
            if (showTimeline) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WaveformTimeRulerHeight)
                        .padding(horizontal = innerPad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(labelGap),
                ) {
                    Spacer(Modifier.width(labelColumnWidth))
                    Spacer(Modifier.width(StripVuMeterWidth))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(WaveformTimeRulerHeight)
                            .onSizeChanged { waveformWidthPx = it.width.toFloat() },
                    ) {
                        RecordingTimelineRuler(
                            elapsedSec = recordElapsedSec,
                            viewStartSec = displayViewStart,
                            viewWindowSec = displayViewWindow,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.height(gap))
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
            visibleStrips.forEach { strip ->
                ChannelStripRow(
                    strip = strip,
                    routing = routing,
                    soundcheckMode = soundcheckMode,
                    stripHeight = stripHeight,
                    innerPad = innerPad,
                    labelFontSize = labelFontSize,
                    labelColumnWidth = labelColumnWidth,
                    waveform = if (showWaveforms && isRecording) waveformPeaks[strip.index] else null,
                    recordWaveformHistorySec = recordWaveformHistorySec,
                    recordViewStartSec = displayViewStart,
                    recordViewWindowSec = displayViewWindow,
                    recordElapsedSec = recordElapsedSec,
                    captureMeterLevel = if (showVuMeters && (isMonitoring || isRecording || isVuMetering)) {
                        captureMeterLevels[strip.index] ?: 0f
                    } else {
                        null
                    },
                    normalized = normalized,
                    showWaveform = showWaveforms,
                    numberMode = numberMode,
                    iconMode = iconMode,
                    hideArm = hideArm,
                    hideMonitor = hideMonitor,
                    hideSolo = hideSolo,
                    hideRoutingBadges = hideRoutingBadges,
                    onOpenControls = { overlayIndex = strip.index },
                )
            }
            }
        }
            if (showWaveforms) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(start = waveformAreaStart, end = innerPad)
                        .transformable(
                            state = transformState,
                            lockRotationOnZoomPan = true,
                        ),
                )
            }
        }
    }

    overlayIndex?.let { index ->
        val strip = strips.firstOrNull { it.index == index } ?: return@let
        ChannelStripControlDialog(
            strip = strip,
            routing = routing,
            usbChannelCount = usbChannelCount,
            hideArm = false,
            hideMonitor = false,
            hideSolo = false,
            onDismiss = { overlayIndex = null },
            onArm = { onArm(strip.index) },
            onMonitor = { onMonitor(strip.index) },
            onSolo = { onSolo(strip.index) },
            onInputSourceChange = { onUpdateInput(strip.index, it) },
            onOutputTargetChange = { onUpdateOutput(strip.index, it) },
            onHiddenRecordChange = { onSetHiddenRecord(strip.index, it) },
            onHiddenSoundcheckChange = { onSetHiddenSoundcheck(strip.index, it) },
        )
    }
}

@Composable
private fun ChannelStripRow(
    strip: ChannelStripState,
    routing: MixerRoutingConfig,
    soundcheckMode: Boolean,
    stripHeight: Dp,
    innerPad: Dp,
    labelFontSize: Float,
    labelColumnWidth: Dp,
    waveform: LiveWaveformSnapshot?,
    recordWaveformHistorySec: Float,
    recordViewStartSec: Float,
    recordViewWindowSec: Float,
    recordElapsedSec: Float,
    captureMeterLevel: Float?,
    normalized: Boolean,
    showWaveform: Boolean,
    numberMode: StripNumberMode,
    iconMode: StripIconMode,
    hideArm: Boolean,
    hideMonitor: Boolean,
    hideSolo: Boolean,
    hideRoutingBadges: Boolean,
    onOpenControls: () -> Unit,
) {
    val colorBarHeight = stripHeight - innerPad * 2
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(stripHeight)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = innerPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(innerPad / 2),
    ) {
        StripIdentityCell(
            strip = strip,
            columnWidth = labelColumnWidth,
            labelFontSize = labelFontSize,
            numberMode = numberMode,
            iconMode = iconMode,
            colorBarHeight = colorBarHeight,
            hideArm = hideArm,
            hideMonitor = hideMonitor,
            hideSolo = hideSolo,
            routing = routing,
            soundcheckMode = soundcheckMode,
            hideRoutingBadges = hideRoutingBadges,
            onClick = onOpenControls,
        )
        StripVuMeter(
            level = captureMeterLevel ?: liveVuLevel(waveform, normalized),
            height = colorBarHeight,
        )
        if (showWaveform) {
            LiveWaveformStrip(
                peaks = waveform?.peaks ?: floatArrayOf(),
                bufferWindowSec = recordWaveformHistorySec.coerceIn(
                    RecordViewLayout.MIN_HISTORY_SEC,
                    RecordViewLayout.MAX_HISTORY_SEC,
                ),
                elapsedSec = recordElapsedSec,
                peaksPerSec = RemoteProtocol.LIVE_WAVEFORM_PEAKS_PER_SEC,
                color = Color(strip.colorArgb),
                normalized = normalized,
                viewStartSec = recordViewStartSec,
                viewWindowSec = recordViewWindowSec,
                modifier = Modifier
                    .weight(1f)
                    .height(colorBarHeight)
                    .clip(RoundedCornerShape(3.dp)),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun AddMixerDialog(
    devices: List<UsbAudioDeviceDescriptor>,
    alreadyAddedCount: Int,
    hasVirtualDemoMixer: Boolean,
    onAdd: (UsbAudioDeviceDescriptor) -> Unit,
    onAddVirtualDemo: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add audio interface") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!hasVirtualDemoMixer) {
                    TextButton(onClick = onAddVirtualDemo) {
                        Text("Demo band — no USB")
                    }
                    HorizontalDivider()
                }
                if (devices.isEmpty()) {
                    Text(
                        if (alreadyAddedCount > 0) {
                            "All connected USB mixers are already added."
                        } else {
                            "No USB devices found."
                        },
                    )
                } else {
                    devices.forEach { d ->
                        TextButton(onClick = { onAdd(d) }) {
                            Text("${d.productName ?: d.deviceName} (${d.guessedModel ?: "USB"})")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
