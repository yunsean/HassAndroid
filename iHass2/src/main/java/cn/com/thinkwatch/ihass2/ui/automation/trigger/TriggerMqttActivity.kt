package cn.com.thinkwatch.ihass2.ui.automation.trigger

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.model.automation.*
import cn.com.thinkwatch.ihass2.ui.AutomationEditActivity
import cn.com.thinkwatch.ihass2.ui.NotificationInputActivity
import cn.com.thinkwatch.ihass2.utils.Gsons
import com.dylan.uiparts.activity.ActivityResult
import com.yunsean.dynkotlins.extensions.*
import kotlinx.android.synthetic.main.activity_hass_automation_trigger_mqtt.*
import org.jetbrains.anko.act

class TriggerMqttActivity : BaseActivity() {

    private lateinit var trigger: MqttTrigger
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_trigger_mqtt)
        setTitle("MQTT触发", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val trigger = intent.getStringExtra("trigger")
        if (trigger == null) this.trigger = MqttTrigger()
        else this.trigger = Gsons.gson.fromJson(trigger, MqttTrigger::class.java)

        ui()
        data()
    }
    override fun doRight() {
        val topic = act.topic.text().trim()
        if (topic.isBlank()) return toastex("请填写MQTT主题！")
        trigger.topic = topic
        trigger.payload = act.payload.text()
        if (trigger.payload.isNullOrBlank()) trigger.payload = null
        trigger.encoding = act.encoding.text().trim()
        if (trigger.encoding.isNullOrBlank()) trigger.encoding = null
        setResult(Activity.RESULT_OK, Intent().putExtra("trigger", AutomationEditActivity.gsonBuilder.toJson(trigger)))
        finish()
    }

    private fun ui() {
        act.edit.setOnClickListener {
            startActivityForResult(Intent(this, NotificationInputActivity::class.java), 101)
        }
    }
    private fun data() {
        act.topic.setText(trigger.topic)
        act.payload.setText(trigger.payload)
        act.encoding.setText(trigger.encoding)
    }

    @ActivityResult(requestCode = 101)
    private fun onInput(data: Intent?) {
        data?.getStringExtra("content")?.let {
            act.payload.setText(it)
        }
    }
}

