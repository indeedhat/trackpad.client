package com.indeedhat.trackpad.net

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.EditText
import android.text.InputType
import android.text.TextUtils
import android.text.method.PasswordTransformationMethod
import com.indeedhat.trackpad.MainActivity
import com.neovisionaries.ws.client.*

class WebSocketHanler {
    public var isAuthenticated: Boolean = false
    public val isConnected: Boolean
        get() = socket.isOpen

    private var connectCb: () -> Unit
    private var disconnectCb: () -> Unit

    private var socket: WebSocket

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

            override fun onBinaryMessage(websocket: WebSocket?, binary: ByteArray?) {
                Log.d("message", "binary")
                super.onBinaryMessage(websocket, binary)
            }

            override fun onMessageError(
                websocket: WebSocket?,
                cause: WebSocketException?,
                frames: MutableList<WebSocketFrame>?
            ) {
                Log.d("message", "error")
                super.onMessageError(websocket, cause, frames)
            }

            override fun onTextMessage(websocket: WebSocket?, data: String) {
                isAuthenticated = data == "auth;true"

                if (isAuthenticated) {
                    onConnect()
                } else {
                    handleAuth(onConnect)
                }

                super.onTextMessage(websocket, data)
            }
        })

        socket.connectAsynchronously()
    }

    public fun sendRightClick(down: Boolean) {
        socket.sendText("click;right;${down}")
    }

    public fun sendLeftClick(down: Boolean) {
        socket.sendText("click;left;${down}")
    }

    public fun sendMotion(x: Float, y: Float) {
        socket.sendText("move;${x};${y}")
    }

    public fun sendScroll(x: Float, y: Float) {
        socket.sendText("scroll;${x};${y}")
    }

    public fun sendKeyCode(code: Int) {
        socket.sendText("keeb;${code}")
    }

    public fun sendPass(pass: String) {
        socket.sendText("pass;${pass}")
    }
    
    public fun sendZoom(amount: Float) {
        // always happens at the start/end of a zoom
        if (amount == 1f) {
            return
        }

        socket.sendText("zoom;${amount}")
    }

    public fun disconnect() {
        isAuthenticated = false
        socket.disconnect()
    }

    private fun handleAuth(onComplete: () -> Unit) {
        Log.d("auth", "sleeping")
        Thread.sleep(100)

        Log.d("auth", "handle")
        if (isAuthenticated) {
            Log.d("auth", "authenticated")
            onComplete()
            return
        }

        val context = MainActivity.instance
        context.runOnUiThread{
            val input = EditText(context)
            input.isSingleLine = true
            input.hint = "Password"
            input.ellipsize = TextUtils.TruncateAt.START
            input.transformationMethod = PasswordTransformationMethod.getInstance()

            val builder = AlertDialog.Builder(context)
            builder.setTitle("Enter Password")
                .setView(input)
                .setPositiveButton("Submit") { dialog, _ ->
                    val passwd = input.text.toString()
                    sendPass(passwd)
                    dialog.cancel()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.cancel()
                }
                .show();

            Log.d("auth", "showing")
        }
    }
}
