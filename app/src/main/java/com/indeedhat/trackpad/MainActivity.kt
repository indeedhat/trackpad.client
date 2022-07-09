package com.indeedhat.trackpad

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
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
import com.indeedhat.trackpad.net.ServerDiscoveryHandler
import com.indeedhat.trackpad.ui.ConnectFragment
import com.indeedhat.trackpad.ui.TrackpadFragment
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

class MainActivity : AppCompatActivity() {
    companion object {
        public lateinit var instance: MainActivity
    }

    public lateinit var ws: WebSocketHanler
    public lateinit var discovery: ServerDiscoveryHandler

    public var keebOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        setContentView(R.layout.activity_main)

        discovery = ServerDiscoveryHandler()
        discovery.start()

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
        }.start()
    }

    public fun goFullScreen() {
        val windowInsetsController =
            ViewCompat.getWindowInsetsController(window.decorView) ?: return
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    public fun exitFullScreen() {
        val windowInsetsController =
            ViewCompat.getWindowInsetsController(window.decorView) ?: return
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    public fun showKeeb() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
        keebOpen = true
    }

    public fun hideKeeb(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.getApplicationWindowToken(), 0)
        keebOpen = false
    }
}
