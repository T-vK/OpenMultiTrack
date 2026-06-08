package org.openmultitrack.app.scribble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.openmultitrack.mixer.behringer.UsbChannelScribble
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads FLOW 8 channel strip names over BLE (see docs/flow8-reverse-engineering/).
 *
 * Requires pairing mode on the mixer (~60 s window) and Bluetooth permissions.
 */
class Flow8BleScribbleImporter(private val context: Context) {
    suspend fun fetchChannelLabels(): Result<List<UsbChannelScribble>> = withContext(Dispatchers.IO) {
        runCatching {
            val device = findFlowDevice() ?: error("FLOW 8 not found over BLE — enable pairing mode on the mixer")
            fetchFromDevice(device)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun findFlowDevice(): BluetoothDevice? {
        val adapter = bluetoothAdapter() ?: error("Bluetooth is not available")
        if (!adapter.isEnabled) error("Bluetooth is disabled")
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val scanner = adapter.bluetoothLeScanner
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        val dev = result?.device ?: return
                        val name = dev.name ?: result.scanRecord?.deviceName
                        if (name != null && name.uppercase().contains("FLOW")) {
                            scanner.stopScan(this)
                            if (cont.isActive) cont.resume(dev)
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        scanner.stopScan(this)
                        if (cont.isActive) cont.resumeWithException(IllegalStateException("BLE scan failed: $errorCode"))
                    }
                }
                cont.invokeOnCancellation { runCatching { scanner.stopScan(callback) } }
                scanner.startScan(callback)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchFromDevice(device: BluetoothDevice): List<UsbChannelScribble> =
        suspendCancellableCoroutine { cont ->
            val clientId = ClientIdStore.load(context)
            val session = Flow8BleSession(device, clientId) { result ->
                if (cont.isActive) {
                    result.fold(
                        onSuccess = { cont.resume(it) },
                        onFailure = { cont.resumeWithException(it) },
                    )
                }
            }
            cont.invokeOnCancellation { session.close() }
            session.start(context)
        }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return manager.adapter
    }

    private class ClientIdStore {
        companion object {
            private const val PREFS = "flow8_ble"
            private const val KEY = "client_id"

            fun load(context: Context): ByteArray {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val stored = prefs.getString(KEY, null)?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()
                if (stored != null && stored.size == 16 && stored.any { it != 0.toByte() }) return stored
                val fresh = ByteArray(16) { kotlin.random.Random.nextInt(1, 256).toByte() }
                prefs.edit().putString(KEY, fresh.joinToString("") { "%02x".format(it) }).apply()
                return fresh
            }
        }
    }

    private class Flow8BleSession(
        private val device: BluetoothDevice,
        private val clientId: ByteArray,
        private val onComplete: (Result<List<UsbChannelScribble>>) -> Unit,
    ) {
        private var gatt: BluetoothGatt? = null
        private var characteristic: BluetoothGattCharacteristic? = null
        private val fragments = linkedMapOf<Int, ByteArray>()
        private var fragmentTotal = 0
        private var sawHandshakeHost = false
        private var sawHandshakeReply = false

        @SuppressLint("MissingPermission")
        fun start(context: Context) {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }

        fun close() {
            runCatching { gatt?.close() }
            gatt = null
        }

        private val gattCallback = object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!sawHandshakeReply) {
                        onComplete(Result.failure(IllegalStateException("FLOW 8 disconnected during BLE import")))
                    }
                    close()
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onComplete(Result.failure(IllegalStateException("GATT service discovery failed")))
                    return
                }
                val service = gatt.getService(SERVICE_UUID) ?: run {
                    onComplete(Result.failure(IllegalStateException("FLOW 8 GATT service not found")))
                    return
                }
                characteristic = service.getCharacteristic(CHAR_UUID) ?: run {
                    onComplete(Result.failure(IllegalStateException("FLOW 8 GATT characteristic not found")))
                    return
                }
                gatt.setCharacteristicNotification(characteristic, true)
                val cccd = characteristic!!.getDescriptor(CLIENT_CONFIG_UUID)
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(cccd)
            }

            @SuppressLint("MissingPermission")
            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                // Wait for 0x35 HandshakeHost from device (pairing mode).
            }

            @SuppressLint("MissingPermission")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                handleNotification(value, gatt)
            }

            @Deprecated("Deprecated in API 33")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                handleNotification(characteristic.value ?: return, gatt)
            }

            @SuppressLint("MissingPermission")
            private fun handleNotification(data: ByteArray, gatt: BluetoothGatt) {
                if (data.isEmpty()) return
                when (data[0].toInt() and 0xFF) {
                    T_HANDSHAKE_HOST -> {
                        sawHandshakeHost = true
                        writeFrame(gatt, T_HANDSHAKE_CLIENT, clientId)
                    }
                    T_HANDSHAKE_REPLY -> {
                        sawHandshakeReply = true
                        writeFrame(gatt, T_GET_MIXER_STATE)
                    }
                    T_MIXER_STATE -> {
                        if (data.size < 4) return
                        fragmentTotal = data[1].toInt() and 0xFF
                        val seq = data[3].toInt() and 0xFF
                        fragments[seq] = data.copyOfRange(4, data.size)
                        if (fragmentTotal > 0 && fragments.size >= fragmentTotal) {
                            finishDump()
                        }
                    }
                }
            }

            @SuppressLint("MissingPermission")
            private fun writeFrame(gatt: BluetoothGatt, type: Int, payload: ByteArray = byteArrayOf()) {
                val char = characteristic ?: return
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                char.value = frame(type, payload)
                gatt.writeCharacteristic(char)
            }

            private fun finishDump() {
                val buf = buildList {
                    for (i in 0 until fragmentTotal) {
                        add(fragments[i] ?: byteArrayOf())
                    }
                }.fold(byteArrayOf()) { acc, bytes -> acc + bytes }
                val names = Flow8NameDecoder.decode(buf)
                if (names.isEmpty()) {
                    onComplete(Result.failure(IllegalStateException("No channel names in FLOW 8 state dump")))
                    return
                }
                val labels = names.take(CHANNEL_COUNT).mapIndexed { index, name ->
                    UsbChannelScribble(
                        usbChannel = index + 1,
                        sourceLabel = "Ch${index + 1}",
                        name = name,
                        colorIndex = null,
                    )
                }
                onComplete(Result.success(labels))
                close()
            }
        }
    }

    private object Flow8NameDecoder {
        private val OFFSETS = intArrayOf(0x0554, 0x0572, 0x0590, 0x05AE, 0x05CC, 0x05EA, 0x0608)
        private const val STRIDE = 0x1E

        fun decode(buf: ByteArray): List<String> {
            val fixed = OFFSETS.map { off -> readLengthPrefixed(buf, off) }.filterNotNull()
            if (fixed.size >= CHANNEL_COUNT) return fixed
            return scanNames(buf)
        }

        private fun readLengthPrefixed(buf: ByteArray, offset: Int): String? {
            if (offset >= buf.size) return null
            val len = buf[offset].toInt() and 0xFF
            if (len !in 2..18 || offset + 1 + len > buf.size) return null
            val slice = buf.copyOfRange(offset + 1, offset + 1 + len)
            if (!slice.all { it in 0x20..0x7E }) return null
            return String(slice, Charsets.US_ASCII)
        }

        private fun scanNames(buf: ByteArray): List<String> {
            val names = mutableListOf<String>()
            var i = 0
            while (i < buf.size) {
                val len = buf[i].toInt() and 0xFF
                if (len in 2..18 && i + 1 + len <= buf.size) {
                    val slice = buf.copyOfRange(i + 1, i + 1 + len)
                    if (slice.all { b -> b in 0x20..0x7E }) {
                        names.add(String(slice, Charsets.US_ASCII))
                        i += 1 + len
                        continue
                    }
                }
                i++
            }
            return names
        }
    }

    companion object {
        private const val CHANNEL_COUNT = 7
        private val SERVICE_UUID = UUID.fromString("14839ad4-8d7e-415c-9a42-167340cf2339")
        private val CHAR_UUID = UUID.fromString("0034594a-a8e7-4b1a-a6b1-cd5243059a57")
        private val CLIENT_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val T_HANDSHAKE_HOST = 0x35
        private const val T_HANDSHAKE_REPLY = 0x36
        private const val T_GET_MIXER_STATE = 0x37
        private const val T_MIXER_STATE = 0x38
        private const val T_HANDSHAKE_CLIENT = 0x39

        private fun frame(type: Int, payload: ByteArray): ByteArray {
            val body = byteArrayOf(type.toByte(), 0x01) + payload
            val checksum = (body.fold(0) { acc, b -> acc + (b.toInt() and 0xFF) } and 0xFF).toByte()
            return body + checksum
        }
    }
}
