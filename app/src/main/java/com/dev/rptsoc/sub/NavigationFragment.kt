package com.dev.rpt.sub

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.car.CarNotConnectedException
import android.car.cluster.CarInstrumentClusterManager
import android.car.cluster.ClusterActivityState
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.*
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.dev.rptsoc.MainClusterActivity
import com.dev.rptsoc.R
import com.dev.rptsoc.SampleClusterServiceImpl

import kotlinx.android.synthetic.main.fragment_navigation.*

@SuppressLint("LongLogTag")
@RequiresApi(Build.VERSION_CODES.M)
class NavigationFragment : Fragment() {
    private var mDisplayManager: DisplayManager? = null
    private var mUnobscuredBounds: Rect? = null
    private val mDisplayListener: DisplayManager.DisplayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                val navDisplayId: Int = virtualDisplayId

                if (navDisplayId == displayId) {
                    try {
                        Log.i(TAG, "starttt setClusterActivityLaunchOptions")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            service?.setClusterActivityLaunchOptions(CarInstrumentClusterManager.CATEGORY_NAVIGATION, ActivityOptions.makeBasic().setLaunchDisplayId(displayId))
                            Log.i(TAG, "setClusterActivityLaunchOptions success")
                        }
                        mRegisteredNavDisplayId = displayId
                        service?.setClusterActivityState(CarInstrumentClusterManager.CATEGORY_NAVIGATION, ClusterActivityState.create(true, mUnobscuredBounds).toBundle())
                    } catch (e: CarNotConnectedException) {
                        Log.d(TAG, "---" + e.localizedMessage + " --- \n" + e.message)
                        /*throw IllegalStateException(
                            e.toString(), e
                        )*/
                    }
                }
            }

            override fun onDisplayRemoved(displayId: Int) {
                Log.i(TAG, "onDisplayRemoved")
                if (mRegisteredNavDisplayId == displayId) {
                    try {
                        mRegisteredNavDisplayId = Display.INVALID_DISPLAY
                        service?.setClusterActivityLaunchOptions(CarInstrumentClusterManager.CATEGORY_NAVIGATION, null)
                    } catch (e: CarNotConnectedException) {
                        // This can happen only during shutdown, ignore.
                    }
                }
            }

            override fun onDisplayChanged(displayId: Int) {}
        }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.i(TAG, "onCreateView")
        mDisplayManager = activity?.getSystemService(DisplayManager::class.java)
        mDisplayManager?.registerDisplayListener(mDisplayListener, Handler())

        return inflater.inflate(R.layout.fragment_navigation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        nav_surface.holder.addCallback(object : SurfaceHolder.Callback {
            @SuppressLint("LongLogTag")
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.i(TAG, "surfaceCreated, holder: $holder")
            }

            @SuppressLint("LongLogTag")
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                Log.i(TAG, ("surfaceChanged, holder: " + holder + ", size:" + width + "x" + height + ", format:" + format))

                //Create dummy unobscured area to report to navigation activity.
                mUnobscuredBounds = Rect(40, 0, width - 80, height - 40)
                if (mVirtualDisplay == null) {
                    mVirtualDisplay = createVirtualDisplay(holder.surface, width, height)
                } else {
                    mVirtualDisplay?.surface = holder.surface
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.i(TAG, ("surfaceDestroyed, holder: " + holder + ", detaching surface from" + " display, surface: " + holder.surface))
                // detaching surface is similar to turning off the display
                mVirtualDisplay?.surface = null
            }
        })
    }

    @SuppressLint("LongLogTag")
    private fun createVirtualDisplay(surface: Surface, width: Int, height: Int): VirtualDisplay {
        Log.i(TAG, ("createVirtualDisplay, surface: " + surface + ", width: " + width + "x" + height))
        return mDisplayManager?.createVirtualDisplay("Cluster-App-VD", width, height, 160, surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)!!
    }

    @SuppressLint("LongLogTag")
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
    }

    private val service: SampleClusterServiceImpl?
        get() = (activity as MainClusterActivity?)?.mService

    private val virtualDisplayId: Int
        private get() = if ((mVirtualDisplay != null && mVirtualDisplay?.display != null)) mVirtualDisplay?.display!!
            .displayId else Display.INVALID_DISPLAY

    companion object {
        const val TAG: String = "NAVIGATION_FRAGMENT_TAG"
        // Static because we want to keep alive this virtual display when navigating through
        // ViewPager (this fragment gets dynamically destroyed and created)
        private var mVirtualDisplay: VirtualDisplay? = null
        private var mRegisteredNavDisplayId: Int = Display.INVALID_DISPLAY
    }
}