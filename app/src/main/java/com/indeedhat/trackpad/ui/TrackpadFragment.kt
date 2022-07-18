package com.indeedhat.trackpad.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.core.view.ScaleGestureDetectorCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.indeedhat.trackpad.MainActivity
import com.indeedhat.trackpad.R
import android.view.*
import android.util.Log
import android.graphics.Color

class TrackpadFragment : Fragment() {
    private lateinit var keeb: View
    private lateinit var leftClick: View
    private lateinit var rightClick: View
    private lateinit var trackpad: View

    private lateinit var activity: MainActivity

    private var prevX: Float? = null
    private var prevY: Float? = null
    private var isZoom = false
    private var isScroll = false

    private lateinit var _gestureListener: GestureDetector;
    private val gestureListener: GestureDetector
        get() {
            if (!this::_gestureListener.isInitialized) {
                _gestureListener = initGestureListener()
            }

            return _gestureListener
        }

    private lateinit var _scaleListener: ScaleGestureDetector
    private val scaleListener: ScaleGestureDetector
        get() {
            if (!this::_scaleListener.isInitialized) {
                _scaleListener = initScaleListener()
            }

            return _scaleListener
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        activity = getActivity() as MainActivity

        Thread.sleep(100)
        //activity.goFullScreen()

        leftClick  = activity.findViewById<View>(R.id.left_click)
        rightClick = activity.findViewById<View>(R.id.right_click)
        keeb       = activity.findViewById<View>(R.id.keeb)
        trackpad   = activity.findViewById<View>(R.id.trackpad)

        leftClick.setOnTouchListener(View.OnTouchListener{ view, event ->
            return@OnTouchListener handleLeftButtonTouch(view, event)
        })

        rightClick.setOnTouchListener(View.OnTouchListener{ view, event ->
            return@OnTouchListener handleRightClickButtonTouch(view, event)
        })

        keeb.setOnTouchListener(View.OnTouchListener{ view, event ->
            return@OnTouchListener handleKeebTouch(view, event)
        })

        trackpad.setOnTouchListener(View.OnTouchListener { view, event ->
            return@OnTouchListener handleTrackpadTouch(view, event)
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_trackpad, container, false)
    }

    companion object {
        @JvmStatic
        fun newInstance() = TrackpadFragment().apply {}
    }

    private fun handleLeftButtonTouch(v: View, event: MotionEvent): Boolean {
        Log.d("touch", "left click")
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                leftClick.setBackgroundColor(Color.parseColor("#ffffff"))
                activity.ws.sendLeftClick(true)
            }
            MotionEvent.ACTION_UP -> {
                leftClick.setBackgroundColor(Color.parseColor("#000000"))
                activity.ws.sendLeftClick(false)
            }
        }

        return true
    }

    private fun handleRightClickButtonTouch(v: View, event: MotionEvent): Boolean {
        Log.d("touch", "right click")
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                rightClick.setBackgroundColor(Color.parseColor("#ffffff"))
                activity.ws.sendRightClick(true)
            }
            MotionEvent.ACTION_UP -> {
                rightClick.setBackgroundColor(Color.parseColor("#000000"))
                activity.ws.sendRightClick(false)
            }
        }

        return true
    }

    private fun handleKeebTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                keeb.setBackgroundColor(Color.parseColor("#ffffff"))
            }
            MotionEvent.ACTION_UP -> {
                keeb.setBackgroundColor(Color.parseColor("#000000"))
                activity.showKeeb()
            }
        }

        return true
    }

    private fun handleTrackpadTouch(v: View, event: MotionEvent): Boolean {
        if (gestureListener.onTouchEvent(event)) {
            return true
        } else if (event.pointerCount > 1 && scaleListener.onTouchEvent(event)) {
            return true
        }

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if (prevX != null && prevY != null) {
                    activity.ws.sendMotion(event.x - prevX!!, event.y - prevY!!)
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

        return true
    }

    private fun initGestureListener(): GestureDetector {
        return GestureDetector(activity, object: GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e2.pointerCount == 1 || isZoom) {
                    return false
                }
                if (!isScroll && (Math.abs(distanceX) < 10 && Math.abs(distanceY) < 10)) {
                    return false
                }

                isScroll = true
                var x = 0f
                var y = 0f

                activity.ws.sendScroll(distanceX / 10, distanceY / 10)

                return true
            }

            override fun onSingleTapConfirmed(event: MotionEvent?): Boolean {
                if (event == null) {
                    return true
                }

                if (activity.keebOpen) {
                    activity.hideKeeb(trackpad)
                } else if (event.pointerCount == 1) {
                    activity.ws.sendLeftClick(true)
                    activity.ws.sendLeftClick(false)
                } else {
                    // this never gets triggered
                    activity.ws.sendRightClick(true)
                    activity.ws.sendRightClick(false)
                }

                return true
            }

            override fun onDoubleTap(e: MotionEvent?): Boolean {
                activity.ws.sendLeftClick(true)
                activity.ws.sendLeftClick(false)
                activity.ws.sendLeftClick(true)
                activity.ws.sendLeftClick(false)

                return true
            }
        })
    }

    private fun initScaleListener(): ScaleGestureDetector {
        return ScaleGestureDetector(activity, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
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

                activity.ws.sendZoom(detector.scaleFactor)
                isZoom = true
                lastEvent = detector.eventTime

                return true
            }
        })
    }
}
