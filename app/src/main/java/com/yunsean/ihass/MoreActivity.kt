package com.yunsean.ihass

import android.os.Bundle
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.ui.ConfActivity
import cn.com.thinkwatch.ihass2.ui.PanelListActivity
import cn.com.thinkwatch.ihass2.ui.SettingActivity
import cn.com.thinkwatch.ihass2.ui.WidgetListActivity
import com.dylan.common.utils.Utility
import com.yunsean.dynkotlins.extensions.activity
import kotlinx.android.synthetic.main.activity_more.*
import org.jetbrains.anko.sdk25.coroutines.onClick

class MoreActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_more)
        setTitle("附加设置", true)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)
        supportActionBar?.elevation = 0f

        ui()
    }

    private fun ui() {
        this.version.text = "v ${Utility.getVerName(this)}"
        this.panels.onClick {
            activity(PanelListActivity::class.java)
        }
        this.shortcuts.onClick {
            activity(ShortcutEditActivity::class.java)
        }
        this.widgets.onClick {
            activity(WidgetListActivity::class.java)
        }
        this.server.onClick {
            activity(ConfActivity::class.java)
        }
        this.setting.onClick {
            activity(SettingActivity::class.java)
        }
        this.share.onClick {
            activity(ShareActivity::class.java)
        }
    }
}

