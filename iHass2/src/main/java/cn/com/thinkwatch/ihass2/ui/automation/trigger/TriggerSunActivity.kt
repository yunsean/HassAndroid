package cn.com.thinkwatch.ihass2.ui.automation.trigger

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.model.automation.MqttTrigger
import cn.com.thinkwatch.ihass2.model.automation.SunEvent
import cn.com.thinkwatch.ihass2.model.automation.SunTrigger
import cn.com.thinkwatch.ihass2.ui.AutomationEditActivity
import cn.com.thinkwatch.ihass2.utils.Gsons
import com.dylan.dyn3rdparts.pickerview.DateTimePicker
import com.yunsean.dynkotlins.extensions.*
import kotlinx.android.synthetic.main.activity_hass_automation_trigger_sun.*
import org.jetbrains.anko.act
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.util.*

class TriggerSunActivity : BaseActivity() {

    private lateinit var trigger: SunTrigger
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_trigger_sun)
        setTitle("日出日落触发", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val trigger = intent.getStringExtra("trigger")
        if (trigger == null) this.trigger = SunTrigger()
        else this.trigger = Gsons.gson.fromJson(trigger, SunTrigger::class.java)

        ui()
        data()
    }
    override fun doRight() {
        if (act.sunrise.isChecked) trigger.event = SunEvent.sunrise
        else if (act.sunset.isChecked) trigger.event = SunEvent.sunset
        else return toastex("请选择触发时机！")
        if (!trigger.offset.isNullOrBlank() && act.negativeOffset.isChecked) {
            trigger.offset = "-" + trigger.offset
        }
        setResult(Activity.RESULT_OK, Intent().putExtra("trigger", AutomationEditActivity.gsonBuilder.toJson(trigger)))
        finish()
    }

    private fun ui() {
        act.offsetValue.onClick {
            showDialog(R.layout.dialog_choice_time, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.findViewById<TextView>(R.id.title).setText("时间偏移")
                    contentView.findViewById<DateTimePicker>(R.id.picker).time = trigger.offset?.ktime()?.time ?: -TimeZone.getDefault().rawOffset.toLong()
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        trigger.offset = contentView.findViewById<DateTimePicker>(R.id.picker).date.ktime()
                        act.offsetValue.setText(trigger.offset)
                    } else if (clickedView.id == R.id.cancel) {
                        trigger.offset = null
                        act.offsetValue.text = ""
                    }
                    dialog.dismiss()
                }
            })
        }
    }
    private fun data() {
        if (trigger.event == SunEvent.sunrise) act.sunrise.isChecked = true
        else if (trigger.event == SunEvent.sunset) act.sunset.isChecked = true
        if (trigger.offset?.trim()?.startsWith("-") ?: false) {
            act.negativeOffset.isChecked = true
            trigger.offset = trigger.offset?.trim()?.trim { it == '-' }
        }
        act.offsetValue.text = trigger.offset?.trim()?.trim { it == '-' }
    }
}

