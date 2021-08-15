package cn.com.thinkwatch.ihass2.ui.automation.condition

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
import kotlinx.android.synthetic.main.activity_hass_automation_condition_state.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.lang.StringBuilder

class ConditionStateActivity : BaseActivity() {

    private lateinit var condition: StateCondition
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_condition_state)
        setTitle("状态条件", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val condition = intent.getStringExtra("condition")
        if (condition == null) this.condition = StateCondition()
        else this.condition = Gsons.gson.fromJson(condition, StateCondition::class.java)

        ui()
        data()
    }
    override fun doRight() {
        if (condition.entityId.isBlank()) return showError("请选择观察的目标！")
        condition.state = act.state.text().let { if (it.isBlank()) return showError("请设置有效的状态！") else it }
        setResult(Activity.RESULT_OK, Intent().putExtra("condition", AutomationEditActivity.gsonBuilder.toJson(condition)))
        finish()
    }

    private fun ui() {
        act.lasted.onClick {
            showDialog(R.layout.dialog_choice_time, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.findViewById<TextView>(R.id.title).setText("持续时长")
                    condition.lasted?.toDate()?.let { contentView.findViewById<DateTimePicker>(R.id.picker).setDate(it) }
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        condition.lasted = TimeOffset()
                        condition.lasted?.setDate(contentView.findViewById<DateTimePicker>(R.id.picker).date)
                        act.lasted.setText(condition.lasted.toString())
                    } else if (clickedView.id == R.id.cancel) {
                        condition.lasted = null
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
            if (condition.entityId.isBlank()) return@setOnClickListener
            startActivityForResult(Intent(ctx, AttributeListActivity::class.java)
                    .putExtra("entityId", condition.entityId), 205)
        }
        act.cleanAttribute.setOnClickListener {
            condition.attribute = null
            act.attribute.text = ""
            act.cleanAttribute.visibility = View.GONE
        }
    }
    private fun data() {
        if (condition.entityId.isNotBlank()) {
            db.getEntity(condition.entityId)?.let {
                act.entity.text = it.friendlyName
            }
        }
        act.attribute.text = condition.attribute
        act.state.setText(condition.state)
        act.lasted.setText(condition.lasted?.toString() ?: "")
    }

    @ActivityResult(requestCode = 205)
    private fun afterAttribibute(data: Intent?) {
        val entityId = data?.getStringExtra("entityId")
        val attribute = data?.getStringExtra("attribute")
        if (entityId.isNullOrBlank() || attribute.isNullOrBlank()) return
        condition.attribute = attribute
        act.attribute.text = attribute
        act.cleanAttribute.visibility = View.VISIBLE
    }

    @ActivityResult(requestCode = 105)
    private fun afterAddEntity(data: Intent?) {
        val entityIds = data?.getStringArrayExtra("entityIds")
        if (entityIds == null || entityIds.isEmpty()) return
        db.getEntity(entityIds[0])?.let {
            condition.entityId = it.entityId
            act.entity.text = it.friendlyName
            act.state.setText(it.state)
            act.addAttribute.visibility = View.VISIBLE
        }
    }

    companion object {
        fun desc(condition: StateCondition): String {
            val result = StringBuilder()
            var from: String? = null
            condition.entityId?.let {
                HassApplication.application.db.getEntity(it)?.let {
                    if (result.isNotBlank()) result.append(" ")
                    condition.state.let { t-> from = it.getFriendlyState(t) }
                    result.append(it.friendlyName)
                }
            }
            if (!condition.attribute.isNullOrBlank()) result.append(".${condition.attribute}")
            result.append("状态为")
            from?.let { result.append("$it") }
            condition.lasted?.let { result.append("，持续$it") }
            return result.toString()
        }
    }
}

