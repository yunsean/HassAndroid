package com.yunsean.ihass

import android.os.Bundle
import android.support.design.widget.AppBarLayout
import android.support.v4.content.ContextCompat
import android.view.View
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.ui.*
import com.dylan.common.utils.Utility
import com.yunsean.dynkotlins.extensions.activity
import kotlinx.android.synthetic.main.fragment_more.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx
import android.support.v7.app.AppCompatActivity



class MoreFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.fragment_more
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle("附加设置", true)
        toolbar()
        ui()
    }

    private fun toolbar() {
        this.appBar.addOnOffsetChangedListener(object: AppBarLayout.OnOffsetChangedListener {
            override fun onOffsetChanged(appBarLayout: AppBarLayout?, verticalOffset: Int) {
                if (appBarLayout == null) return
                val abs = Math.abs(appBarLayout.totalScrollRange) / 2
                if (Math.abs(verticalOffset) >= abs) {
                    toolBar.visibility = View.VISIBLE
                    toolBar.alpha = (Math.abs(verticalOffset) - abs) * 1F / (Math.abs(appBarLayout.totalScrollRange) - abs)
                } else {
                    toolBar.visibility = View.GONE
                }
            }
        })
    }

    private fun ui() {
        this.version.text = "v ${Utility.getVerName(ctx)}"
        this.panels.onClick {
            ctx.activity(PanelListActivity::class.java)
        }
        this.shortcuts.onClick {
            ctx.activity(ShortcutEditActivity::class.java)
        }
        this.widgets.onClick {
            ctx.activity(WidgetListActivity::class.java)
        }
        this.scripts.onClick {
            ctx.activity(ScriptsActivity::class.java)
        }
        this.entities.onClick {
            ctx.activity(EntitiesActivity::class.java)
        }
        this.service.onClick {
            ctx.activity(CallServiceActivity::class.java)
        }
        this.server.onClick {
            ctx.activity(ConfActivity::class.java)
        }
        this.setting.onClick {
            ctx.activity(SettingActivity::class.java)
        }
        this.share.onClick {
            ctx.activity(ShareActivity::class.java)
        }
        this.observed.onClick {
            ctx.activity(ObservedActivity::class.java)
        }
        this.trigger.onClick {
            ctx.activity(TriggerActivity::class.java)
        }
        this.triggerHistory.onClick {
            ctx.activity(TriggerHistoryActivity::class.java)
        }
        this.notifications.onClick {
            ctx.activity(NotificationEditActivity::class.java)
        }
        this.voice.onClick {
            ctx.activity(SettingVoiceActivity::class.java)
        }
    }
}

