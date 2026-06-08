package org.openmultitrack.app.scribble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.openmultitrack.app.util.AppLogBuffer
import org.openmultitrack.audio.OmtLog
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
 * Requires pairing mode on the mixer (~15 s per button press). The in-app default scan
 * window matches that window; instrumented tests may pass a longer [discoveryTimeoutMs].
 */
class Flow8BleScribbleImporter(
    private val context: Context,
    private val discoveryTimeoutMs: Long = DEFAULT_DISCOVERY_TIMEOUT_MS,
    private val onStatus: (String) -> Unit = {},
) {
    suspend fun fetchChannelLabels(): Result<List<UsbChannelScribble>> = withContext(Dispatchers.IO) {
        runCatching {
            val device = findFlowDevice() ?: error(PAIRING_NOT_FOUND_MESSAGE)
            reportStatus("FLOW 8 found — reading channel names…")
            fetchFromDevice(device)
        }
    }

    private fun reportStatus(message: String) {
        OmtLog.i("Flow8Ble", message)
        AppLogBuffer.append("I", "Flow8Ble", message)
        onStatus(message)
    }

    @SuppressLint("MissingPermission")
    private suspend fun findFlowDevice(): BluetoothDevice? {
        val adapter = bluetoothAdapter() ?: error("Bluetooth is not available on this device")
        if (!adapter.isEnabled) error("Bluetooth is off — turn it on and try again")

        reportStatus(PAIRING_PROMPT_MESSAGE)
        OmtLog.i("Flow8Ble", "scanning ${discoveryTimeoutMs}ms for FLOW 8 in pairing mode")

        if (discoveryTimeoutMs <= DEFAULT_DISCOVERY_TIMEOUT_MS) {
            return scanOnce(adapter, discoveryTimeoutMs)
        }

        val deadline = System.currentTimeMillis() + discoveryTimeoutMs
        var round = 0
        while (System.currentTimeMillis() < deadline) {
            round++
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            if (round > 1) {
                reportStatus("Still scanning… press the FLOW 8 Bluetooth pairing button again")
            }
            scanOnce(adapter, remaining)?.let { return it }
            delay(BETWEEN_ROUNDS_MS)
        }
        return null
    }

    private suspend fun scanOnce(adapter: BluetoothAdapter, budgetMs: Long): BluetoothDevice? {
        val filteredMs = budgetMs / 2
        val unfilteredMs = budgetMs - filteredMs
        // Official app scans by service UUID and only connects when pairing flag is set.
        scanBurst(adapter, filtered = true, timeoutMs = filteredMs)?.let { return it }
        if (unfilteredMs > 0) {
            scanBurst(adapter, filtered = false, timeoutMs = unfilteredMs)?.let { return it }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun scanBurst(
        adapter: BluetoothAdapter,
        filtered: Boolean,
        timeoutMs: Long,
    ): BluetoothDevice? = withTimeoutOrNull(timeoutMs) {
        val mainHandler = Handler(Looper.getMainLooper())
        suspendCancellableCoroutine { cont ->
            val scanner = adapter.bluetoothLeScanner
                ?: run {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    val dev = result?.device ?: return
                    val record = result.scanRecord
                    val name = dev.name ?: record?.deviceName
                    val pairing = isInPairingMode(result)
                    OmtLog.i("Flow8Ble", "saw ${dev.address} name=$name pairing=$pairing")
                    if (isFlowDevice(dev, result) && pairing) {
                        LastKnownFlow8Store.save(context, dev.address)
                        mainHandler.post { runCatching { scanner.stopScan(this) } }
                        if (cont.isActive) cont.resume(dev)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    OmtLog.w("Flow8Ble", "scan failed code=$errorCode (filtered=$filtered)")
                    mainHandler.post { runCatching { scanner.stopScan(this) } }
                    if (cont.isActive) cont.resume(null)
                }
            }
            cont.invokeOnCancellation {
                mainHandler.post { runCatching { scanner.stopScan(callback) } }
            }
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setReportDelay(0L)
                .build()
            mainHandler.post {
                adapter.cancelDiscovery()
                val started = runCatching {
                    if (filtered) {
                        val filters = listOf(
                            ScanFilter.Builder()
                                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                                .build(),
                        )
                        scanner.startScan(filters, settings, callback)
                    } else {
                        scanner.startScan(callback)
                    }
                }
                if (started.isFailure) {
                    OmtLog.w("Flow8Ble", "startScan failed: ${started.exceptionOrNull()?.message}")
                    if (cont.isActive) cont.resume(null)
                } else {
                    OmtLog.i("Flow8Ble", "startScan active (filtered=$filtered, ${timeoutMs}ms)")
                }
            }
        }
    }

    private fun isFlowDevice(device: BluetoothDevice, result: ScanResult? = null): Boolean {
        if (matchesFlowDevice(device.name)) return true
        val record = result?.scanRecord
        if (matchesFlowDevice(record?.deviceName)) return true
        return record?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true
    }

    private fun matchesFlowDevice(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val upper = name.uppercase()
        return upper.contains("FLOW 8") || upper.contains("FLOW8") || upper == "FLOW"
    }

    /** Pairing-mode flag in BLE advertisement (Flow Mix app: scan record byte 24). */
    private fun isInPairingMode(result: ScanResult?): Boolean {
        val bytes = result?.scanRecord?.bytes ?: return false
        return bytes.size > PAIRING_FLAG_BYTE_INDEX &&
            (bytes[PAIRING_FLAG_BYTE_INDEX].toInt() and 0xFF) > 0
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchFromDevice(device: BluetoothDevice): List<UsbChannelScribble> {
        val mainHandler = Handler(Looper.getMainLooper())
        return suspendCancellableCoroutine { cont ->
            val clientId = ClientIdStore.load(context)
            val session = Flow8BleSession(device, clientId) { result ->
                if (cont.isActive) {
                    result.fold(
                        onSuccess = { cont.resume(it) },
                        onFailure = { cont.resumeWithException(it) },
                    )
                }
            }
            cont.invokeOnCancellation { mainHandler.post { session.close() } }
            mainHandler.post { session.start(context) }
        }
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return manager.adapter
    }

    private object LastKnownFlow8Store {
        private const val PREFS = "flow8_ble"
        private const val KEY_ADDRESS = "last_address"

        fun load(context: Context): String? =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ADDRESS, null)

        fun save(context: Context, address: String) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ADDRESS, address)
                .apply()
        }
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
        private enum class ConnectionState {
            Connecting,
            RequestingMtu,
            DiscoveringServices,
            WaitingForHandshake,
            WaitingForHandshakeReply,
            CollectingState,
        }

        private var gatt: BluetoothGatt? = null
        private var characteristic: BluetoothGattCharacteristic? = null
        private val fragments = linkedMapOf<Int, ByteArray>()
        private var fragmentTotal = 0
        private var connectionState = ConnectionState.Connecting
        private var completed = false
        private val mainHandler = Handler(Looper.getMainLooper())
        private val writeQueue = ArrayDeque<ByteArray>()
        private var writeInFlight = false

        @SuppressLint("MissingPermission")
        fun start(context: Context) {
            // Match Flow Mix: connectGatt(context, false, callback) — no refresh(), no CCCD write.
            gatt = device.connectGatt(context, false, gattCallback)
        }

        fun close() {
            runCatching { gatt?.close() }
            gatt = null
        }

        private val gattCallback = object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (gatt != this@Flow8BleSession.gatt) return
                if (newState != BluetoothProfile.STATE_CONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_DISCONNECTED && !completed) {
                        OmtLog.w("Flow8Ble", "GATT disconnected status=$status state=$connectionState")
                        fail("FLOW 8 disconnected during BLE import (status=$status)")
                    }
                    return
                }
                OmtLog.i("Flow8Ble", "GATT connected")
                if (connectionState == ConnectionState.Connecting) {
                    connectionState = ConnectionState.RequestingMtu
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    gatt.requestMtu(DEFAULT_MTU)
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (gatt != this@Flow8BleSession.gatt) return
                OmtLog.i("Flow8Ble", "MTU=$mtu status=$status")
                if (connectionState == ConnectionState.RequestingMtu) {
                    connectionState = ConnectionState.DiscoveringServices
                    gatt.discoverServices()
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (gatt != this@Flow8BleSession.gatt) return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail("GATT service discovery failed (status=$status)")
                    return
                }
                val service = gatt.getService(SERVICE_UUID) ?: run {
                    fail("FLOW 8 GATT service not found")
                    return
                }
                characteristic = service.getCharacteristic(CHAR_UUID) ?: run {
                    fail("FLOW 8 GATT characteristic not found")
                    return
                }
                val char = characteristic!!
                if (!gatt.setCharacteristicNotification(char, true)) {
                    fail("FLOW 8 notify subscription failed")
                    return
                }
                connectionState = ConnectionState.WaitingForHandshake
                OmtLog.i("Flow8Ble", "waiting for HandshakeHost (0x35)")
            }

            @SuppressLint("MissingPermission")
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (gatt != this@Flow8BleSession.gatt) return
                writeInFlight = false
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    OmtLog.w("Flow8Ble", "GATT write status=$status")
                    fail("FLOW 8 GATT write failed (status=$status)")
                    return
                }
                drainWriteQueue(gatt)
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
                        if (connectionState != ConnectionState.WaitingForHandshake) return
                        OmtLog.i("Flow8Ble", "HandshakeHost (0x35) len=${data.size}")
                        connectionState = ConnectionState.WaitingForHandshakeReply
                        // Official app spaces GATT writes ~200 ms; never write from this callback directly.
                        mainHandler.postDelayed({
                            queuePacket(gatt, frame(T_HANDSHAKE_CLIENT, clientId))
                        }, WRITE_GAP_MS)
                    }
                    T_HANDSHAKE_REPLY -> {
                        if (connectionState != ConnectionState.WaitingForHandshakeReply) return
                        OmtLog.i("Flow8Ble", "HandshakeReply (0x36)")
                        connectionState = ConnectionState.CollectingState
                        mainHandler.postDelayed({
                            queuePacket(gatt, frame(T_GET_MIXER_STATE, byteArrayOf()))
                        }, WRITE_GAP_MS)
                    }
                    T_MIXER_STATE -> {
                        if (connectionState != ConnectionState.CollectingState) return
                        if (data.size < 4) return
                        fragmentTotal = data[1].toInt() and 0xFF
                        val seq = data[3].toInt() and 0xFF
                        fragments[seq] = data.copyOfRange(4, data.size)
                        OmtLog.i("Flow8Ble", "MixerState fragment ${fragments.size}/$fragmentTotal")
                        if (fragmentTotal > 0 && fragments.size >= fragmentTotal) {
                            finishDump()
                        }
                    }
                }
            }

            @SuppressLint("MissingPermission")
            private fun queuePacket(gatt: BluetoothGatt, bytes: ByteArray) {
                writeQueue.addLast(bytes)
                drainWriteQueue(gatt)
            }

            @SuppressLint("MissingPermission")
            private fun drainWriteQueue(gatt: BluetoothGatt) {
                if (writeInFlight || writeQueue.isEmpty()) return
                val char = characteristic ?: return
                val bytes = writeQueue.removeFirst()
                OmtLog.i("Flow8Ble", "write 0x${"%02X".format(bytes[0])} len=${bytes.size}")
                writeInFlight = true
                @Suppress("DEPRECATION")
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                char.value = bytes
                @Suppress("DEPRECATION")
                if (!gatt.writeCharacteristic(char)) {
                    writeInFlight = false
                    fail("FLOW 8 writeCharacteristic failed")
                }
            }

            private fun fail(message: String) {
                if (completed) return
                completed = true
                onComplete(Result.failure(IllegalStateException(message)))
                close()
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
                completed = true
                OmtLog.i("Flow8Ble", "decoded ${labels.size} channel names")
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
        /** Matches FLOW 8 pairing visibility (~15 s per button press). */
        const val DEFAULT_DISCOVERY_TIMEOUT_MS = 15_000L
        /** Longer window for instrumented hardware tests (operator can re-enable pairing). */
        const val INSTRUMENTED_DISCOVERY_TIMEOUT_MS = 90_000L

        const val PAIRING_PROMPT_MESSAGE =
            "FLOW 8: press the Bluetooth pairing button now — pairing stays active ~15 s"
        const val PAIRING_NOT_FOUND_MESSAGE =
            "FLOW 8 not found in pairing mode. Press the Bluetooth pairing button and tap Import again."

        private const val BETWEEN_ROUNDS_MS = 100L
        private const val PAIRING_FLAG_BYTE_INDEX = 24

        private const val CHANNEL_COUNT = 7
        private const val DEFAULT_MTU = 255
        private const val WRITE_GAP_MS = 200L
        private val SERVICE_UUID = UUID.fromString("14839ad4-8d7e-415c-9a42-167340cf2339")
        private val CHAR_UUID = UUID.fromString("0034594a-a8e7-4b1a-a6b1-cd5243059a57")

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
