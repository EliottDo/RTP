package com.dev.rpt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.dev.rpt.SampleClusterServiceImpl.Companion.LOCAL_BINDING_ACTION
import com.dev.rpt.sub.NavigationFragment
import kotlinx.android.synthetic.main.activity_main.*


class MainClusterActivity : FragmentActivity(), SampleClusterServiceImpl.Listener {
    var mService: SampleClusterServiceImpl? = null
    private var mInputMethodManager: InputMethodManager? = null


    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MAINNNN", "AAAAAA")
        setContentView(R.layout.activity_main)
        mInputMethodManager =
            getSystemService(InputMethodManager::class.java)
        val intent = Intent(this, SampleClusterServiceImpl::class.java)
        intent.action = LOCAL_BINDING_ACTION
        bindService(
            intent,
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName,
                    service: IBinder
                ) {
                    mService = (service as SampleClusterServiceImpl.LocalBinder)
                        .service
                    mService?.registerListener(this@MainClusterActivity)
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    mService = null
                }
            }, Context.BIND_AUTO_CREATE
        )
        pager.adapter = ClusterPageAdapter(supportFragmentManager)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onDestroy() {
        super.onDestroy()
        if (mService != null) {
            mService!!.unregisterListener()
        }
    }

    inner class ClusterPageAdapter(fm: FragmentManager?) :
        FragmentPagerAdapter(fm!!) {
        override fun getCount(): Int {
            return 1
        }

        @RequiresApi(Build.VERSION_CODES.M)
        override fun getItem(position: Int): Fragment {
            return NavigationFragment()
        }
    }

    companion object {
        private val TAG = MainClusterActivity::class.java.simpleName
    }

    override fun onKeyEvent(event: KeyEvent?) {
        Log.d("ChoanhChoanh", event.toString() + " ---- aa")
    }
}
