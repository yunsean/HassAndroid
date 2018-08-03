package com.yunsean.ihass

import android.content.Intent
import android.os.Bundle
import android.view.animation.Animation
import android.widget.Toast
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.ChoosePanel
import cn.com.thinkwatch.ihass2.bus.RefreshEvent
import cn.com.thinkwatch.ihass2.fragment.HassFragment
import cn.com.thinkwatch.ihass2.ui.ConfActivity
import cn.com.thinkwatch.ihass2.ui.PanelListActivity
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Animations
import com.yunsean.dynkotlins.extensions.activity
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.sdk25.coroutines.onLongClick

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewNoTitlebar(R.layout.activity_main)
        setTitle("智能家居", "", R.drawable.ic_action_panels, "", R.drawable.ic_action_edit)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)
        supportActionBar?.elevation = 0F
        supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, HassFragment())
                .commit()

        ui()
    }
    override fun onResume() {
        super.onResume()
        Animations.RotateAnimation(ball, 0F, 360F, Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5f)
                .duration(5000)
                .repeatCount(10000000)
                .start()
    }
    override fun onPause() {
        ball.clearAnimation()
        super.onPause()
    }

    override fun doLeft() {
        RxBus2.getDefault().post(ChoosePanel())
    }
    override fun doRight() {
        activity(PanelListActivity::class.java)
    }

    private var latestPressedTab0: Long = 0
    private var latestRefreshed: Long = 0
    private fun ui() {
        ball.onClick {
            if (System.currentTimeMillis() - latestPressedTab0 < 1000) {
                latestPressedTab0 = 0
                if (System.currentTimeMillis() - latestRefreshed > 5000) {
                    RxBus2.getDefault().post(RefreshEvent())
                    latestRefreshed = System.currentTimeMillis()
                }
            } else {
                latestPressedTab0 = System.currentTimeMillis()
            }
        }
        ball.onLongClick {
            RxBus2.getDefault().post(ChoosePanel())
            true
        }
        titlebarRight?.onLongClick {
            activity(ConfActivity::class.java)
            true
        }
    }

    internal var waitTime: Long = 2000
    internal var touchTime: Long = 0
    override fun onBackPressed() {
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

