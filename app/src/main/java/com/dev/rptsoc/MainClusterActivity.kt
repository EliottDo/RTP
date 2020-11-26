package com.dev.rptsoc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.util.SparseArray
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View.OnFocusChangeListener
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.dev.rpt.sub.NavigationFragment
import com.dev.rptsoc.SampleClusterServiceImpl.LOCAL_BINDING_ACTION
import com.dev.rptsoc.sub.CarInfoFragment
import com.dev.rptsoc.sub.MusicFragment
import com.dev.rptsoc.sub.PhoneFragment
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.reflect.InvocationTargetException
import java.util.*


class MainClusterActivity : FragmentActivity(), SampleClusterServiceImpl.Listener {
    private var mInputMethodManager: InputMethodManager? = null

    private val mPager: ViewPager? = null

    var mService: SampleClusterServiceImpl? = null

    private val mButtonToFacet: HashMap<Button, Facet<*>> = HashMap()
    private val mOrderToFacet: SparseArray<Facet<*>> = SparseArray()

    private val mFacetButtonFocusListener = OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                mPager?.currentItem = mButtonToFacet[v]?.order!!
            }
        }


    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MAINNNN", "AAAAAA")
        setContentView(R.layout.activity_main)
        mInputMethodManager = getSystemService(InputMethodManager::class.java)
        val intent = Intent(this, SampleClusterServiceImpl::class.java)
        intent.action = LOCAL_BINDING_ACTION
        bindService(intent, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    mService = (service as SampleClusterServiceImpl.LocalBinder).service
                    mService?.registerListener(this@MainClusterActivity)
                }
                override fun onServiceDisconnected(name: ComponentName) {
                    mService = null
                }
            }, Context.BIND_AUTO_CREATE
        )

        registerFacets(
            Facet(btn_nav, 0, NavigationFragment::class.java),
            Facet(btn_phone, 1, PhoneFragment::class.java),
            Facet(btn_music, 2, MusicFragment::class.java),
            Facet(btn_car_info, 3, CarInfoFragment::class.java)
        )
        Log.d(TAG, "--" + mOrderToFacet.size().toString())
        pager.adapter = ClusterPageAdapter(supportFragmentManager)
        mOrderToFacet[0].button.requestFocus()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onDestroy() {
        super.onDestroy()
        if (mService != null) {
            mService?.unregisterListener()
        }
    }

    inner class ClusterPageAdapter(fm: FragmentManager?) : FragmentPagerAdapter(fm) {
        override fun getCount(): Int = mButtonToFacet.size

        @RequiresApi(Build.VERSION_CODES.M)
        override fun getItem(position: Int): Fragment? {
            return mOrderToFacet.get(position).getOrCreateFragment
        }
    }

    companion object {
        private val TAG = MainClusterActivity::class.java.simpleName
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onKeyEvent(event: KeyEvent?) {
        Log.d(TAG, event.toString() + " ---- aa")

        // This is a hack. We use SOURCE_CLASS_POINTER here because this type of input is associated
        // with the display. otherwise this event will be ignored in ViewRootImpl because injecting
        // KeyEvent w/o activity being focused is useless.
        event!!.source = event.source or InputDevice.SOURCE_CLASS_POINTER
        mInputMethodManager?.dispatchKeyEventFromInputMethod(currentFocus, event)
    }


    private fun registerFacets(vararg facets: Facet<*>) {
        for (f in facets) {
            registerFacet(f)
        }
    }

    private fun <T> registerFacet(facet: Facet<T>) {
        mOrderToFacet.append(facet.order, facet)
        mButtonToFacet[facet.button] = facet
        facet.button.onFocusChangeListener = mFacetButtonFocusListener
    }

    private class Facet<T> internal constructor(var button: Button, var order: Int, var clazz: Class<T>) {
        private var mFragment: Fragment? = null
        val getOrCreateFragment: Fragment?
            get() {
                if (mFragment == null) {
                    mFragment = try {
                        Log.d(TAG, "0  ${clazz.name}")
                        clazz.constructors[0].newInstance() as Fragment
                    } catch (e: InstantiationException) {
                        Log.e(TAG, "1  $e")
                        throw RuntimeException(e)
                    } catch (e: IllegalAccessException) {
                        Log.e(TAG, "2  $e")
                        throw RuntimeException(e)
                    } catch (e: InvocationTargetException) {
                        Log.e(TAG, "3  $e")
                        throw RuntimeException(e)
                    }
                }
                return mFragment
            }

    }
}
