package cn.com.thinkwatch.ihass2.ui.automation.condition

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.model.automation.*
import cn.com.thinkwatch.ihass2.ui.AutomationEditActivity
import cn.com.thinkwatch.ihass2.utils.Gsons
import com.dylan.dyn3rdparts.pickerview.DateTimePicker
import com.yunsean.dynkotlins.extensions.*
import kotlinx.android.synthetic.main.activity_hass_automation_condition_sun.*
import org.jetbrains.anko.act
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.lang.StringBuilder
import java.util.*

class ConditionSunActivity : BaseActivity() {

    private lateinit var condition: SunCondition
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_condition_sun)
        setTitle("日出条件", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val condition = intent.getStringExtra("condition")
        if (condition == null) this.condition = SunCondition()
        else this.condition = Gsons.gson.fromJson(condition, SunCondition::class.java)

        ui()
        data()
    }
    override fun doRight() {
        if (condition.after == null && condition.before == null) return showError("请选择日出模式！")
        if (!condition.beforeOffset.isNullOrBlank() && act.beforeMinus.isChecked) {
            condition.beforeOffset = "-" + condition.beforeOffset
        }
        if (!condition.afterOffset.isNullOrBlank() && act.afterMinus.isChecked) {
            condition.afterOffset = "-" + condition.afterOffset
        }
        setResult(Activity.RESULT_OK, Intent().putExtra("condition", AutomationEditActivity.gsonBuilder.toJson(condition)))
        finish()
    }

    private fun ui() {
        act.beforeOffset.onClick {
            showDialog(R.layout.dialog_choice_time, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.findViewById<TextView>(R.id.title).setText("提早")
                    contentView.findViewById<DateTimePicker>(R.id.picker).time = condition.beforeOffset?.ktime()?.time ?: -TimeZone.getDefault().rawOffset.toLong()
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        condition.beforeOffset = contentView.findViewById<DateTimePicker>(R.id.picker).date.ktime()
                        act.beforeOffset.setText(condition.beforeOffset)
                    } else if (clickedView.id == R.id.cancel) {
                        condition.beforeOffset = null
                        act.beforeOffset.text = ""
                    }
                    updateUi()
                    dialog.dismiss()
                }
            })
        }
        act.afterOffset.onClick {
            showDialog(R.layout.dialog_choice_time, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.findViewById<TextView>(R.id.title).setText("延后")
                    contentView.findViewById<DateTimePicker>(R.id.picker).time = condition.afterOffset?.ktime()?.time ?: -TimeZone.getDefault().rawOffset.toLong()
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        condition.afterOffset = contentView.findViewById<DateTimePicker>(R.id.picker).date.ktime()
                        act.afterOffset.setText(condition.afterOffset)
                    } else if (clickedView.id == R.id.cancel) {
                        condition.afterOffset = null
                        act.afterOffset.text = ""
                    }
                    updateUi()
                    dialog.dismiss()
                }
            })
        }
        act.before.setOnCheckedChangeListener { group, checkedId ->
            condition.before = if (checkedId == R.id.beforeSunset) SunEvent.sunset else if (checkedId == R.id.beforeSunrise) SunEvent.sunrise else null
            updateUi()
        }
        act.after.setOnCheckedChangeListener { group, checkedId ->
            condition.after = if (checkedId == R.id.afterSunset) SunEvent.sunset else if (checkedId == R.id.afterSunrise) SunEvent.sunrise else null
            updateUi()
        }
    }
    private fun updateUi() {
        act.beforeOffset.visibility = if (condition.before != null) View.VISIBLE else View.GONE
        act.beforeMinusPanel.visibility = if (condition.before != null && !condition.beforeOffset.isNullOrBlank()) View.VISIBLE else View.GONE
        act.afterOffset.visibility = if (condition.after != null) View.VISIBLE else View.GONE
        act.afterMinusPanel.visibility = if (condition.after != null && !condition.afterOffset.isNullOrBlank()) View.VISIBLE else View.GONE
    }
    private fun data() {
        when (condition.before) {
            SunEvent.sunrise-> act.beforeSunrise.isChecked = true
            SunEvent.sunset-> act.beforeSunset.isChecked = true
            else-> act.beforeNone.isChecked = true
        }
        when (condition.after) {
            SunEvent.sunrise-> act.afterSunrise.isChecked = true
            SunEvent.sunset-> act.afterSunset.isChecked = true
            else-> act.afterNone.isChecked = true
        }
        if (condition.beforeOffset?.trim()?.startsWith("-") ?: false) {
            act.beforeMinus.isChecked = true
            condition.beforeOffset = condition.beforeOffset?.trim()?.trim { it == '-' }
        }
        if (condition.afterOffset?.trim()?.startsWith("-") ?: false) {
            act.afterMinus.isChecked = true
            condition.afterOffset = condition.afterOffset?.trim()?.trim { it == '-' }
        }
        act.beforeOffset.setText(condition.beforeOffset)
        act.afterOffset.setText(condition.afterOffset)
        updateUi()
    }

    companion object {
        fun desc(condition: SunCondition): String {
            val result = StringBuilder()
            if (condition.before != null) {
                result.append("早于").append(condition.before?.desc).append("前")
                condition.beforeOffset?.let { if (!it.isBlank()) result.append(it) }
            }
            if (condition.after != null) {
                if (!result.isBlank()) result.append("，")
                result.append("晚于").append(condition.after?.desc).append("后")
                condition.afterOffset?.let { if (!it.isBlank()) result.append(it) }
            }
            return result.toString()
        }
    }
}

