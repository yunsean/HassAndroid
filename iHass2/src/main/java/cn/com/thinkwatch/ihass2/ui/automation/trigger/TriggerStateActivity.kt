package cn.com.thinkwatch.ihass2.ui.automation.trigger

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.automation.*
import cn.com.thinkwatch.ihass2.ui.AttributeListActivity
import cn.com.thinkwatch.ihass2.ui.AutomationEditActivity
import cn.com.thinkwatch.ihass2.ui.EntityListActivity
import cn.com.thinkwatch.ihass2.utils.Gsons
import com.dylan.dyn3rdparts.pickerview.DateTimePicker
import com.dylan.uiparts.activity.ActivityResult
import com.yunsean.dynkotlins.extensions.*
import kotlinx.android.synthetic.main.activity_hass_automation_trigger_state.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.lang.StringBuilder
import java.util.*

class TriggerStateActivity : BaseActivity() {

    private lateinit var trigger: StateTrigger
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_trigger_state)
        setTitle("状态触发", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val trigger = intent.getStringExtra("trigger")
        if (trigger == null) this.trigger = StateTrigger()
        else this.trigger = Gsons.gson.fromJson(trigger, StateTrigger::class.java)

        ui()
        data()
    }
    override fun doRight() {
        if (trigger.entityId.isBlank()) return showError("请选择观察的目标！")
        trigger.from = act.from.text().let { if (it.isBlank()) null else it }
        trigger.to = act.to.text().let { if (it.isBlank()) null else it }
        trigger.lasted = act.lasted.text().let { if (it.isBlank()) null else it }
        setResult(Activity.RESULT_OK, Intent().putExtra("trigger", AutomationEditActivity.gsonBuilder.toJson(trigger)))
        finish()
    }

    private fun ui() {
        act.lasted.onClick {
            showDialog(R.layout.dialog_choice_time, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.findViewById<TextView>(R.id.title).setText("持续时长")
                    contentView.findViewById<DateTimePicker>(R.id.picker).time = trigger.lasted?.ktime()?.time ?: -TimeZone.getDefault().rawOffset.toLong()
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        trigger.lasted = contentView.findViewById<DateTimePicker>(R.id.picker).date.ktime()
                        act.lasted.setText(trigger.lasted)
                    } else if (clickedView.id == R.id.cancel) {
                        trigger.lasted = null
                        act.lasted.text = ""
                    }
                    dialog.dismiss()
                }
            })
        }
        act.entity.onClick {
            startActivityForResult(Intent(ctx, EntityListActivity::class.java)
                    .putExtra("singleOnly", true), 105)
        }
        act.addAttribute.setOnClickListener {
            if (trigger.entityId.isBlank()) return@setOnClickListener
            startActivityForResult(Intent(ctx, AttributeListActivity::class.java)
                    .putExtra("entityId", trigger.entityId), 205)
        }
        act.cleanAttribute.setOnClickListener {
            trigger.attribute = null
            act.attribute.text = ""
            act.cleanAttribute.visibility = View.GONE
        }
    }
    private fun data() {
        if (trigger.entityId.isNotBlank()) {
            db.getEntity(trigger.entityId)?.let {
                act.entity.text = it.friendlyName
            }
        }
        act.attribute.text = trigger.attribute
        act.from.setText(trigger.from)
        act.to.setText(trigger.to)
        act.lasted.setText(trigger.lasted)
    }

    @ActivityResult(requestCode = 205)
    private fun afterAttribibute(data: Intent?) {
        val entityId = data?.getStringExtra("entityId")
        val attribute = data?.getStringExtra("attribute")
        if (entityId.isNullOrBlank() || attribute.isNullOrBlank()) return
        trigger.attribute = attribute
        act.attribute.text = attribute
        act.cleanAttribute.visibility = View.VISIBLE
    }

    @ActivityResult(requestCode = 105)
    private fun afterAddEntity(data: Intent?) {
        val entityIds = data?.getStringArrayExtra("entityIds")
        if (entityIds == null || entityIds.isEmpty()) return
        db.getEntity(entityIds[0])?.let {
            trigger.entityId = it.entityId
            act.entity.text = it.friendlyName
            act.to.setText(it.state)
            act.addAttribute.visibility = View.VISIBLE
        }
    }

    companion object {
        fun desc(trigger: StateTrigger): String {
            val result = StringBuilder()
            var from: String? = null
            var to: String? = null
            trigger.entityId.split(",").forEach {
                HassApplication.application.db.getEntity(it)?.let {
                    if (result.isNotBlank()) result.append(" ")
                    trigger.from?.let {t-> from = it.getFriendlyState(t) }
                    trigger.to?.let {t-> to = it.getFriendlyState(t) }
                    result.append(it.friendlyName)
                }
            }
            if (!trigger.attribute.isNullOrBlank()) result.append(".${trigger.attribute}")
            result.append("状态")
            from?.let { result.append("从$it") }
            to?.let { result.append("变更为$it") }
            trigger.lasted?.trim()?.let { if (it.isNotBlank()) result.append("，持续$it") }
            return result.toString()
        }
    }
}

