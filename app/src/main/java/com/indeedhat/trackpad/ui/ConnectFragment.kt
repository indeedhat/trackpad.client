package com.indeedhat.trackpad.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.text.Editable
import android.widget.Button
import android.widget.ListView
import android.widget.ArrayAdapter
import android.text.TextWatcher
import android.net.Uri
import com.indeedhat.trackpad.MainActivity
import com.indeedhat.trackpad.R
import com.indeedhat.trackpad.net.WebSocketHanler

class ConnectFragment : Fragment() {
    private lateinit var input: EditText
    private lateinit var connect: Button
    private lateinit var addressList: ListView
    private lateinit var addressListAdapter: ArrayAdapter<String>

    private lateinit var activity: MainActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        activity = getActivity() as MainActivity
        activity.exitFullScreen()

        input   = activity.findViewById<EditText>(R.id.connect_ip)
        connect = activity.findViewById<Button>(R.id.connect_button)
        addressList = activity.findViewById<ListView>(R.id.listView1)
        addressList.setOnItemClickListener { adapterView, view, i, l ->
            input.setText(activity.discovery.addresses[i])
        }

        addressListAdapter  = ArrayAdapter(activity, android.R.layout.simple_list_item_single_choice, activity.discovery.serverList)
        addressList.adapter = addressListAdapter

        activity.discovery.onDiscovery = { address, hostname ->
            addressListAdapter.add("${hostname}\n${address}")
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                val url = input.text.toString()
                var uri = Uri.parse("ws://" + url)
                connect.isEnabled = uri.scheme == "ws"
                        && uri.host != ""
                        && uri.port > 0
                        && uri.path == ""

                if (activity.discovery.addresses.indexOf(url) == -1) {
                    addressList.clearChoices()
                    addressList.requestLayout()
                }
            }
        })

        connect.setOnClickListener{
            activity.ws = WebSocketHanler("ws://${input.text}/ws", {
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_manager, TrackpadFragment.newInstance(), "trackpad_page")
                    .commit()
            }, {
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_manager, ConnectFragment.newInstance(), "connect_page")
                    .commit()

                onStart()
            })
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_connect, container, false)
    }

    companion object {
        @JvmStatic
        fun newInstance() =ConnectFragment().apply {
        }
    }
}
