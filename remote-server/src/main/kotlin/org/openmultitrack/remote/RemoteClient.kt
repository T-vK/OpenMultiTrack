package org.openmultitrack.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.openmultitrack.domain.remote.RemoteProtocol
import java.util.concurrent.TimeUnit

class RemoteClient(
  private val okHttp: OkHttpClient = defaultClient(),
  private val listener: Listener,
) {
  interface Listener {
    fun onConnected()
    fun onMessage(json: String)
    fun onDisconnected(reason: String?)
    fun onFailure(error: String)
  }

  @Volatile
  private var webSocket: WebSocket? = null

  fun connect(host: String, port: Int, authToken: String?) {
    disconnect()
    val request = Request.Builder()
      .url("ws://$host:$port${RemoteProtocol.WS_PATH}")
      .apply {
        if (!authToken.isNullOrBlank()) {
          header("Authorization", "Bearer $authToken")
        }
      }
      .build()
    webSocket = okHttp.newWebSocket(
      request,
      object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          listener.onConnected()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
          listener.onMessage(text)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
          listener.onDisconnected(reason.ifBlank { null })
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
          listener.onFailure(t.message ?: "connection failed")
        }
      },
    )
  }

  fun send(text: String): Boolean {
    val ws = webSocket ?: return false
    return runCatching { ws.send(text) }.getOrDefault(false)
  }

  fun disconnect() {
    webSocket?.close(1000, "client disconnect")
    webSocket = null
  }

  fun isConnected(): Boolean = webSocket != null

  companion object {
    fun defaultClient(): OkHttpClient =
      OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
  }
}
