package com.indeedhat.trackpad.net

import com.indeedhat.trackpad.MainActivity
import android.content.Context
import android.net.wifi.WifiManager
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.DatagramPacket

class ServerDiscoveryHandler: Thread() {
    public var addresses = ArrayList<String>()
    public var serverList = ArrayList<String>()

    private lateinit var _onDiscovery: (address: String, hostname: String) -> Unit
    public var onDiscovery: (address: String, hostname: String) -> Unit
        get() = _onDiscovery
        set(value) {
            _onDiscovery = value
        }

    override public fun run() {
        var activity = MainActivity.instance;

        val wifi = activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
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

            activity.runOnUiThread {
                addresses.add(server)

                if (this::_onDiscovery.isInitialized) {
                    _onDiscovery(parts[2], server)
                }
            }
        }

        lock.release()
        lock = null
    }
}
