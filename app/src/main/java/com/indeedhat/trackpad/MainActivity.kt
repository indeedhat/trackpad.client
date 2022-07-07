package com.indeedhat.trackpad

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.graphics.Color
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.indeedhat.trackpad.net.WebSocketHanler
import com.indeedhat.trackpad.ui.ConnectFragment
import com.indeedhat.trackpad.ui.TrackpadFragment
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

class MainActivity : AppCompatActivity() {
    companion object {
        public lateinit var instance: MainActivity
    }

    private lateinit var ws: WebSocketHanler

    private lateinit var keeb: View
    private lateinit var leftClick: View
    private lateinit var rightClick: View
    private lateinit var trackpad: View

    private lateinit var input: EditText
    private lateinit var connect: Button
    private lateinit var addressList: ListView
    private lateinit var addressListAdapter: ArrayAdapter<String>

    private var addresses = ArrayList<String>()
    private var hostnames = ArrayList<String>()

    private var keebOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        setContentView(R.layout.activity_main)
        serverDiscovery()

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_manager, ConnectFragment.newInstance(), "connect_page")
            .addToBackStack(null)
            .commit()
    }

    override fun onBackPressed() {
        if (this::ws.isInitialized && ws.isConnected) {
            ws.disconnect()
        } else {
            finish()
            moveTaskToBack(true)
        }
    }

    override fun onPause() {
        if (this::ws.isInitialized && ws.isConnected) {
            ws.disconnect()
        }
        super.onPause()
    }

    public fun openControlsView() {
        Thread.sleep(100)
        goFullScreen()

        leftClick  = findViewById<View>(R.id.left_click)
        rightClick = findViewById<View>(R.id.right_click)
        keeb       = findViewById<View>(R.id.keeb)
        trackpad   = findViewById<View>(R.id.trackpad)

        leftClick.setOnTouchListener(View.OnTouchListener{ View, event ->
            Log.d("touch", "left click")
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    leftClick.setBackgroundColor(Color.parseColor("#ffffff"))
                    ws.sendLeftClick(true)
                }
                MotionEvent.ACTION_UP -> {
                    leftClick.setBackgroundColor(Color.parseColor("#000000"))
                    ws.sendLeftClick(false)
                }
            }
            return@OnTouchListener true
        })

        rightClick.setOnTouchListener(View.OnTouchListener{ View, event ->
            Log.d("touch", "right click")
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    rightClick.setBackgroundColor(Color.parseColor("#ffffff"))
                    ws.sendRightClick(true)
                }
                MotionEvent.ACTION_UP -> {
                    rightClick.setBackgroundColor(Color.parseColor("#000000"))
                    ws.sendRightClick(false)
                }
            }
            return@OnTouchListener true
        })

        keeb.setOnTouchListener(View.OnTouchListener{ view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    keeb.setBackgroundColor(Color.parseColor("#ffffff"))
                    ws.sendRightClick(true)
                }
                MotionEvent.ACTION_UP -> {
                    keeb.setBackgroundColor(Color.parseColor("#000000"))
                    ws.sendRightClick(false)
                    showKeeb()
                }
            }
            return@OnTouchListener true
        })

        var prevX: Float? = null
        var prevY: Float? = null
        var isZoom = false
        var isScroll = false
        var self = this

        val gestureListener = GestureDetector(this, object: GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e2.pointerCount == 1 || isZoom) {
                    return false
                }
                if (Math.abs(distanceX) < 10 && Math.abs(distanceY) < 10) {
                    return false
                }

                isScroll = true
                ws.sendScroll(distanceX, distanceY)
                return true
            }

            override fun onSingleTapConfirmed(event: MotionEvent?): Boolean {
                if (event == null) {
                    return true
                }

                if (keebOpen) {
                    hideKeeb(trackpad)
                } else if (event.pointerCount == 1) {
                    ws.sendLeftClick(true)
                    ws.sendLeftClick(false)
                } else {
                    // this never gets triggered
                    ws.sendRightClick(true)
                    ws.sendRightClick(false)
                }

                return true
            }
        })

        val scaleListener = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleEnd(detector: ScaleGestureDetector?) {
                isZoom = false
                super.onScaleEnd(detector)
            }
            private val eventDelay = 50
            private var lastEvent: Long = 0
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (detector.scaleFactor < 0.01) {
                    return false
                }

                if (isScroll || lastEvent + eventDelay > detector.eventTime) {
                    return true
                }

                ws.sendZoom(detector.scaleFactor)
                isZoom = true
                lastEvent = detector.eventTime

                return true
            }
        })


        trackpad.setOnTouchListener(View.OnTouchListener { View, event ->
            if (gestureListener.onTouchEvent(event)) {
                return@OnTouchListener true
            } else if (event.pointerCount > 1 && scaleListener.onTouchEvent(event)) {
                return@OnTouchListener true
            }

            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    if (prevX != null && prevY != null) {
                        ws.sendMotion(event.x - prevX!!, event.y - prevY!!)
                    }

                    prevX = event.x
                    prevY = event.y
                }
                MotionEvent.ACTION_UP -> {
                    prevX = null
                    prevY = null
                    isZoom = false
                    isScroll = false
                }
            }

            return@OnTouchListener true
        })
    }

    public fun openConnectView() {
        exitFullScreen()

        input   = findViewById<EditText>(R.id.connect_ip)
        connect = findViewById<Button>(R.id.connect_button)

        addressList = findViewById<ListView>(R.id.listView1)
        addressList.setOnItemClickListener { adapterView, view, i, l ->
            input.setText(addresses[i])
        }
        addressListAdapter  = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, hostnames)
        addressList.adapter = addressListAdapter

        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                val url = input.text.toString()
                var uri = Uri.parse("ws://" + url)
                connect.isEnabled = uri.scheme == "ws"
                        && uri.host != ""
                        && uri.port > 0
                        && uri.path == ""

                if (addresses.indexOf(url) == -1) {
                    addressList.clearChoices()
                    addressList.requestLayout()
                }
            }
        })

        connect.setOnClickListener{
            ws = WebSocketHanler("ws://${input.text}/ws", {
                // password entry managed by the socket handler

                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_manager, TrackpadFragment.newInstance(), "trackpad_page")
                    .commit()
            }, {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_manager, ConnectFragment.newInstance(), "connect_page")
                    .commit()

                openConnectView()
            })
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (!this::ws.isInitialized) {
            return false
        }

        var code = event?.unicodeChar!!
        if (keyCode == 67) {
            code = 2408
        }

        // android back button/shift
        if (keyCode != 4 && keyCode != 59) {
            ws.sendKeyCode(code)
        }

        return super.onKeyUp(keyCode, event)
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

                val parts = rcv.data
                    .decodeToString()
                    .split(";")
                if (parts.size != 4 || parts[0] != "trackpad.server") {
                    continue
                }

                val server = "${rcv.address.hostName}:${parts[1]}"
                if (addresses.indexOf(server) > -1) {
                    continue
                }

                runOnUiThread {
                    addressListAdapter.add("${parts[2]}\n${server}")
                    addresses.add(server)
                }
            }

            lock.release()
            lock = null
        }.start()
    }

    private fun goFullScreen() {
        val windowInsetsController =
            ViewCompat.getWindowInsetsController(window.decorView) ?: return
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun exitFullScreen() {
        val windowInsetsController =
            ViewCompat.getWindowInsetsController(window.decorView) ?: return
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun showKeeb() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
        keebOpen = true
    }

    private fun hideKeeb(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.getApplicationWindowToken(), 0)
        keebOpen = false
    }
}
