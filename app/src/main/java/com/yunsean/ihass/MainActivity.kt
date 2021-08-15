package com.yunsean.ihass

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.FragmentTransaction
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.bus.ChoosePanel
import cn.com.thinkwatch.ihass2.bus.DisplayPanel
import cn.com.thinkwatch.ihass2.bus.RefreshEvent
import cn.com.thinkwatch.ihass2.fragment.AutomationFragment
import cn.com.thinkwatch.ihass2.fragment.album.AlbumMainFragment
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.common.rx.RxBus2
import com.dylan.common.utils.Utility
import com.dylan.uiparts.tabhost.TabHost
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.ctx
import org.jetbrains.anko.startActivity

class MainActivity : BaseActivity(), TabHost.OnTabChangeListener {

    private var fontSizeChanged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewNoTitlebar(R.layout.activity_main)
        initTabs()
        setTitle("智能家居", false)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)
        supportActionBar?.elevation = 0F
    }
    override fun onResume() {
        super.onResume()
        if (fontSizeChanged) {
            finish()
            startActivity<MainActivity>()
        }
        if (useAlbum != cfg.getBoolean(HassConfig.Album_Used)) {
            initTabs()
        }
    }

    private lateinit var mTabResId: Array<String>
    private lateinit var mTabTitle: Array<String>
    private lateinit var mTabIds: IntArray
    private var latestPressedTab0: Long = 0
    private var latestRefreshed: Long = 0
    private var useAlbum = false
    private fun initTabs() {
        useAlbum = cfg.getBoolean(HassConfig.Album_Used)
        if (useAlbum) {
            mTabResId = arrayOf("\uf0f5", "\uf59f", "\uf276", "\uf2ec", "\uf8ba")
            mTabTitle = arrayOf("HOME", "HASS", "智能", "同步", "更多")
            mTabIds = intArrayOf(R.id.hass, R.id.web, R.id.flow, R.id.album, R.id.more)
        } else {
            mTabResId = arrayOf("\uf0f5", "\uf59f", "\uf276", "\uf8ba")
            mTabTitle = arrayOf("HOME", "HASS", "智能", "更多")
            mTabIds = intArrayOf(R.id.hass, R.id.web, R.id.flow, R.id.more)
        }
        this.tabhost.setup()
        this.tabhost.clearAllTabs()
        val inflater = LayoutInflater.from(ctx)
        for (i in mTabTitle.indices) {
            val item = inflater.inflate(R.layout.tabitem_main, null)
            if (i != 0) {
                item.findViewById<TextView>(R.id.icon)?.setText(mTabResId[i])
                item.findViewById<TextView>(R.id.text)?.setText(mTabTitle[i])
                item.findViewById<View>(R.id.ball)?.visibility = View.GONE
            } else {
                item.findViewById<TextView>(R.id.icon)?.visibility = View.GONE
                item.findViewById<TextView>(R.id.text)?.visibility = View.GONE
            }
            tabhost.addTab(tabhost.newTabSpec(mTabTitle[i]).setIndicator(item).setContent(mTabIds[i]))
        }
        this.tabhost.currentTab = 0
        val listener = object : GestureDetector.OnGestureListener {
            private var isFliping = false
            override fun onDown(motionEvent: MotionEvent): Boolean {
                isFliping = false
                return false
            }
            override fun onShowPress(motionEvent: MotionEvent) {}
            override fun onSingleTapUp(motionEvent: MotionEvent): Boolean {
                if (!isFliping) {
                    RxBus2.getDefault().post(DisplayPanel(false, 0))
                    if (tabhost.getCurrentTab() != 0) tabhost.setCurrentTab(0)
                    if (System.currentTimeMillis() - latestPressedTab0 < 500) {
                        latestPressedTab0 = 0
                        if (System.currentTimeMillis() - latestRefreshed > 5000) {
                            RxBus2.getDefault().post(RefreshEvent())
                            latestRefreshed = System.currentTimeMillis()
                        }
                    } else {
                        latestPressedTab0 = System.currentTimeMillis()
                    }
                }
                return false
            }
            override fun onScroll(e1: MotionEvent, e2: MotionEvent, v: Float, v1: Float): Boolean {
                if (!isFliping && e1.y - e2.y > 20) {
                    if (tabhost.getCurrentTab() != 0) tabhost.setCurrentTab(0)
                    isFliping = true
                    RxBus2.getDefault().post(DisplayPanel(true, 0))
                    return false
                }
                return false
            }
            override fun onLongPress(motionEvent: MotionEvent) {
                if (!isFliping) {
                    RxBus2.getDefault().post(DisplayPanel(false, 0))
                    if (tabhost.getCurrentTab() != 0) tabhost.setCurrentTab(0)
                    RxBus2.getDefault().post(ChoosePanel())
                }
            }
            override fun onFling(e1: MotionEvent, e2: MotionEvent, v: Float, v1: Float): Boolean {
                return false
            }
        }
        val detector = GestureDetector(this@MainActivity, listener)
        tabhost.getTabWidget().getChildAt(0).setOnTouchListener(View.OnTouchListener { view, motionEvent ->
            RxBus2.getDefault().post(motionEvent)
            detector.onTouchEvent(motionEvent)
        })
        tabhost.setOnTabChangedListener(this)
        tabhost.currentTab = 0
        onTabChanged(mTabTitle[0])
        val metrics = DisplayMetrics()
        getWindowManager().getDefaultDisplay().getMetrics(metrics)
        tabhost.initSwitchAnimation(metrics.widthPixels)
        if (cfg.getBoolean(HassConfig.Ui_WebFrist)) tabhost.currentTab = 1
    }

    override fun onTabChanged(tag: String) {
        RxBus2.getDefault().post(DisplayPanel(false, 0))
        Utility.hideSoftKeyboard(this)
        updateTab()
        val fm = this.getSupportFragmentManager()
        var frag = fm.findFragmentById(mTabIds[tabhost.currentTab]) as BaseFragment?
        val ft = fm.beginTransaction()
        if (frag == null) {
            val viewID = mTabIds[tabhost.currentTab]
            frag = when (viewID) {
                R.id.hass-> MainFragment()
                R.id.web-> WebFragment()
                R.id.flow-> AutomationFragment()
                R.id.album-> AlbumMainFragment()
                R.id.more-> MoreFragment()
                else-> null
            }
            if (frag == null) return
            ft.replace(viewID, frag, tag)
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            ft.commit()
        }
    }
    private fun updateTab() {
        for (i in 0 until tabhost.tabWidget.childCount) {
            val view = tabhost.tabWidget.getChildAt(i)
            view.isActivated = tabhost.currentTab == i
        }
    }

    internal var waitTime: Long = 2000
    internal var touchTime: Long = 0
    override fun onBackPressed() {
        if (tabhost.currentTab != 1 || !(supportFragmentManager.findFragmentById(R.id.web) as WebFragment).onBackPressed()) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - touchTime >= waitTime) {
                Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show()
                touchTime = currentTime
            } else {
                val launcherIntent = Intent(Intent.ACTION_MAIN)
                launcherIntent.addCategory(Intent.CATEGORY_HOME)
                startActivity(launcherIntent)
            }
        }
    }
}
