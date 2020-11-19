package cn.com.thinkwatch.ihass2.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.EntityChanged
import cn.com.thinkwatch.ihass2.db.db
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.*
import kotlinx.android.synthetic.main.activity_hass_notifycation_input.*
import org.jetbrains.anko.act

class NotificationInputActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_notifycation_input)
        setTitle("通知内容", true, "确定")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val content = intent.getStringExtra("content")
        act.content.setText(content)

        ui()

        disposable = RxBus2.getDefault().register(EntityChanged::class.java, {
            if (it.entityId.startsWith("persistent_notification.")) {
                db.getEntity(it.entityId)?.let {
                    act.content.setText(it.attributes?.message)
                }
            }
        }, disposable)
    }
    override fun doRight() {
        val content = act.content.text()
        setResult(Activity.RESULT_OK, Intent().putExtra("content", content))
        finish()
    }

    private fun ui() {
    }
}

