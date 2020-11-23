package com.dev.rpt

import android.app.ActivityOptions
import android.car.CarNotConnectedException

import android.car.cluster.ClusterActivityState

import android.car.cluster.renderer.InstrumentClusterRenderingService

import android.car.cluster.renderer.NavigationRenderer

import android.car.navigation.CarNavigationInstrumentCluster

import android.content.Intent
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent.obtain
import androidx.annotation.RequiresApi
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.*


@RequiresApi(Build.VERSION_CODES.M)
class SampleClusterServiceImpl : InstrumentClusterRenderingService() {
    private var mListener: Listener? = null
    private val mLocalBinder: Binder = LocalBinder()
    private var mDisplayProvider: ClusterDisplayProvider? = null
    override fun onBind(intent: Intent): IBinder? {
        Log.i(TAG, "onBind, intent: $intent")
        return if (LOCAL_BINDING_ACTION == intent.action) mLocalBinder else super.onBind(
            intent
        )
    }


    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        mDisplayProvider = ClusterDisplayProvider(this,
            object : DisplayManager.DisplayListener {
                @RequiresApi(Build.VERSION_CODES.O)
                override fun onDisplayAdded(displayId: Int) {
                    Log.i(
                        TAG,
                        "Cluster display found, displayId: $displayId"
                    )
                    doClusterDisplayConnected(displayId)
                }

                override fun onDisplayRemoved(displayId: Int) {
                    Log.w(
                        TAG,
                        "Cluster display has been removed"
                    )
                }

                override fun onDisplayChanged(displayId: Int) {}
            })
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun doClusterDisplayConnected(displayId: Int) {
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = displayId
        val intent = Intent(this, MainClusterActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent, options.toBundle())
    }

    override fun onKeyEvent(keyEvent: KeyEvent) {
        Log.i(
            "ChoanhChoanh",
            "onKeyEvent, keyEvent: $keyEvent, listener: $mListener"
        )
        if (mListener != null) {
            mListener!!.onKeyEvent(keyEvent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "onDestroy")
    }

    fun registerListener(listener: Listener?) {
        mListener = listener
    }

    fun unregisterListener() {
        mListener = null
    }

    override fun getNavigationRenderer(): NavigationRenderer {
        val navigationRenderer: NavigationRenderer = object : NavigationRenderer() {
            override fun getNavigationProperties(): CarNavigationInstrumentCluster {
                Log.i(
                    TAG,
                    "getNavigationProperties"
                )
                val config =
                    CarNavigationInstrumentCluster.createCluster(1000)
                Log.i(
                    TAG,
                    "getNavigationProperties, returns: $config"
                )
                return config
            }

            override fun onEvent(eventType: Int, bundle: Bundle) {
                val bundleSummary = StringBuilder()
                for (key in bundle.keySet()) {
                    bundleSummary.append(key)
                    bundleSummary.append("=")
                    bundleSummary.append(bundle[key])
                    bundleSummary.append(" ")
                }
                Log.i(
                    TAG,
                    "onEvent($eventType, $bundleSummary)"
                )
            }
        }
        Log.i(
            TAG,
            "createNavigationRenderer, returns: $navigationRenderer"
        )
        return navigationRenderer
    }

    internal inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        val service: SampleClusterServiceImpl
            get() =// Return this instance of LocalService so clients can call public methods
                SampleClusterServiceImpl()
    }

    interface Listener {
        fun onKeyEvent(event: KeyEvent?)
    }

    override fun dump(
        fd: FileDescriptor,
        writer: PrintWriter,
        args: Array<String>
    ) {
        if (args != null && args.size > 0) {
            execShellCommand(args)
        } else {
            if (args == null || args.size == 0) {
                writer.println("* dump " + javaClass.canonicalName + " *")
                writer.println("DisplayProvider: $mDisplayProvider")
            }
        }
    }

    private fun emulateKeyEvent(keyCode: Int) {
        Log.i(
            TAG,
            "emulateKeyEvent, keyCode: $keyCode"
        )
        val downTime = SystemClock.uptimeMillis()
        var eventTime = SystemClock.uptimeMillis()
        /*var event =
            obtainKeyEvent(keyCode, downTime, eventTime, KeyEvent.ACTION_DOWN)
        onKeyEvent(event)
        eventTime = SystemClock.uptimeMillis()
        event = obtainKeyEvent(keyCode, downTime, eventTime, KeyEvent.ACTION_UP)
        onKeyEvent(event)*/

    }

   /* private fun obtainKeyEvent(
        keyCode: Int,
        downTime: Long,
        eventTime: Long,
        action: Int
    ): KeyEvent {
        var scanCode = 0
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            scanCode = 108
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            scanCode = 106
        }
        return KeyEvent.obtain(
                downTime,
                eventTime,
                action,
                keyCode,
                0 *//* repeat *//*,
                0 *//* meta state *//*,
                0 *//* deviceId*//*,
                scanCode *//* scancode *//*,
                KeyEvent.FLAG_FROM_SYSTEM *//* flags *//*,
                InputDevice.SOURCE_KEYBOARD,
                null *//* characters *//*
        )
    }*/

    private fun execShellCommand(args: Array<String>) {
        Log.i(
            TAG,
            "execShellCommand, args: " + Arrays.toString(args)
        )
        val command = args[0]
        when (command) {
            "injectKey" -> {
                if (args.size > 1) {
                    emulateKeyEvent(args[1].toInt())
                } else {
                    Log.i(
                        TAG,
                        "Not enough arguments"
                    )
                }
            }
            /*"destroyOverlayDisplay" -> {
                Settings.Global.putString(
                    contentResolver,
                    Settings.Global.OVERLAY_DISPLAY_DEVICES, ""
                )
            }
            "createOverlayDisplay" -> {
                if (args.size > 1) {
                    Settings.Global.putString(
                        contentResolver,
                        Settings.Global.OVERLAY_DISPLAY_DEVICES, args[1]
                    )
                } else {
                    Log.i(
                        TAG,
                        "Not enough arguments, expected 2"
                    )
                }
            }*/
            "setUnobscuredArea" -> {
                if (args.size > 5) {
                    val unobscuredArea = Rect(
                        args[2].toInt(), args[3].toInt(),
                        args[4].toInt(), args[5].toInt()
                    )
                    try {
                        setClusterActivityState(
                            args[1],
                            ClusterActivityState.create(true, unobscuredArea).toBundle()
                        )
                    } catch (e: CarNotConnectedException) {
                        Log.i(
                            TAG,
                            "Failed to set activity state.",
                            e
                        )
                    }
                } else {
                    Log.i(
                        TAG,
                        "wrong format, expected: category left top right bottom"
                    )
                }
            }
        }
    }

    companion object {
        private val TAG = "ChoanhChoanh"
        const val LOCAL_BINDING_ACTION = "local"
    }
}
