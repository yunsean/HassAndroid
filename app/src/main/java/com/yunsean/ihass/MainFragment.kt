package com.yunsean.ihass

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import cn.com.thinkwatch.ihass2.base.BaseFragment
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
import kotlinx.android.synthetic.main.fragment_main.*
import kotlinx.android.synthetic.main.listitem_shortcut.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.dip


class MainFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.fragment_main
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (cfg.getBoolean(HassConfig.Ui_HomePanels)) setTitle("智能家居", false, "", R.drawable.ic_action_panels)
        else setTitle("智能家居", false)
        childFragmentManager.beginTransaction()
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
    override fun onPause() {
        ball.clearAnimation()
        super.onPause()
    }
    override fun doRight() {
        if (cfg.getBoolean(HassConfig.Ui_HomePanels)) ctx.activity(PanelListActivity::class.java)
    }

    private var latestPressedTab0: Long = 0
    private var latestRefreshed: Long = 0
    private val statusBarTask = object: Runnable {
        override fun run() {
            if (statusBar.visibility == View.GONE) {
                act.savePref("showShortcut", "true")
                statusBar.visibility = View.VISIBLE
                Animations.MarginAnimation(statusBar, MarginAnimation.Margin.Right, ctx.screenWidth(), 0)
                        .duration(300)
                        .start()
            } else {
                act.savePref("showShortcut", "false")
                Animations.MarginAnimation(statusBar, MarginAnimation.Margin.Right, 0, ctx.screenWidth())
                        .duration(300)
                        .animationListener { statusBar.visibility = View.GONE }
                        .start()
            }
            statusBar.removeCallbacks(this)
        }
    }
    private lateinit var shortcutAdapter: RecyclerAdapter<JsonEntity>
    private fun ui() {
        val listener = object : GestureDetector.OnGestureListener {
            private var isFliping = false
            override fun onDown(motionEvent: MotionEvent): Boolean {
                isFliping = false
                return false
            }
            override fun onShowPress(motionEvent: MotionEvent) {}
            override fun onSingleTapUp(motionEvent: MotionEvent): Boolean {
                if (isFliping) return false
                if (buttonGuide.visibility == View.VISIBLE) {
                    ctx.savePref("hiddenButtonGuide1", "true", "DEFAULT")
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
                return false
            }
            override fun onScroll(e1: MotionEvent, e2: MotionEvent, v: Float, v1: Float): Boolean {
                if (!isFliping && e1.getY() - e2.getY() > 20) {
                    isFliping = true
                    RxBus2.getDefault().post(DisplayPanel(true, ball.height + dip(2)))
                    return false
                }
                return false
            }
            override fun onLongPress(motionEvent: MotionEvent) {
                if (!isFliping) {
                    RxBus2.getDefault().post(DisplayPanel(false))
                    RxBus2.getDefault().post(ChoosePanel())
                }
            }
            override fun onFling(e1: MotionEvent, e2: MotionEvent, v: Float, v1: Float): Boolean {
                return false
            }
        }
        val detector = GestureDetector(ctx, listener)
        ball.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
                RxBus2.getDefault().post(motionEvent)
                return detector.onTouchEvent(motionEvent)
            }
        })
        setting.onClick {
            ctx.activity(MoreFragment::class.java)
        }
        shortcutAdapter = RecyclerAdapter(R.layout.listitem_shortcut, null) {
            view, _, item ->
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
        shortcut.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun shortcut() {
        if (false) {
            val shortcuts = db.readShortcut()
            shortcutAdapter.items = shortcuts
            if (cfg.getBoolean(HassConfig.Ui_HomePanels)) setTitle("智能家居", false, "", R.drawable.ic_action_panels)
            else setTitle("智能家居", false)
            if (ctx.readPref("hiddenButtonGuide1", "false", "DEFAULT")?.toBoolean() ?: false) {
                if (!(ctx.readPref("showShortcut")?.toBoolean()
                                ?: false)) statusBar.visibility = View.GONE
                buttonGuide.visibility = View.GONE
                shortcut.visibility = View.VISIBLE
            }
        }
    }
}

