package com.dev.rptsoc

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.M)
class ClusterDisplayProvider internal constructor(
    context: Context,
    private val mListener: DisplayManager.DisplayListener
) {
    private val mDisplayManager: DisplayManager = context.getSystemService(DisplayManager::class.java)
    private var mNetworkedVirtualDisplay: NetworkedVirtualDisplay? = null
    private var mClusterDisplayId = -1

    private fun setupNetworkDisplay(context: Context) {
        mNetworkedVirtualDisplay = NetworkedVirtualDisplay(
            context,
            NETWORKED_DISPLAY_WIDTH,
            NETWORKED_DISPLAY_HEIGHT,
            NETWORKED_DISPLAY_DPI
        )
        val displayName: String? = mNetworkedVirtualDisplay?.start()
        trackClusterDisplay(displayName)
    }

    private fun trackClusterDisplay(displayName: String?) {
        mDisplayManager.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                var clusterDisplayAdded = false
                if (displayName == null && mClusterDisplayId == -1) {
                    mClusterDisplayId = displayId
                    clusterDisplayAdded = true
                } else {
                    val display = mDisplayManager.getDisplay(displayId)
                    if (display != null && TextUtils.equals(display.name, displayName)) {
                        mClusterDisplayId = displayId
                        clusterDisplayAdded = true
                    }
                }
                if (clusterDisplayAdded) {
                    mListener.onDisplayAdded(displayId)
                }
            }

            override fun onDisplayRemoved(displayId: Int) {
                if (displayId == mClusterDisplayId) {
                    mClusterDisplayId = -1
                    mListener.onDisplayRemoved(displayId)
                }
            }

            override fun onDisplayChanged(displayId: Int) {
                if (displayId == mClusterDisplayId) {
                    mListener.onDisplayChanged(displayId)
                }
            }
        }, null)
    }

    override fun toString(): String {
        return (javaClass.simpleName + "{"
                + " clusterDisplayId = " + mClusterDisplayId
                + "}")
    }

    companion object {
        private val TAG = "ClusterDisplay_TAG"
        private const val NETWORKED_DISPLAY_WIDTH = 1280
        private const val NETWORKED_DISPLAY_HEIGHT = 720
        private const val NETWORKED_DISPLAY_DPI = 320
        private fun getInstrumentClusterDisplay(displayManager: DisplayManager): Display? {
            val displays = displayManager.displays
            Log.d(
                TAG,
                "There are currently " + displays.size + " displays connected."
            )
            return if (displays.size > 1) {
                // TODO: assuming that secondary display is instrument cluster. Put this into settings?
                // We could use name and ownerPackageName to verify this is the right display.
                displays[1]
            } else null
        }
    }

    init {
        val clusterDisplay =
            getInstrumentClusterDisplay(mDisplayManager)
        if (clusterDisplay != null) {
            mClusterDisplayId = clusterDisplay.displayId
            mListener.onDisplayAdded(clusterDisplay.displayId)
            trackClusterDisplay(null /* no need to track display by name */)
            Log.d(TAG, "" + mClusterDisplayId + " - " + clusterDisplay.name)
        } else {
            Log.i(
                TAG,
                "No physical cluster display found, starting network display"
            )
            setupNetworkDisplay(context)
        }
    }
}