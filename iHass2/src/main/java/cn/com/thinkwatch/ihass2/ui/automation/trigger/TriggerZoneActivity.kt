package cn.com.thinkwatch.ihass2.ui.automation.trigger

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.automation.MqttTrigger
import cn.com.thinkwatch.ihass2.model.automation.ZoneEvent
import cn.com.thinkwatch.ihass2.model.automation.ZoneTrigger
import cn.com.thinkwatch.ihass2.ui.AutomationEditActivity
import cn.com.thinkwatch.ihass2.ui.EntityListActivity
import cn.com.thinkwatch.ihass2.utils.Gsons
import com.dylan.uiparts.activity.ActivityResult
import com.yunsean.dynkotlins.extensions.toastex
import kotlinx.android.synthetic.main.activity_hass_automation_trigger_zone.*
import kotlinx.android.synthetic.main.activity_hass_automation_trigger_zone.entity
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick

class TriggerZoneActivity : BaseActivity() {

    private lateinit var trigger: ZoneTrigger
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_trigger_zone)
        setTitle("区域触发", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val trigger = intent.getStringExtra("trigger")
        if (trigger == null) this.trigger = ZoneTrigger()
        else this.trigger = Gsons.gson.fromJson(trigger, ZoneTrigger::class.java)

        ui()
        data()
    }
    override fun doRight() {
        if (trigger.entityId.isBlank()) return toastex("请选择观察对象！")
        if (trigger.zone.isBlank()) return toastex("请选择目标区域！")
        if (act.enter.isChecked) trigger.event = ZoneEvent.enter
        else if (act.leave.isChecked) trigger.event = ZoneEvent.leave
        else return toastex("请选择触发时机！")
        setResult(Activity.RESULT_OK, Intent().putExtra("trigger", AutomationEditActivity.gsonBuilder.toJson(trigger)))
        finish()
    }

    private fun ui() {
        act.entity.onClick {
            startActivityForResult(Intent(ctx, EntityListActivity::class.java)
                    .putExtra("singleOnly", true)
                    .putExtra("filter", "device_tracker."), 105)
        }
        act.zone.onClick {
            startActivityForResult(Intent(ctx, EntityListActivity::class.java)
                    .putExtra("singleOnly", true)
                    .putExtra("filter", "zone."), 106)
        }
    }
    private fun data() {
        act.entity.text = db.getEntity(trigger.entityId)?.friendlyName
        act.zone.text = db.getEntity(trigger.zone)?.friendlyName
        if (trigger.event == ZoneEvent.enter) act.enter.isChecked = true
        else if (trigger.event == ZoneEvent.leave) act.leave.isChecked = true
    }

    @ActivityResult(requestCode = 105)
    private fun afterAddEntity(data: Intent?) {
        val entityIds = data?.getStringArrayExtra("entityIds")
        if (entityIds == null || entityIds.size < 1) return
        db.getEntity(entityIds.get(0))?.let {
            trigger.entityId = it.entityId
            act.entity.text = it.friendlyName
        }
    }
    @ActivityResult(requestCode = 106)
    private fun afterZone(data: Intent?) {
        val entityIds = data?.getStringArrayExtra("entityIds")
        if (entityIds == null || entityIds.size < 1) return
        db.getEntity(entityIds.get(0))?.let {
            trigger.zone = it.entityId
            act.zone.text = it.friendlyName
        }
    }
}

