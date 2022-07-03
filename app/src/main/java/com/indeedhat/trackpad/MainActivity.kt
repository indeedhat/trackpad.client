package com.indeedhat.trackpad

import android.content.Context
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import androidx.core.widget.addTextChangedListener
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketFrame
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

class MainActivity : AppCompatActivity() {
    private var prevX: Float? = null
    private var prevY: Float? = null
    private var ws: WebSocket? = null

    private var keeb: Button? = null
    private var leftClick: Button? = null
    private var rightClick: Button? = null
    private var trackpad: View? = null

    private lateinit var addressList: ListView
    private lateinit var addressListAdapter: ArrayAdapter<String>
    private var addresses = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        serverDiscovery()

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_manager, ConnectFragment.newInstance(), "connect_page")
            .addToBackStack(null)
            .commit()
    }

    override fun onBackPressed() {
        Log.d("kp", "back button pressed")
        if (ws?.isOpen!!) {
            Log.d("ws", "calling disconnect")
            ws?.disconnect()
        } else {
            Log.d("ws", "not connected")
        }
    }

    public fun openControlsView() {
        Log.d("view", "controls")

        leftClick = findViewById<Button>(R.id.left_click)
        leftClick?.setOnClickListener{
            sendLeftClick();
        }

        rightClick = findViewById<Button>(R.id.right_click)
        rightClick?.setOnClickListener{
            sendRightClick()
        }

        keeb = findViewById<Button>(R.id.keeb)
        keeb?.setOnClickListener{ view ->
            val input = (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            input.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
        }

        var downX: Float? = null
        var downY: Float? = null

        trackpad = findViewById<View>(R.id.trackpad)
        trackpad?.setOnTouchListener(View.OnTouchListener { View, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (downX == null) {
                        downX = event.getX(0)
                        downY = event.getY(0)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    prevX = null
                    prevY = null

                    Log.d("tap", "[${downX} -> ${event.x}} {${downY} -> ${event.y}}")

                    if (event.getX(0) == downX && event.getY(0) == downY) {
                        if (event.pointerCount > 1) {
                            sendRightClick()
                        } else {
                            sendLeftClick()
                        }
                    }
                    downX = null
                    downY = null
                }
                MotionEvent.ACTION_MOVE -> {
                    var x = event.x
                    var y = event.y

                    if (prevX != null && prevY != null) {
                        if (event.pointerCount > 1) {
                            sendScroll(x - prevX!!, y - prevY!!)
                        } else {
                            sendMotion(x - prevX!!, y - prevY!!)
                        }
                    }

                    prevX = x
                    prevY = y
                }
            }

            return@OnTouchListener true
        })
    }

    public fun openConnectView() {
        Log.d("func", "openConnectView")
        val input = findViewById<EditText>(R.id.connect_ip)
        val connect = findViewById<Button>(R.id.connect_button)

        addressListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, addresses)
        addressList = findViewById<ListView>(R.id.listView1)
        addressList.adapter = addressListAdapter
        addressList.setOnItemClickListener { adapterView, view, i, l ->
            input.setText(addresses[i])
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                if (addresses.indexOf(input.text.toString()) == -1) {
                    addressList.clearChoices()
                    addressList.requestLayout()
                }
            }
        })

        connect.setOnClickListener{
            val wsFactory = WebSocketFactory()
            Log.d("connect", "ws://${input.text}/ws")
            ws = wsFactory.createSocket("ws://192.168.0.10:8881/ws")!!

            Log.d("connect", "created")
            ws?.addListener(object: WebSocketAdapter() {
                override fun onConnected(
                    websocket: WebSocket?,
                    headers: MutableMap<String, MutableList<String>>?
                ) {
                    Log.d("connect", "connected")
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.fragment_manager, TrackpadFragment.newInstance(), "trackpad_page")
                        .commit()

                    super.onConnected(websocket, headers)
                }

                override fun onDisconnected(
                    websocket: WebSocket?,
                    serverCloseFrame: WebSocketFrame?,
                    clientCloseFrame: WebSocketFrame?,
                    closedByServer: Boolean
                ) {
                    Log.d("connect", "disconnected")
                    super.onDisconnected(websocket, serverCloseFrame, clientCloseFrame, closedByServer)
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.fragment_manager, ConnectFragment.newInstance(), "connect_page")
                        .commit()
                    openConnectView()
                }
            })

            Log.d("connect", "before")
            ws?.connectAsynchronously()
            Log.d("connect", "after")
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d("keycode", "${event?.unicodeChar} -- ${event?.keyCode}")

        var code = event?.unicodeChar
        if (keyCode == 67) {
            code = 2408
        }

        // android back button
        if (keyCode != 4 && keyCode != 59) {
            ws?.sendText("keeb,${code}")
        }

        return super.onKeyUp(keyCode, event)
    }

    private fun sendRightClick() {
        Log.d("click", "right click")
        ws?.sendText("click,right")
    }

    private fun sendLeftClick() {
        Log.d("click", "left click")
        ws?.sendText("click,left")
    }

    private fun sendMotion(x: Float, y: Float) {
        Log.d("motion", "x(${x}) y(${y})")
        ws?.sendText("move,${x},${y}")
    }

    private fun sendScroll(x: Float, y: Float) {
        Log.d("scroll", "x(${x}) y(${y})")
        ws?.sendText("scroll,${x},${y}")
    }

    private fun serverDiscovery() {
        Thread {
            val wifi = getApplicationContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
            var lock = wifi.createMulticastLock("multicastlock")
            lock.setReferenceCounted(true)
            lock.acquire()

            val group = InetAddress.getByName("239.2.39.0")
            val socket = MulticastSocket(8181)
            socket.joinGroup(group)

            while (true) {
                var buff = ByteArray(256)
                var rcv = DatagramPacket(buff, buff.size)
                socket.receive(rcv)

                val message = rcv.data.decodeToString()
                val parts = message.split(";")
                if (parts.size != 3 || parts[0] != "trackpad.server") {
                    continue
                }

                val server = "${rcv.address.hostName}:${parts[1]}"

                if (addresses.indexOf(server) == -1) {
                    runOnUiThread {
                        addressListAdapter.add(server)
                    }
                }
            }

            lock.release()
            lock = null
        }.start()
    }
}