package org.openmultitrack.app

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.ui.daw.DawMainScreen
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.usb.BehringerUsbIdentifiers
import org.openmultitrack.usb.UsbAudioEnumerator

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory(applicationContext)
    }
    private val settings by lazy { AppSettingsStore(applicationContext) }

    private val usbPermissionAction = "${BuildConfig.APPLICATION_ID}.USB_PERMISSION"

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        OmtLog.i("Activity", "POST_NOTIFICATIONS granted=$granted")
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
                    if (granted && device != null) {
                        viewModel.onUsbPermissionGranted(device.deviceName)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val attached = parseUsbDevice(intent) ?: return
                    val desc = toDescriptor(attached)
                    viewModel.onUsbAttached(desc)
                    requestUsbPermission(attached)
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
                    DawMainScreen(
                        state = state.value,
                        monitorGain = settings.monitorGainLinear,
                        waveformNormalized = settings.waveformNormalized,
                        onAddMixer = { viewModel.showAddMixerDialog(true) },
                        onSelectMixer = viewModel::setActiveMixer,
                        onAddMixerDevice = viewModel::addMixer,
                        onDismissAddMixer = { viewModel.showAddMixerDialog(false) },
                        onToggleArm = viewModel::toggleArm,
                        onToggleMonitor = viewModel::toggleMonitor,
                        onToggleSolo = viewModel::toggleSolo,
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
                        onRefreshUsb = viewModel::refreshUsbAndOutputs,
                        onRefreshScribble = viewModel::refreshScribble,
                        onHideArmChange = viewModel::setHideArmButton,
                        onHideMonitorChange = viewModel::setHideMonitorButton,
                        onHideSoloChange = viewModel::setHideSoloButton,
                        onShowWaveformsChange = viewModel::setShowWaveforms,
                    )
                }
            }
        }

        viewModel.refreshUsbAndOutputs()
        handleUsbIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbIntent(intent)
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
        requestUsbPermission(device)
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        if (usbManager.hasPermission(device)) {
            viewModel.onUsbPermissionGranted(device.deviceName)
            return
        }
        val permissionIntent = Intent(usbPermissionAction).setPackage(packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(this, device.deviceId, permissionIntent, flags)
        usbManager.requestPermission(device, pi)
    }
}
