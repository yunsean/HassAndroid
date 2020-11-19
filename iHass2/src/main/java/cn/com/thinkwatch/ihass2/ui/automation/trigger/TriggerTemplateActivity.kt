package cn.com.thinkwatch.ihass2.ui.automation.trigger

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
import cn.com.thinkwatch.ihass2.ui.TemplateEditActivity
import cn.com.thinkwatch.ihass2.utils.Gsons
import com.dylan.dyn3rdparts.pickerview.DateTimePicker
import com.dylan.uiparts.activity.ActivityResult
import com.yunsean.dynkotlins.extensions.*
import kotlinx.android.synthetic.main.activity_hass_automation_trigger_template.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.util.*

class TriggerTemplateActivity : BaseActivity() {

    private lateinit var trigger: TemplateTrigger
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_trigger_template)
        setTitle("模板触发", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val trigger = intent.getStringExtra("trigger")
        if (trigger == null) this.trigger = TemplateTrigger()
        else this.trigger = Gsons.gson.fromJson(trigger, TemplateTrigger::class.java)

        ui()
        data()
    }
    override fun doRight() {
        trigger.valueTemplate = act.valueTemplate.text().trim()
        if (trigger.valueTemplate.isBlank()) return toastex("请输入模板参数！")
        setResult(Activity.RESULT_OK, Intent().putExtra("trigger", AutomationEditActivity.gsonBuilder.toJson(trigger)))
        finish()
    }

    private fun ui() {
        this.edit.onClick {
            startActivityForResult(Intent(ctx, TemplateEditActivity::class.java)
                    .putExtra("template", act.valueTemplate.text().trim()), 100)
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
                    } else if (clickedView.id == R.id.cancel) {
                        trigger.lasted = null
                        act.lasted.text = ""
                    }
                    dialog.dismiss()
                }
            })
        }
    }
    private fun data() {
        act.valueTemplate.setText(trigger.valueTemplate)
        act.lasted.setText(trigger.lasted)
    }

    @ActivityResult(requestCode = 100)
    private fun afterEdit(data: Intent?) {
        if (data == null || !data.hasExtra("template")) return
        act.valueTemplate.setText(data.getStringExtra("template"))
    }
}

