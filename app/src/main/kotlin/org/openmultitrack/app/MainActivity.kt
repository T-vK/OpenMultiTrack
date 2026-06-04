package org.openmultitrack.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openmultitrack.app.ui.MainScreen
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory(applicationContext)
    }

    private val usbPermissionAction = "${BuildConfig.APPLICATION_ID}.USB_PERMISSION"

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
                in setOf(
                    UsbManager.ACTION_USB_DEVICE_ATTACHED,
                    UsbManager.ACTION_USB_DEVICE_DETACHED,
                ) -> viewModel.refreshDevices()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerUsbReceiver()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface {
                    val state = viewModel.uiState.collectAsStateWithLifecycle()
                    MainScreen(
                        state = state.value,
                        onRefresh = viewModel::refreshDevices,
                        onRequestPermission = ::requestUsbPermission,
                        onProbe = viewModel::probeDevice,
                    )
                }
            }
        }

        viewModel.refreshDevices()
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
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        } ?: return
        requestUsbPermission(device)
    }

    private fun requestUsbPermission(descriptor: UsbAudioDeviceDescriptor) {
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        val device = usbManager.deviceList[descriptor.deviceName] ?: return
        requestUsbPermission(device)
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        if (usbManager.hasPermission(device)) {
            viewModel.onUsbPermissionGranted(device.deviceName)
            return
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        val intent = PendingIntent.getBroadcast(this, 0, Intent(usbPermissionAction), flags)
        usbManager.requestPermission(device, intent)
    }
}
