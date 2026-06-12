package org.openmultitrack.app

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.data.MixerDeviceStore
import org.openmultitrack.app.audio.RecordAudioPermissions
import org.openmultitrack.app.scribble.Flow8BlePermissions
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.openmultitrack.app.device.PrerequisiteKind
import org.openmultitrack.app.service.SessionTransportActions
import org.openmultitrack.app.ui.daw.DawMainScreen
import org.openmultitrack.app.ui.daw.FloatingLogViewerOverlay
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import kotlinx.coroutines.launch
import org.openmultitrack.usb.BehringerUsbIdentifiers
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbPermissionCoordinator
import org.openmultitrack.usb.UsbPermissionQueue

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory(applicationContext)
    }
    private val settings by lazy { AppSettingsStore(applicationContext) }

    private val usbPermissionAction = "${BuildConfig.APPLICATION_ID}.USB_PERMISSION"
    private val usbPermissionQueue = UsbPermissionQueue()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        OmtLog.i("Activity", "POST_NOTIFICATIONS granted=$granted")
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results.values.all { it }
        OmtLog.i("Activity", "Bluetooth permissions granted=$granted")
        viewModel.onBluetoothPermissionsResult(granted)
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        OmtLog.i("Activity", "RECORD_AUDIO granted=$granted")
        viewModel.onAudioPermissionResult(granted)
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        OmtLog.i("Activity", "ACCESS_FINE_LOCATION granted=$granted")
        viewModel.onLocationPermissionResult(granted)
    }

    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshPrerequisites()
    }

    private val remoteQrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { viewModel.pairRemoteFromQr(it) }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                usbPermissionAction -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        UsbPermissionCoordinator.markRequestFinished(device, granted)
                        usbPermissionQueue.onRequestFinished(device)
                        if (granted) {
                            viewModel.onUsbPermissionGranted(device.deviceName)
                        }
                        drainUsbPermissionQueue()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val attached = parseUsbDevice(intent) ?: return
                    if (!viewModel.isSavedMixerUsbDevice(attached)) {
                        OmtLog.d(
                            "Activity",
                            "Ignoring USB attach for non-saved device: ${attached.productName} " +
                                "vid=${attached.vendorId} pid=${attached.productId}",
                        )
                        return
                    }
                    val desc = toDescriptor(attached)
                    viewModel.onUsbAttached(desc)
                    enqueueUsbPermissionRequests()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val detached = parseUsbDevice(intent)
                    viewModel.onUsbDetached(detached?.deviceName)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        registerUsbReceiver()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state = viewModel.uiState.collectAsStateWithLifecycle()
                    Box(Modifier.fillMaxSize()) {
                    DawMainScreen(
                        state = state.value,
                        monitorGain = state.value.monitorGainLinear,
                        recordWaveformWindowSec = state.value.recordWaveformWindowSec,
                        recordWaveformHistorySec = state.value.recordWaveformHistorySec,
                        playbackWaveformWindowSec = state.value.playbackWaveformWindowSec,
                        recordWaveformNormalized = state.value.recordWaveformNormalized,
                        playbackWaveformNormalized = state.value.playbackWaveformNormalized,
                        onAddMixer = { viewModel.showAddMixerDialog(true) },
                        showMixerPicker = state.value.showMixerPicker,
                        onOpenMixerPicker = { viewModel.showMixerPicker(true) },
                        onCloseMixerPicker = { viewModel.showMixerPicker(false) },
                        mixerSettingsMixerId = state.value.mixerSettingsMixerId,
                        onOpenMixerSettings = viewModel::showMixerSettings,
                        onCloseMixerSettings = { viewModel.showMixerSettings(null) },
                        mixerRoutingById = state.value.mixerRoutingById,
                        onSaveMixerRouting = viewModel::saveMixerRouting,
                        onUpdateChannelInput = viewModel::updateChannelInputSource,
                        onUpdateChannelOutput = viewModel::updateChannelOutputTarget,
                        onSetChannelHidden = viewModel::setChannelHidden,
                        showRemoteControlSheet = state.value.showRemoteControlSheet,
                        remotePairingUri = state.value.remotePairingUri,
                        remotePairingPin = state.value.remotePairingPin,
                        onOpenRemoteControl = { viewModel.showRemoteControlSheet(true) },
                        onCloseRemoteControl = { viewModel.showRemoteControlSheet(false) },
                        onEnterRemoteClientMode = viewModel::enterRemoteClientMode,
                        onEnableRemoteHosting = viewModel::enableRemoteHosting,
                        onStopRemoteHosting = viewModel::stopRemoteHosting,
                        onUnpairRemoteHost = viewModel::unpairRemoteHost,
                        onConnectRemoteManual = viewModel::connectRemoteManual,
                        onScanRemoteQr = {
                            remoteQrScanLauncher.launch(
                                ScanOptions().apply {
                                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    setPrompt("Scan OpenMultiTrack pairing QR")
                                    setBeepEnabled(false)
                                },
                            )
                        },
                        onSelectMixer = viewModel::setActiveMixer,
                        onRemoveMixer = viewModel::removeMixer,
                        onAddMixerDevice = viewModel::addMixer,
                        onAddVirtualDemoMixer = viewModel::addVirtualDemoMixer,
                        onDismissAddMixer = { viewModel.showAddMixerDialog(false) },
                        onToggleArm = viewModel::toggleArm,
                        onToggleMonitor = viewModel::toggleMonitor,
                        onToggleSolo = viewModel::toggleSolo,
                        onToggleMute = viewModel::toggleMute,
                        onSetMonitorOutput = viewModel::setMonitorOutput,
                        onStartMonitor = viewModel::startMonitor,
                        onStopMonitor = viewModel::stopMonitor,
                        onStartRecord = viewModel::startRecord,
                        onStopRecord = viewModel::stopRecord,
                        onSetAppMode = viewModel::setAppMode,
                        onOpenSettings = { viewModel.showSettings(true) },
                        onCloseSettings = { viewModel.showSettings(false) },
                        onOpenLog = { viewModel.showLogViewer(true) },
                        onCloseLog = { viewModel.showLogViewer(false) },
                        onMonitorGainChange = viewModel::setMonitorGain,
                        onLoadScribbleStrip = viewModel::loadScribbleStrip,
                        onSelectSoundcheckSession = viewModel::selectSoundcheckSession,
                        onToggleSoundcheckPlayback = viewModel::toggleSoundcheckPlayback,
                        onStopSoundcheck = viewModel::stopSoundcheck,
                        onSeekSoundcheck = viewModel::seekSoundcheck,
                        onPanSoundcheckView = viewModel::panSoundcheckView,
                        onZoomSoundcheckView = viewModel::zoomSoundcheckView,
                        onSetSoundcheckView = viewModel::setSoundcheckView,
                        onZoomRecordView = viewModel::zoomRecordView,
                        onSetRecordView = viewModel::setRecordView,
                        onSetSoundcheckLoopRegion = viewModel::setSoundcheckLoopRegion,
                        onToggleSoundcheckLoop = viewModel::toggleSoundcheckLoop,
                        onSetSoundcheckLoopIn = viewModel::setSoundcheckLoopIn,
                        onSetSoundcheckLoopOut = viewModel::setSoundcheckLoopOut,
                        onConfirmFlow8PairingImport = viewModel::confirmFlow8PairingImport,
                        onDismissFlow8PairingDialog = viewModel::dismissFlow8PairingDialog,
                        onHideArmChange = viewModel::setHideArmButton,
                        onHideMonitorChange = viewModel::setHideMonitorButton,
                        onHideSoloChange = viewModel::setHideSoloButton,
                        onHideRoutingBadgesChange = viewModel::setHideRoutingBadges,
                        onShowWaveformsChange = viewModel::setShowWaveforms,
                        onShowVuMetersChange = viewModel::setShowVuMeters,
                        onStripNumberModeChange = viewModel::setStripNumberMode,
                        onStripIconModeChange = viewModel::setStripIconMode,
                        onRecordWaveformWindowChange = viewModel::setRecordWaveformWindowSec,
                        onRecordWaveformHistoryChange = viewModel::setRecordWaveformHistorySec,
                        onPlaybackWaveformWindowChange = viewModel::setPlaybackWaveformWindowSec,
                        onDismissStatusToast = viewModel::dismissStatusToast,
                        onFinalizeIncompleteRecording = viewModel::finalizeIncompleteRecording,
                        onLoadRecordingIntoSoundcheck = viewModel::loadRecordingIntoSoundcheck,
                        onLoadRecordingIntoSimplePlay = viewModel::loadRecordingIntoSimplePlay,
                        onDismissSoundcheckLoadPrompt = viewModel::dismissSoundcheckLoadPrompt,
                        onRoutingAutomationConfigChange = { config ->
                            viewModel.uiState.value.activeMixerId?.let { id ->
                                viewModel.setRoutingAutomationConfig(id, config)
                            }
                        },
                        onOpenInputSources = { viewModel.showInputSources(true) },
                        onCloseInputSources = { viewModel.showInputSources(false) },
                        onRefreshInputSources = viewModel::refreshInputSources,
                        onConfirmRoutingApply = viewModel::confirmRoutingApply,
                        onCancelRoutingApply = viewModel::cancelRoutingApply,
                        onConfirmRoutingRestore = viewModel::confirmRoutingRestore,
                        onCancelRoutingRestore = viewModel::cancelRoutingRestore,
                        onPostRecordBehaviorChange = viewModel::setPostRecordBehavior,
                        onShowRecordingStorageInfoButtonChange = viewModel::setShowRecordingStorageInfoButton,
                        onAutoShowRecordingStorageTooltipChange = viewModel::setAutoShowRecordingStorageTooltip,
                        onRecordWaveformNormalizedChange = viewModel::setRecordWaveformNormalized,
                        onPlaybackWaveformNormalizedChange = viewModel::setPlaybackWaveformNormalized,
                        onSetStorageRootPath = viewModel::setStorageRootPath,
                        onAddAdditionalLibraryRoot = viewModel::addAdditionalLibraryRoot,
                        onRemoveAdditionalLibraryRoot = viewModel::removeAdditionalLibraryRoot,
                        onAutoScanRemovableMediaChange = viewModel::setAutoScanRemovableMedia,
                        onAddRedundantRecordingRoot = viewModel::addRedundantRecordingRoot,
                        onRemoveRedundantRecordingRoot = viewModel::removeRedundantRecordingRoot,
                        onAlwaysIncludeOpenMultiTrackFoldersChange = viewModel::setAlwaysIncludeOpenMultiTrackFolders,
                        onLocalSpillBufferEnabledChange = viewModel::setLocalSpillBufferEnabled,
                        onLocalSpillBufferMinutesChange = viewModel::setLocalSpillBufferMinutes,
                        onMinFreeStorageBytesChange = viewModel::setMinFreeStorageBytes,
                        onOpenBatterySettings = viewModel::openBatterySettings,
                        onRenameSoundcheckSession = viewModel::renameSoundcheckSession,
                        onDeleteSoundcheckSession = viewModel::deleteSoundcheckSession,
                        lastSelectedSoundcheckSession = viewModel::lastSelectedSoundcheckSession,
                        onOpenSessionPicker = { viewModel.showSessionPicker(true) },
                        onCloseSessionPicker = { viewModel.showSessionPicker(false) },
                        onDiscoverRemoteHosts = viewModel::discoverRemoteHosts,
                        onConnectRemoteHost = viewModel::connectRemoteHost,
                        onDisconnectRemote = viewModel::disconnectRemote,
                        onExitRemoteMode = viewModel::exitRemoteMode,
                        onPrerequisiteAction = ::handlePrerequisiteAction,
                        onChapterSupportEnabledChange = viewModel::setChapterSupportEnabled,
                        onPreviousTrackmark = viewModel::seekToPreviousTrackmark,
                        onNextTrackmark = viewModel::seekToNextTrackmark,
                        onAddTrackmark = viewModel::addTrackmark,
                    )
                    if (BuildConfig.DEBUG) {
                        FloatingLogViewerOverlay(
                            visible = state.value.showLogViewer,
                            onDismiss = { viewModel.showLogViewer(false) },
                        )
                    }
                    }
                }
            }
        }

        enqueueUsbPermissionRequests()
        handleUsbIntent(intent)
        deliverNotificationTransportIntent(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.usbPermissionRequests.collect {
                    enqueueUsbPermissionRequests()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.bluetoothPermissionRequests.collect {
                    requestBluetoothPermissionsIfNeeded()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.audioPermissionRequests.collect {
                    requestAudioPermissionIfNeeded()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.storageAccessRequests.collect { path ->
                    requestStorageAccessForPath(path)
                }
            }
        }
        requestBluetoothPermissionsForFlow8IfNeeded()
        requestAudioPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        enqueueUsbPermissionRequests()
        requestBluetoothPermissionsForFlow8IfNeeded()
        requestAudioPermissionIfNeeded()
        viewModel.onAppResumed()
        viewModel.refreshPrerequisites()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbIntent(intent)
        deliverNotificationTransportIntent(intent)
    }

    private fun deliverNotificationTransportIntent(intent: Intent?) {
        val action = intent?.action
        val mixerId = intent?.getStringExtra(SessionTransportActions.EXTRA_MIXER_ID)
        if (action != null && SessionTransportActions.isNotificationAction(action)) {
            SessionTransportActions.handle(this, action, fromActivity = true, mixerId = mixerId)
            intent.action = Intent.ACTION_MAIN
            setIntent(intent)
            return
        }
        if (mixerId != null) {
            viewModel.setActiveMixer(mixerId)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(usbReceiver)
        super.onDestroy()
    }

    private fun parseUsbDevice(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

    private fun toDescriptor(device: UsbDevice): UsbAudioDeviceDescriptor {
        val enumerator = UsbAudioEnumerator(applicationContext)
        return enumerator.listUsbDevices().firstOrNull { it.deviceName == device.deviceName }
            ?: UsbAudioDeviceDescriptor(
                deviceName = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                manufacturerName = device.manufacturerName,
                productName = device.productName,
                serialNumber = null,
                isLikelyBehringerMixer = BehringerUsbIdentifiers.isLikelyBehringerMixer(
                    device.vendorId,
                    device.productName,
                ),
                guessedModel = BehringerUsbIdentifiers.guessModel(device.productName),
                androidAudioDeviceId = null,
            )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(usbPermissionAction)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }
    }

    private fun handleUsbIntent(intent: Intent?) {
        val device = parseUsbDevice(intent ?: return) ?: return
        if (!viewModel.isSavedMixerUsbDevice(device)) return
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        usbPermissionQueue.enqueueMissing(listOf(device), usbManager)
        drainUsbPermissionQueue()
    }

    private fun requestAudioPermissionIfNeeded() {
        if (RecordAudioPermissions.hasPermission(this)) {
            viewModel.onAudioPermissionResult(granted = true)
            return
        }
        if (MixerDeviceStore(applicationContext).listMixers().isEmpty()) return
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun requestBluetoothPermissionsIfNeeded() {
        val missing = Flow8BlePermissions.missing(this)
        if (missing.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(missing.toTypedArray())
        } else {
            viewModel.onBluetoothPermissionsResult(granted = true)
        }
    }

    private fun requestBluetoothPermissionsForFlow8IfNeeded() {
        val hasFlow8 = MixerDeviceStore(applicationContext).listMixers().any { it.productId == FLOW8_PRODUCT_ID }
        if (!hasFlow8) return
        val missing = Flow8BlePermissions.missing(this)
        if (missing.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(missing.toTypedArray())
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            Flow8BlePermissions.needsLocationForBleScan(this)
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun handlePrerequisiteAction(kind: PrerequisiteKind) {
        when (kind) {
            PrerequisiteKind.BLUETOOTH_PERMISSION -> requestBluetoothPermissionsIfNeeded()
            PrerequisiteKind.LOCATION_PERMISSION -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            PrerequisiteKind.BLUETOOTH_OFF -> requestBluetoothEnable()
            PrerequisiteKind.RECORD_AUDIO_PERMISSION -> requestAudioPermissionIfNeeded()
        }
    }

    private fun requestBluetoothEnable() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        try {
            bluetoothEnableLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            OmtLog.w("Activity", "Bluetooth enable intent unavailable", e)
            openBluetoothSettings()
        }
    }

    private fun openBluetoothSettings() {
        try {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            OmtLog.w("Activity", "Bluetooth settings unavailable", e)
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", packageName, null),
        )
        startActivity(intent)
    }

    private fun enqueueUsbPermissionRequests() {
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        val saved = usbManager.deviceList.values
            .filter { viewModel.isSavedMixerUsbDevice(it) }
        usbPermissionQueue.enqueueMissing(saved, usbManager)
        drainUsbPermissionQueue()
    }

    private fun drainUsbPermissionQueue() {
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        val connected = usbManager.deviceList.values
        while (true) {
            val device = usbPermissionQueue.pollNext(usbManager, connected) ?: return
            if (usbManager.hasPermission(device)) {
                UsbPermissionCoordinator.markGranted(usbManager, device)
                usbPermissionQueue.onRequestFinished(device)
                viewModel.onUsbPermissionGranted(device.deviceName)
                continue
            }
            requestUsbPermission(device)
            return
        }
    }

    private fun requestStorageAccessForPath(path: String) {
        val helper = org.openmultitrack.app.data.StorageAccessHelper
        when {
            helper.needsManageAllFilesAccess(this, path) -> helper.openManageAllFilesSettings(this)
            helper.needsLegacyStoragePermission(this, path) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    helper.openManageAllFilesSettings(this)
                }
            }
            !helper.canWriteTo(path) -> helper.openManageAllFilesSettings(this)
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        if (usbManager.hasPermission(device)) {
            UsbPermissionCoordinator.markGranted(usbManager, device)
            usbPermissionQueue.onRequestFinished(device)
            viewModel.onUsbPermissionGranted(device.deviceName)
            drainUsbPermissionQueue()
            return
        }
        if (!UsbPermissionCoordinator.shouldRequest(usbManager, device)) {
            usbPermissionQueue.onRequestFinished(device)
            drainUsbPermissionQueue()
            return
        }
        if (!UsbPermissionCoordinator.markRequestStarted(device)) {
            drainUsbPermissionQueue()
            return
        }
        usbPermissionQueue.onRequestStarted(device)
        val permissionIntent = Intent(usbPermissionAction).setPackage(packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        val requestCode = UsbPermissionCoordinator.stableKey(device).hashCode()
        val pi = PendingIntent.getBroadcast(this, requestCode, permissionIntent, flags)
        runCatching {
            usbManager.requestPermission(device, pi)
        }.onFailure { error ->
            UsbPermissionCoordinator.markRequestFinished(device, granted = false)
            usbPermissionQueue.onRequestFinished(device)
            drainUsbPermissionQueue()
            OmtLog.w("Activity", "USB permission request failed for ${device.deviceName}", error)
        }
    }

    private companion object {
        private const val FLOW8_PRODUCT_ID = 0x050c
    }
}
