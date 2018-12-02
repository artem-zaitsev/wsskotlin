package ru.r3tam.wsskotlin

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

internal class WssListener(
        val onOpenAction: (wss: WebSocket, response: Response) -> Unit,
        val onMessageAction: (wss: WebSocket, text: String?, bytes: ByteString?) -> Unit,
        val onFailureAction: (wss: WebSocket, t: Throwable, response: Response?) -> Unit,
        val onClosedAction: (wss: WebSocket, code: Int, reason: String) -> Unit
) : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        onOpenAction(webSocket, response)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)
        onFailureAction(webSocket, t, response)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        onMessageAction(webSocket, text, null)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
        onMessageAction(webSocket, null, bytes)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)
        onClosedAction(webSocket, code, reason)
    }
}