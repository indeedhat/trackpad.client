package com.indeedhat.trackpad.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.indeedhat.trackpad.MainActivity
import com.indeedhat.trackpad.R

class TrackpadFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        (getActivity() as MainActivity).openControlsView()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_trackpad, container, false)
    }

    companion object {
        @JvmStatic
        fun newInstance() = TrackpadFragment().apply {

        }
    }
}
