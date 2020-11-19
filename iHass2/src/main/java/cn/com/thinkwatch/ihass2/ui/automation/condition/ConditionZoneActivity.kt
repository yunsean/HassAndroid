package cn.com.thinkwatch.ihass2.ui.automation.condition

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.automation.StateCondition
import cn.com.thinkwatch.ihass2.model.automation.ZoneCondition
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

class ConditionZoneActivity : BaseActivity() {

    private lateinit var condition: ZoneCondition
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_condition_zone)
        setTitle("区域触发", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val condition = intent.getStringExtra("condition")
        if (condition == null) this.condition = ZoneCondition()
        else this.condition = Gsons.gson.fromJson(condition, ZoneCondition::class.java)

        ui()
        data()
    }
    override fun doRight() {
        if (condition.entityId.isBlank()) return toastex("请选择观察对象！")
        if (condition.zone.isBlank()) return toastex("请选择目标区域！")
        setResult(Activity.RESULT_OK, Intent().putExtra("condition", AutomationEditActivity.gsonBuilder.toJson(condition)))
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
        act.entity.text = db.getEntity(condition.entityId)?.friendlyName
        act.zone.text = db.getEntity(condition.zone)?.friendlyName
    }

    @ActivityResult(requestCode = 105)
    private fun afterAddEntity(data: Intent?) {
        val entityIds = data?.getStringArrayExtra("entityIds")
        if (entityIds == null || entityIds.size < 1) return
        db.getEntity(entityIds.get(0))?.let {
            condition.entityId = it.entityId
            act.entity.text = it.friendlyName
        }
    }
    @ActivityResult(requestCode = 106)
    private fun afterZone(data: Intent?) {
        val entityIds = data?.getStringArrayExtra("entityIds")
        if (entityIds == null || entityIds.size < 1) return
        db.getEntity(entityIds.get(0))?.let {
            condition.zone = it.entityId
            act.zone.text = it.friendlyName
        }
    }
}

