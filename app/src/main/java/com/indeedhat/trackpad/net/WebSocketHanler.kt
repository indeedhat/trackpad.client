package com.indeedhat.trackpad.net

import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketFrame

class WebSocketHanler {
    public val isConnected: Boolean
        get() = socket.isOpen

    private lateinit var connectCb: () -> Unit
    private lateinit var disconnectCb: () -> Unit

    private lateinit var socket: WebSocket

    constructor(url: String, onConnect: () -> Unit, onDisconnect: () -> Unit) {
        connectCb = onConnect
        disconnectCb = onDisconnect

        val factory = WebSocketFactory()
        socket = factory.createSocket(url)!!

        socket.addListener(object: WebSocketAdapter() {
            override fun onConnected(
                websocket: WebSocket?,
                headers: MutableMap<String, MutableList<String>>?
            ) {
                onConnect()
                super.onConnected(websocket, headers)
            }

            override fun onDisconnected(
                websocket: WebSocket?,
                serverCloseFrame: WebSocketFrame?,
                clientCloseFrame: WebSocketFrame?,
                closedByServer: Boolean
            ) {
                onDisconnect()
                super.onDisconnected(websocket, serverCloseFrame, clientCloseFrame, closedByServer)
            }
        })

        socket.connectAsynchronously()
    }

    public fun sendRightClick(down: Boolean) {
        socket.sendText("click,right,${down}")
    }

    public fun sendLeftClick(down: Boolean) {
        socket.sendText("click,left,${down}")
    }

    public fun sendMotion(x: Float, y: Float) {
        socket.sendText("move,${x},${y}")
    }

    public fun sendScroll(x: Float, y: Float) {
        socket.sendText("scroll,${x},${y}")
    }

    public fun sendKeyCode(code: Int) {
        socket.sendText("keeb,${code}")
    }

    public fun disconnect() {
        socket.disconnect()
    }

}