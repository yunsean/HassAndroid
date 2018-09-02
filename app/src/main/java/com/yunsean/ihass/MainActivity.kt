package com.yunsean.ihass

import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.Toast
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.*
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.enums.ItemType
import cn.com.thinkwatch.ihass2.fragment.HassFragment
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.ui.PanelListActivity
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Animations
import com.dylan.uiparts.annimation.MarginAnimation
import com.yunsean.dynkotlins.extensions.activity
import com.yunsean.dynkotlins.extensions.readPref
import com.yunsean.dynkotlins.extensions.savePref
import com.yunsean.dynkotlins.extensions.screenWidth
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.ihass.db.ShortcutChanged
import com.yunsean.ihass.db.db
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.listitem_shortcut.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewNoTitlebar(R.layout.activity_main)
        setTitle("智能家居", false)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)
        supportActionBar?.elevation = 0F
        supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, HassFragment())
                .commit()

        ui()
        shortcut()
        disposable = RxBus2.getDefault().register(ShortcutChanged::class.java, {
            shortcut()
        }, RxBus2.getDefault().register(EntityChanged::class.java, {
            shortcut()
        }, RxBus2.getDefault().register(ConfigChanged::class.java, {
            if (cfg.getBoolean(HassConfig.Ui_HomePanels)) setTitle("智能家居", false, "", R.drawable.ic_action_panels)
            else setTitle("智能家居", false)
        }, disposable)))
    }
    override fun onResume() {
        super.onResume()
    }
    override fun onPause() {
        ball.clearAnimation()
        super.onPause()
    }
    override fun doRight() {
        if (cfg.getBoolean(HassConfig.Ui_HomePanels)) activity(PanelListActivity::class.java)
    }

    private var latestPressedTab0: Long = 0
    private var latestRefreshed: Long = 0
    private val statusBarTask = object: Runnable {
        override fun run() {
            if (statusBar.visibility == View.GONE) {
                savePref("showShortcut", "true")
                statusBar.visibility = View.VISIBLE
                Animations.MarginAnimation(statusBar, MarginAnimation.Margin.Right, screenWidth(), 0)
                        .duration(300)
                        .start()
            } else {
                savePref("showShortcut", "false")
                Animations.MarginAnimation(statusBar, MarginAnimation.Margin.Right, 0, screenWidth())
                        .duration(300)
                        .animationListener { statusBar.visibility = View.GONE }
                        .start()
            }
            statusBar.removeCallbacks(this)
        }
    }
    private lateinit var shortcutAdapter: RecyclerAdapter<JsonEntity>
    private fun ui() {
        ball.onClick {
            if (buttonGuide.visibility == View.VISIBLE) {
                savePref("hiddenButtonGuide", "true", "DEFAULT")
                buttonGuide.visibility = View.GONE
                shortcut.visibility = View.VISIBLE
            }
            if (System.currentTimeMillis() - latestPressedTab0 < 300) {
                latestPressedTab0 = 0
                if (System.currentTimeMillis() - latestRefreshed > 5000) {
                    RxBus2.getDefault().post(RefreshEvent())
                    latestRefreshed = System.currentTimeMillis()
                }
                ball.removeCallbacks(statusBarTask)
            } else {
                latestPressedTab0 = System.currentTimeMillis()
                ball.removeCallbacks(statusBarTask)
                ball.postDelayed(statusBarTask, 300)
            }
        }
        ball.setOnLongClickListener(object: View.OnLongClickListener {
            override fun onLongClick(v: View?): Boolean {
                RxBus2.getDefault().post(ChoosePanel())
                return true
            }
        })
        setting.onClick {
            activity(MoreActivity::class.java)
        }
        shortcutAdapter = RecyclerAdapter(R.layout.listitem_shortcut, null) {
            view, index, item ->
            if (item.itemType == ItemType.blank) {
                view.contentView.visibility = View.INVISIBLE
            } else {
                view.contentView.visibility = View.VISIBLE
                MDIFont.get().setIcon(view.icon, if (item.showIcon.isNullOrBlank()) item.iconState else item.showIcon)
                view.icon.isActivated = item.isActivated
                view.contentView.onClick {
                    if (item.isSwitch && item.isStateful) {
                        RxBus2.getDefault().post(ServiceRequest(item.domain, "toggle", item.entityId))
                    } else {
                        RxBus2.getDefault().post(EntityClicked(item))
                    }
                }
                view.contentView.setOnLongClickListener(object : View.OnLongClickListener {
                    override fun onLongClick(p0: View?): Boolean {
                        RxBus2.getDefault().post(EntityLongClicked(item))
                        return true
                    }
                })
            }
        }
        shortcut.adapter = shortcutAdapter
        shortcut.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun shortcut() {
        val shortcuts = db.readShortcut()
        shortcutAdapter.items = shortcuts
        if (cfg.getBoolean(HassConfig.Ui_HomePanels)) setTitle("智能家居", false, "", R.drawable.ic_action_panels)
        else setTitle("智能家居", false)
        if (readPref("hiddenButtonGuide", "false", "DEFAULT")?.toBoolean() ?: false) {
            if (!(readPref("showShortcut")?.toBoolean() ?: false)) statusBar.visibility = View.GONE
            buttonGuide.visibility = View.GONE
            shortcut.visibility = View.VISIBLE
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

