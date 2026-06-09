package org.openmultitrack.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.openmultitrack.domain.remote.RemoteProtocol
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.charset.StandardCharsets

object RemoteDiscovery {
  fun localIpv4(context: Context): String? {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return null
    val props = cm.getLinkProperties(network) ?: return null
    return props.linkAddresses
      .mapNotNull { it.address as? Inet4Address }
      .firstOrNull { !it.isLoopbackAddress }
      ?.hostAddress
  }

  suspend fun discoverHosts(
    context: Context,
    timeoutMs: Long = 3000,
  ): List<RemoteDiscoveredHost> = withContext(Dispatchers.IO) {
    withMulticastLock(context) {
      val found = linkedMapOf<String, RemoteDiscoveredHost>()
      DatagramSocket().use { socket ->
        socket.broadcast = true
        socket.soTimeout = 200
        val request = RemoteProtocol.DISCOVER_REQUEST.toByteArray(StandardCharsets.UTF_8)
        broadcastAddresses(context).forEach { addr ->
          runCatching {
            socket.send(
              DatagramPacket(
                request,
                request.size,
                addr,
                RemoteProtocol.DISCOVERY_PORT,
              ),
            )
          }
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        val buf = ByteArray(512)
        while (System.currentTimeMillis() < deadline) {
          try {
            val packet = DatagramPacket(buf, buf.size)
            socket.receive(packet)
            val sourceIp = (packet.address as? Inet4Address)?.hostAddress ?: continue
            parseAnnounce(String(packet.data, 0, packet.length, StandardCharsets.UTF_8), sourceIp)?.let { host ->
              found[host.host] = host
            }
          } catch (_: java.net.SocketTimeoutException) {
            // keep listening until deadline
          }
        }
      }
      found.values.toList()
    }
  }

  class HostAnnouncer(
    private val context: Context,
    private val hostName: String,
    private val port: Int = RemoteProtocol.HTTP_PORT,
  ) : AutoCloseable {
    @Volatile
    private var running = false
    private var thread: Thread? = null

    fun start() {
      if (running) return
      running = true
      thread = Thread(
        {
          withMulticastLockBlocking(context) {
            DatagramSocket(RemoteProtocol.DISCOVERY_PORT).use { socket ->
              socket.broadcast = true
              val buf = ByteArray(256)
              while (running) {
                try {
                  val packet = DatagramPacket(buf, buf.size)
                  socket.receive(packet)
                  val msg = String(packet.data, 0, packet.length, StandardCharsets.UTF_8).trim()
                  if (msg != RemoteProtocol.DISCOVER_REQUEST) continue
                  val reply = announceJson(hostName, port)
                  val bytes = reply.toByteArray(StandardCharsets.UTF_8)
                  socket.send(DatagramPacket(bytes, bytes.size, packet.address, packet.port))
                } catch (_: Exception) {
                  if (!running) break
                }
              }
            }
          }
        },
        "omt-remote-announcer",
      ).also { it.isDaemon = true; it.start() }
    }

    override fun close() {
      running = false
      thread?.interrupt()
      thread = null
    }
  }

  private fun announceJson(name: String, port: Int): String =
    JSONObject().apply {
      put("type", RemoteProtocol.DISCOVER_REPLY)
      put("name", name)
      put("host", "0.0.0.0") // replaced by receiver with packet source IP
      put("port", port)
      put("protocolVersion", RemoteProtocol.VERSION)
    }.toString()

  private fun parseAnnounce(json: String, sourceIp: String): RemoteDiscoveredHost? = runCatching {
    val root = JSONObject(json)
    if (root.optString("type") != RemoteProtocol.DISCOVER_REPLY) return null
    RemoteDiscoveredHost(
      name = root.optString("name", "OpenMultiTrack"),
      host = sourceIp,
      port = root.optInt("port", RemoteProtocol.HTTP_PORT),
      protocolVersion = root.optInt("protocolVersion", RemoteProtocol.VERSION),
    )
  }.getOrNull()

  private fun broadcastAddresses(context: Context): List<InetAddress> {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return listOf(InetAddress.getByName("255.255.255.255"))
    val props = cm.getLinkProperties(network) ?: return listOf(InetAddress.getByName("255.255.255.255"))
    val addrs = props.linkAddresses.mapNotNull { la ->
      val addr = la.address as? Inet4Address ?: return@mapNotNull null
      val prefix = la.prefixLength
      if (prefix <= 0) return@mapNotNull null
      val broadcast = broadcastFor(addr.address, prefix) ?: return@mapNotNull null
      InetAddress.getByAddress(broadcast)
    }
    return if (addrs.isEmpty()) listOf(InetAddress.getByName("255.255.255.255")) else addrs
  }

  private fun broadcastFor(ip: ByteArray, prefix: Int): ByteArray? {
    if (ip.size != 4 || prefix < 1 || prefix > 32) return null
    val mask = -1 shl (32 - prefix)
    val addr = ((ip[0].toInt() and 0xFF) shl 24) or
      ((ip[1].toInt() and 0xFF) shl 16) or
      ((ip[2].toInt() and 0xFF) shl 8) or
      (ip[3].toInt() and 0xFF)
    val bcast = addr or mask.inv()
    return byteArrayOf(
      ((bcast shr 24) and 0xFF).toByte(),
      ((bcast shr 16) and 0xFF).toByte(),
      ((bcast shr 8) and 0xFF).toByte(),
      (bcast and 0xFF).toByte(),
    )
  }

  private suspend fun <T> withMulticastLock(context: Context, block: suspend () -> T): T {
    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val lock = wifi.createMulticastLock("omt-remote-discovery")
    lock.setReferenceCounted(true)
    lock.acquire()
    try {
      return block()
    } finally {
      if (lock.isHeld) lock.release()
    }
  }

  private fun withMulticastLockBlocking(context: Context, block: () -> Unit) {
    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val lock = wifi.createMulticastLock("omt-remote-announcer")
    lock.setReferenceCounted(true)
    lock.acquire()
    try {
      block()
    } finally {
      if (lock.isHeld) lock.release()
    }
  }
}
