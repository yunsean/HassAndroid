package cn.com.thinkwatch.ihass2.ui.automation.trigger

import android.app.Activity
import android.content.Intent
import android.databinding.DataBindingUtil.setContentView
import android.os.Bundle
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.model.automation.AndCondition
import cn.com.thinkwatch.ihass2.model.automation.HomeAssistantEvent
import cn.com.thinkwatch.ihass2.model.automation.HomeAssistantTrigger
import cn.com.thinkwatch.ihass2.ui.AutomationEditActivity
import cn.com.thinkwatch.ihass2.utils.Gsons
import com.yunsean.dynkotlins.extensions.toastex
import kotlinx.android.synthetic.main.activity_hass_automation_trigger_hass.*
import org.jetbrains.anko.act

class TriggerHassActivity : BaseActivity() {

    private lateinit var trigger: HomeAssistantTrigger
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_trigger_hass)
        setTitle("Home Assistant触发", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val trigger = intent.getStringExtra("trigger")
        if (trigger == null) this.trigger = HomeAssistantTrigger()
        else this.trigger = Gsons.gson.fromJson(trigger, HomeAssistantTrigger::class.java)

        ui()
        data()
    }
    override fun doRight() {
        if (act.startup.isChecked) trigger.event = HomeAssistantEvent.start
        else if (act.shutdown.isChecked) trigger.event = HomeAssistantEvent.shutdown
        else return toastex("请选择触发时机！")
        setResult(Activity.RESULT_OK, Intent().putExtra("trigger", AutomationEditActivity.gsonBuilder.toJson(trigger)))
        finish()
    }

    private fun ui() {
    }
    private fun data() {
        if (trigger.event == HomeAssistantEvent.start) act.startup.isChecked = true
        else if (trigger.event == HomeAssistantEvent.shutdown) act.shutdown.isChecked = true
    }
}

