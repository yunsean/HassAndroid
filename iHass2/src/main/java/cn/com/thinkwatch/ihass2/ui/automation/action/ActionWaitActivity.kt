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
import cn.com.thinkwatch.ihass2.ui.TemplateEditActivity
import cn.com.thinkwatch.ihass2.utils.Gsons
import com.dylan.dyn3rdparts.pickerview.DateTimePicker
import com.dylan.uiparts.activity.ActivityResult
import com.yunsean.dynkotlins.extensions.*
import kotlinx.android.synthetic.main.activity_hass_automation_action_wait.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.lang.StringBuilder
import java.util.*

class ActionWaitActivity : BaseActivity() {

    private lateinit var action: WaitAction
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_action_wait)
        setTitle("等待", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val action = intent.getStringExtra("action")
        if (action == null) this.action = WaitAction()
        else this.action = Gsons.gson.fromJson(action, WaitAction::class.java)

        ui()
        data()
    }
    override fun doRight() {
        action.waitTemplate = act.waitTemplate.text()
        if (action.waitTemplate.isBlank()) return showError("请输入等待表达式！")
        action.timeout = act.timeout.text().let { if (it.isBlank()) null else it }
        action.continueOnTimeout = act.continueOnTimeout.isChecked
        setResult(Activity.RESULT_OK, Intent().putExtra("action", AutomationEditActivity.gsonBuilder.toJson(action)))
        finish()
    }

    private fun ui() {
        this.edit.onClick {
            startActivityForResult(Intent(ctx, TemplateEditActivity::class.java)
                    .putExtra("template", act.waitTemplate.text().trim()), 100)
        }
        act.timeout.onClick {
            showDialog(R.layout.dialog_choice_time, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.findViewById<TextView>(R.id.title).setText("等待")
                    contentView.findViewById<DateTimePicker>(R.id.picker).time = action.timeout?.ktime()?.time ?: -TimeZone.getDefault().rawOffset.toLong()
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        action.timeout = contentView.findViewById<DateTimePicker>(R.id.picker).date.ktime()
                        act.timeout.text = action.timeout
                    } else {
                        action.timeout = null
                        act.timeout.text = ""
                    }
                    dialog.dismiss()
                }
            })
        }
    }
    private fun data() {
        act.waitTemplate.setText(action.waitTemplate)
        act.timeout.text = action.timeout
        act.continueOnTimeout.isChecked = action.continueOnTimeout
    }

    @ActivityResult(requestCode = 100)
    private fun afterEdit(data: Intent?) {
        if (data == null || !data.hasExtra("template")) return
        act.waitTemplate.setText(data.getStringExtra("template"))
    }

    companion object {
        private val regex = """^\{\{.*\}\}$""".toRegex()
        fun desc(action: WaitAction): String {
            val result = StringBuilder()
            if (regex.matches(action.waitTemplate)) result.append("等待表达式")
            else result.append("等待${action.waitTemplate}")
            if (!action.timeout.isNullOrBlank()) {
                result.append("，${action.timeout}超时")
                if (action.continueOnTimeout) result.append("，超时后继续")
            }
            return result.toString()
        }
    }
}

