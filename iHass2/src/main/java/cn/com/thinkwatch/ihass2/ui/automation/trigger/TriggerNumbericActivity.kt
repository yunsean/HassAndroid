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
import cn.com.thinkwatch.ihass2.ui.AutomationEditActivity
import cn.com.thinkwatch.ihass2.ui.EntityListActivity
import cn.com.thinkwatch.ihass2.utils.Gsons
import com.dylan.dyn3rdparts.pickerview.DateTimePicker
import com.dylan.uiparts.activity.ActivityResult
import com.yunsean.dynkotlins.extensions.*
import kotlinx.android.synthetic.main.activity_hass_automation_trigger_numberic.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.lang.StringBuilder
import java.util.*

class TriggerNumbericActivity : BaseActivity() {

    private lateinit var trigger: NumericStateTrigger
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_trigger_numberic)
        setTitle("数字量触发", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val trigger = intent.getStringExtra("trigger")
        if (trigger == null) this.trigger = NumericStateTrigger()
        else this.trigger = Gsons.gson.fromJson(trigger, NumericStateTrigger::class.java)

        ui()
        data()
    }
    override fun doRight() {
        if (trigger.entityId.isBlank()) return toastex("请选择观察的目标！")
        trigger.above = act.above.text().trim().toFloatOrNull()?.toBigDecimal()
        trigger.below = act.below.text().trim().toFloatOrNull()?.toBigDecimal()
        if (trigger.above == null && trigger.below == null) return toastex("上限和下限必须设置一个！")
        trigger.valueTemplate = act.valueTemplate.text().let { if (it.isBlank()) null else it }
        trigger.lasted = act.lasted.text().let { if (it.isBlank()) null else it }
        setResult(Activity.RESULT_OK, Intent().putExtra("trigger", AutomationEditActivity.gsonBuilder.toJson(trigger)))
        finish()
    }

    private fun ui() {
        act.entity.onClick {
            startActivityForResult(Intent(ctx, EntityListActivity::class.java)
                    .putExtra("singleOnly", true), 105)
        }
        act.lasted.onClick {
            showDialog(R.layout.dialog_choice_time, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.findViewById<TextView>(R.id.title).setText("持续时间")
                    contentView.findViewById<DateTimePicker>(R.id.picker).time = trigger.lasted?.ktime()?.time ?: -TimeZone.getDefault().rawOffset.toLong()
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        trigger.lasted = contentView.findViewById<DateTimePicker>(R.id.picker).date.ktime()
                        act.lasted.text = trigger.lasted
                    }
                    dialog.dismiss()
                }
            })
        }
    }
    private fun data() {
        if (trigger.entityId.isNotBlank()) {
            db.getEntity(trigger.entityId)?.let {
                act.entity.text = it.friendlyName
            }
        }
        act.above.setText(trigger.above?.toString() ?: "")
        act.below.setText(trigger.below?.toString() ?: "")
        act.valueTemplate.setText(trigger.valueTemplate)
        act.lasted.setText(trigger.lasted)
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

    companion object {
        fun desc(trigger: NumericStateTrigger): String {
            val result = StringBuilder()
            result.append(HassApplication.application.db.getEntity(trigger.entityId)?.friendlyName ?: trigger.entityId)
            if (trigger.valueTemplate.isNullOrBlank()) result.append("的状态")
            else result.append("的值")
            if (trigger.above != null) result.append("高于${trigger.above}")
            if (trigger.below != null) result.append("低于${trigger.below}")
            return result.toString()
        }
    }
}

