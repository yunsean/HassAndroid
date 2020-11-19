package cn.com.thinkwatch.ihass2.ui.automation.action

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
import kotlinx.android.synthetic.main.activity_hass_automation_action_delay.*
import org.jetbrains.anko.act
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.lang.StringBuilder
import java.util.*

class ActionDelayActivity : BaseActivity() {

    private lateinit var action: DelayAction
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_action_delay)
        setTitle("延迟", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val action = intent.getStringExtra("action")
        if (action == null) this.action = DelayAction()
        else this.action = Gsons.gson.fromJson(action, DelayAction::class.java)

        ui()
        data()
    }
    override fun doRight() {
        if (act.modePattern.isChecked) {
            action.delay = null
            action.delayValue = DelayTime().apply {
                days = act.days.text().let { if (it.isBlank()) null else it }
                hours = act.hours.text().let { if (it.isBlank()) null else it }
                minutes = act.minutes.text().let { if (it.isBlank()) null else it }
                seconds = act.seconds.text().let { if (it.isBlank()) null else it }
                milliseconds = act.milliseconds.text().let { if (it.isBlank()) null else it }
            }
        } else {
            action.delay = act.delay.text().let { if (it.isBlank()) return showError("请输入触发时间") else it }
            action.delayValue = null
        }
        setResult(Activity.RESULT_OK, Intent().putExtra("action", AutomationEditActivity.gsonBuilder.toJson(action)))
        finish()
    }

    private fun ui() {
        act.modeAt.onClick { updateChecked() }
        act.modePattern.onClick { updateChecked() }
        act.delay.onClick {
            showDialog(R.layout.dialog_choice_time, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.findViewById<TextView>(R.id.title).setText("延迟")
                    contentView.findViewById<DateTimePicker>(R.id.picker).time = action.delay?.ktime()?.time ?: -TimeZone.getDefault().rawOffset.toLong()
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) act.delay.text = contentView.findViewById<DateTimePicker>(R.id.picker).date.ktime()
                    dialog.dismiss()
                }
            })
        }
        act.delayTemplate.onClick {

        }
        act.daysTemplate.onClick {

        }
        act.hoursTemplate.onClick {

        }
        act.minutesTemplate.onClick {

        }
        act.secondsTemplate.onClick {

        }
        act.millisecondsTemplate.onClick {

        }
    }
    private fun data() {
        act.modeAt.isChecked = !action.delay.isNullOrBlank()
        act.modePattern.isChecked = action.delay.isNullOrBlank()
        act.delay.text = if (!action.delay.isNullOrBlank()) action.delay else ""
        action.delayValue?.let {
            act.days.setText(it.days)
            act.hours.setText(it.hours)
            act.minutes.setText(it.minutes)
            act.seconds.setText(it.seconds)
            act.milliseconds.setText(it.milliseconds)
        }
        updateChecked()
    }
    private fun updateChecked() {
        act.atPanel.visibility = if (act.modeAt.isChecked) View.VISIBLE else View.GONE
        act.patternPanel.visibility = if (act.modePattern.isChecked) View.VISIBLE else View.GONE
    }

    companion object {
        private val timeRegex = """^\d{2}:\d{2}:\d{2}${'$'}""".toRegex()
        private val numberRegex = """^\d+${'$'}""".toRegex()
        fun desc(action: DelayAction): String {
            if (!action.delay.isNullOrBlank() && timeRegex.matches(action.delay!!)) return "延迟${action.delay}"
            if (!action.delay.isNullOrBlank()) return "延迟指定时间"
            action.delayValue?.let {
                val result = StringBuilder("延迟")
                if (!it.days.isNullOrBlank()) if (numberRegex.matches(it.days!!)) result.append("${it.days}天") else return "延迟指定时间"
                if (!it.hours.isNullOrBlank()) if (numberRegex.matches(it.hours!!)) result.append("${it.hours}时") else return "延迟指定时间"
                if (!it.minutes.isNullOrBlank()) if (numberRegex.matches(it.minutes!!)) result.append("${it.minutes}分") else return "延迟指定时间"
                if (!it.seconds.isNullOrBlank()) if (numberRegex.matches(it.seconds!!)) result.append("${it.seconds}秒") else return "延迟指定时间"
                if (!it.milliseconds.isNullOrBlank()) if (numberRegex.matches(it.milliseconds!!)) result.append("${it.milliseconds}毫秒") else return "延迟指定时间"
                return result.toString()
            }
            return "延迟指定时间"
        }
    }
}

