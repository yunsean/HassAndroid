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
import cn.com.thinkwatch.ihass2.ui.AutomationEditActivity
import cn.com.thinkwatch.ihass2.utils.Gsons
import com.dylan.dyn3rdparts.pickerview.DateTimePicker
import com.yunsean.dynkotlins.extensions.*
import kotlinx.android.synthetic.main.activity_hass_automation_condition_time.*
import org.jetbrains.anko.act
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.lang.StringBuilder
import java.util.*

class ConditionTimeActivity : BaseActivity() {

    private lateinit var condition: TimeCondition
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_condition_time)
        setTitle("时间条件", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val condition = intent.getStringExtra("condition")
        if (condition == null) this.condition = TimeCondition()
        else this.condition = Gsons.gson.fromJson(condition, TimeCondition::class.java)

        ui()
        data()
    }
    override fun doRight() {
        if (condition.after == null && condition.before == null) return showError("早于和晚于时间至少指定一个！")
        if (act.weekday.isChecked) {
            val weekdays = mutableListOf<Weekday>()
            if (act.mon.isChecked) weekdays.add(Weekday.mon)
            if (act.thu.isChecked) weekdays.add(Weekday.thu)
            if (act.wed.isChecked) weekdays.add(Weekday.wed)
            if (act.tue.isChecked) weekdays.add(Weekday.tue)
            if (act.fri.isChecked) weekdays.add(Weekday.fri)
            if (act.sat.isChecked) weekdays.add(Weekday.sat)
            if (act.sun.isChecked) weekdays.add(Weekday.sun)
            if (weekdays.isEmpty()) return showError("请指定重复日期！")
            condition.weekday = weekdays
        } else {
            condition.weekday = null
        }
        setResult(Activity.RESULT_OK, Intent().putExtra("condition", AutomationEditActivity.gsonBuilder.toJson(condition)))
        finish()
    }

    private fun ui() {
        act.before.onClick {
            showDialog(R.layout.dialog_choice_time, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.findViewById<TextView>(R.id.title).setText("早于")
                    contentView.findViewById<DateTimePicker>(R.id.picker).time = condition.before?.ktime()?.time ?: -TimeZone.getDefault().rawOffset.toLong()
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        condition.before = contentView.findViewById<DateTimePicker>(R.id.picker).date.ktime()
                        act.before.text = condition.before
                    } else {
                        condition.before = null
                        act.before.text = ""
                    }
                    dialog.dismiss()
                }
            })
        }
        act.after.onClick {
            showDialog(R.layout.dialog_choice_time, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.findViewById<TextView>(R.id.title).setText("晚于")
                    contentView.findViewById<DateTimePicker>(R.id.picker).time = condition.after?.ktime()?.time ?: -TimeZone.getDefault().rawOffset.toLong()
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        condition.after = contentView.findViewById<DateTimePicker>(R.id.picker).date.ktime()
                        act.after.text = condition.after
                    } else {
                        condition.after = null
                        act.after.text = ""
                    }
                    dialog.dismiss()
                }
            })
        }
        act.weekday.setOnCheckedChangeListener { buttonView, isChecked ->
            weekdays.forEach { findViewById<View>(it).visibility = if (isChecked) View.VISIBLE else View.GONE }
        }
    }

    private val weekdays = listOf(R.id.mon, R.id.tue, R.id.wed, R.id.thu, R.id.fri, R.id.sat, R.id.sun)
    private fun data() {
        act.before.text = if (!condition.before.isNullOrBlank()) condition.before else ""
        act.after.text = if (!condition.after.isNullOrBlank()) condition.after else ""
        val showWeekday = condition.weekday?.isEmpty() ?: true
        act.weekday.isChecked = showWeekday
        weekdays.forEach { findViewById<View>(it).visibility = if (showWeekday) View.VISIBLE else View.GONE }
    }

    companion object {
        fun desc(condition: TimeCondition): String {
            val result = StringBuilder()
            condition.weekday?.forEach {
                result.append("${it.desc} ")
            }
            condition.after?.let { result.append("晚于${it}") }
            condition.before?.let { result.append("早于${it}") }
            return result.toString()
        }
    }
}

