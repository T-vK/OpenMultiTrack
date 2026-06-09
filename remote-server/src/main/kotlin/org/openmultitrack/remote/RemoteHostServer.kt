package org.openmultitrack.remote

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONObject
import org.openmultitrack.domain.remote.RemoteProtocol
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet

class RemoteHostServer(
    port: Int = RemoteProtocol.HTTP_PORT,
    private val authToken: String?,
    private val hostId: String? = null,
    private val hostName: String? = null,
    private val listener: Listener,
) : NanoWSD(port) {
    interface Listener {
        fun onClientConnected(sendToClient: (String) -> Unit)
        fun onClientMessage(json: String, sendToClient: (String) -> Unit)
        fun onClientDisconnected()
    }

    private val sockets = CopyOnWriteArraySet<RemoteSocket>()

    override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        if (session.uri == "/api/v1/status" && session.method == NanoHTTPD.Method.GET) {
            val body = JSONObject().apply {
                put("ok", true)
                put("protocolVersion", RemoteProtocol.VERSION)
                hostId?.let { put("hostId", it) }
                hostName?.let { put("name", it) }
            }.toString()
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/json",
                body,
            )
        }
        return super.serve(session)
    }

    override fun openWebSocket(handshake: NanoHTTPD.IHTTPSession): NanoWSD.WebSocket {
        if (handshake.uri != RemoteProtocol.WS_PATH) {
            return rejectingSocket(handshake, NanoWSD.WebSocketFrame.CloseCode.ProtocolError, "invalid path")
        }
        if (!authToken.isNullOrBlank()) {
            val auth = handshake.headers["authorization"] ?: handshake.headers["Authorization"]
            if (auth != "Bearer $authToken") {
                return rejectingSocket(handshake, NanoWSD.WebSocketFrame.CloseCode.PolicyViolation, "unauthorized")
            }
        }
        return RemoteSocket(handshake)
    }

    fun broadcast(text: String) {
        sockets.forEach { socket ->
            runCatching { socket.send(text) }
        }
    }

    fun clientCount(): Int = sockets.size

    private fun rejectingSocket(
        handshake: NanoHTTPD.IHTTPSession,
        code: NanoWSD.WebSocketFrame.CloseCode,
        reason: String,
    ): NanoWSD.WebSocket =
        object : NanoWSD.WebSocket(handshake) {
            override fun onOpen() {
                close(code, reason, false)
            }

            override fun onClose(
                closeCode: NanoWSD.WebSocketFrame.CloseCode?,
                reason: String?,
                initiatedByRemote: Boolean,
            ) = Unit

            override fun onMessage(message: NanoWSD.WebSocketFrame) = Unit

            override fun onPong(pong: NanoWSD.WebSocketFrame) = Unit

            override fun onException(exception: IOException) = Unit
        }

    private inner class RemoteSocket(handshake: NanoHTTPD.IHTTPSession) : NanoWSD.WebSocket(handshake) {
        override fun onOpen() {
            sockets.add(this)
            listener.onClientConnected { msg -> send(msg) }
        }

        override fun onClose(
            code: NanoWSD.WebSocketFrame.CloseCode?,
            reason: String?,
            initiatedByRemote: Boolean,
        ) {
            sockets.remove(this)
            if (sockets.isEmpty()) {
                listener.onClientDisconnected()
            }
        }

        override fun onMessage(message: NanoWSD.WebSocketFrame) {
            val text = message.textPayload ?: return
            listener.onClientMessage(text) { reply -> send(reply) }
        }

        override fun onPong(pong: NanoWSD.WebSocketFrame) = Unit

        override fun onException(exception: IOException) {
            sockets.remove(this)
        }
    }

    companion object {
        fun encodeAck(command: String, ok: Boolean, error: String? = null): String =
            JSONObject().apply {
                put("type", "command_ack")
                put("command", command)
                put("ok", ok)
                error?.let { put("error", it) }
            }.toString()
    }
}
