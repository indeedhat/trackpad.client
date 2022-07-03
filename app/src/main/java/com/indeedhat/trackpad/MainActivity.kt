package com.indeedhat.trackpad

import android.content.Context
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import com.indeedhat.trackpad.net.WebSocketHanler
import com.indeedhat.trackpad.ui.ConnectFragment
import com.indeedhat.trackpad.ui.TrackpadFragment
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

class MainActivity : AppCompatActivity() {
    private lateinit var ws: WebSocketHanler

    private lateinit var keeb: Button
    private lateinit var leftClick: Button
    private lateinit var rightClick: Button
    private lateinit var trackpad: View

    private lateinit var input: EditText
    private lateinit var connect: Button
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
        ws.disconnect()
    }

    public fun openControlsView() {
        leftClick  = findViewById<Button>(R.id.left_click)
        rightClick = findViewById<Button>(R.id.right_click)
        keeb       = findViewById<Button>(R.id.keeb)
        trackpad   = findViewById<View>(R.id.trackpad)

        leftClick.setOnClickListener{
            ws.sendLeftClick();
        }
        rightClick.setOnClickListener{
            ws.sendRightClick()
        }
        keeb.setOnClickListener{ view ->
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
        }

        var downX: Float? = null
        var downY: Float? = null
        var prevX: Float? = null
        var prevY: Float? = null

        trackpad.setOnTouchListener(View.OnTouchListener { View, event ->
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

                    if (event.getX(0) == downX && event.getY(0) == downY) {
                        if (event.pointerCount > 1) {
                            // TODO: this doesn't really work
                            ws.sendRightClick()
                        } else {
                            ws.sendLeftClick()
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
                            ws.sendScroll(x - prevX!!, y - prevY!!)
                        } else {
                            ws.sendMotion(x - prevX!!, y - prevY!!)
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
        input   = findViewById<EditText>(R.id.connect_ip)
        connect = findViewById<Button>(R.id.connect_button)

        addressList = findViewById<ListView>(R.id.listView1)
        addressList.setOnItemClickListener { adapterView, view, i, l ->
            input.setText(addresses[i])
        }
        addressListAdapter  = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, addresses)
        addressList.adapter = addressListAdapter

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
            ws = WebSocketHanler("ws://${input.text}/ws", {
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
                if (parts.size != 3 || parts[0] != "trackpad.server") {
                    continue
                }

                val server = "${rcv.address.hostName}:${parts[1]}"
                if (addresses.indexOf(server) > -1) {
                    continue
                }

                runOnUiThread {
                    addressListAdapter.add(server)
                }
            }

            lock.release()
            lock = null
        }.start()
    }
}