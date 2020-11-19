package cn.com.thinkwatch.ihass2.ui.automation.trigger

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.model.automation.*
import cn.com.thinkwatch.ihass2.ui.AutomationEditActivity
import cn.com.thinkwatch.ihass2.utils.Gsons
import com.dylan.dyn3rdparts.pickerview.DateTimePicker
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import kotlinx.android.synthetic.main.activity_hass_automation_trigger_time.*
import kotlinx.android.synthetic.main.dialog_hass_prompt.view.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import kotlinx.android.synthetic.main.listitem_textview.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.util.*

class TriggerTimeActivity : BaseActivity() {

    private lateinit var trigger: TimeTrigger
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_trigger_time)
        setTitle("时间触发", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val trigger = intent.getStringExtra("trigger")
        if (trigger == null) this.trigger = TimeTrigger()
        else this.trigger = Gsons.gson.fromJson(trigger, TimeTrigger::class.java)

        ui()
        data()
    }
    override fun doRight() {
        if (act.modePattern.isChecked) {
            trigger.at = null
            trigger.hours = act.hours.text().let { if (it.isBlank()) null else it }
            trigger.minutes = act.minutes.text().let { if (it.isBlank()) null else it }
            trigger.seconds = act.seconds.text().let { if (it.isBlank()) null else it }
        } else {
            trigger.at = act.at.text().let { if (it.isBlank()) return showError("请输入触发时间") else it }
            trigger.hours = null
            trigger.minutes = null
            trigger.seconds = null
        }
        setResult(Activity.RESULT_OK, Intent().putExtra("trigger", AutomationEditActivity.gsonBuilder.toJson(trigger)))
        finish()
    }

    private fun ui() {
        act.modeAt.onClick { updateChecked() }
        act.modePattern.onClick { updateChecked() }
        act.at.onClick {
            showDialog(R.layout.dialog_choice_time, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.findViewById<TextView>(R.id.title).setText("触发时间点")
                    contentView.findViewById<DateTimePicker>(R.id.picker).time = trigger.at?.ktime()?.time ?: -TimeZone.getDefault().rawOffset.toLong()
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        set(at = contentView.findViewById<DateTimePicker>(R.id.picker).date.ktime())
                    }
                    dialog.dismiss()
                }
            })
        }
        act.preset.onClick {
            showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.recyclerView.layoutManager = LinearLayoutManager(act)
                    val adaptar = RecyclerAdapter(R.layout.listitem_textview, patterns) {
                        view, index, item ->
                        view.text.text = item
                        view.onClick {
                            dialog.dismiss()
                            doPattern(index)
                        }
                    }
                    val footer = layoutInflater.inflate(R.layout.layout_cancel, contentView.recyclerView, false)
                    footer.onClick { dialog.dismiss() }
                    contentView.recyclerView.adapter = RecyclerAdapterWrapper(adaptar)
                            .addFootView(footer)
                    contentView.recyclerView.addItemDecoration(RecyclerViewDivider()
                            .setColor(0xffeeeeee.toInt())
                            .setSize(1))
                }
            }, null, null)
        }
    }
    private fun doPattern(index: Int) {
        when (index) {
            0-> showDialog(R.layout.dialog_hass_prompt, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.title.text = "每指定小时触发"
                    contentView.content.text = "请输入间隔小时数："
                    contentView.input.gravity = Gravity.CENTER
                    contentView.input.inputType = InputType.TYPE_NUMBER_FLAG_SIGNED
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object: OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        val number = contentView.input.text().trim().toIntOrNull()
                        if (number == null) return toastex("请输入数字！")
                        set(hours = "/$number")
                    }
                    dialog.dismiss()
                }
            })
            1-> showDialog(R.layout.dialog_hass_prompt, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.title.text = "每指定分钟触发"
                    contentView.content.text = "请输入间隔分钟数："
                    contentView.input.gravity = Gravity.CENTER
                    contentView.input.inputType = InputType.TYPE_NUMBER_FLAG_SIGNED
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object: OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        val number = contentView.input.text().trim().toIntOrNull()
                        if (number == null) return toastex("请输入数字！")
                        set(minutes = "/$number")
                    }
                    dialog.dismiss()
                }
            })
            2-> showDialog(R.layout.dialog_hass_prompt, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.title.text = "每指定秒数触发"
                    contentView.content.text = "请输入间隔秒数："
                    contentView.input.gravity = Gravity.CENTER
                    contentView.input.inputType = InputType.TYPE_NUMBER_FLAG_SIGNED
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object: OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        val number = contentView.input.text().trim().toIntOrNull()
                        if (number == null) return toastex("请输入数字！")
                        set(seconds = "/$number")
                    }
                    dialog.dismiss()
                }
            })
            3-> showDialog(R.layout.dialog_hass_prompt, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.title.text = "每小时指定分钟触发"
                    contentView.content.text = "每小时触发分钟值："
                    contentView.input.gravity = Gravity.CENTER
                    contentView.input.inputType = InputType.TYPE_NUMBER_FLAG_SIGNED
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object: OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        val number = contentView.input.text().trim().toIntOrNull()
                        if (number == null) return toastex("请输入数字！")
                        set(minutes = "$number")
                    }
                    dialog.dismiss()
                }
            })
            4-> showDialog(R.layout.dialog_hass_prompt, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.title.text = "每分钟指定秒数触发"
                    contentView.content.text = "每分钟触发秒数值："
                    contentView.input.gravity = Gravity.CENTER
                    contentView.input.inputType = InputType.TYPE_NUMBER_FLAG_SIGNED
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object: OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        val number = contentView.input.text().trim().toIntOrNull()
                        if (number == null) return toastex("请输入数字！")
                        set(seconds = "$number")
                    }
                    dialog.dismiss()
                }
            })
            5-> showDialog(R.layout.dialog_hass_prompt, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.title.text = "指点小时点每分钟触发"
                    contentView.content.text = "输入触发小时数字："
                    contentView.input.gravity = Gravity.CENTER
                    contentView.input.inputType = InputType.TYPE_NUMBER_FLAG_SIGNED
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object: OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        val number = contentView.input.text().trim().toIntOrNull()
                        if (number == null) return toastex("请输入数字！")
                        set(hours = "$number", minutes = "*")
                    }
                    dialog.dismiss()
                }
            })
        }
    }
    private fun set(at: String? = null, hours: String? = null, minutes: String? = null, seconds: String? = null) {
        act.modeAt.isChecked = !at.isNullOrBlank()
        act.modePattern.isChecked = at.isNullOrBlank()
        trigger.at = at
        act.at.text = if (!trigger.at.isNullOrBlank()) desc(trigger) else ""
        trigger.hours = hours
        trigger.minutes = minutes
        trigger.seconds = seconds
        act.hours.setText(hours)
        act.minutes.setText(minutes)
        act.seconds.setText(seconds)
    }
    private val patterns = listOf("每隔N小时", "每隔N分钟", "每隔N秒",
            "每小时第N分钟", "每分钟第N秒", "N点每分钟")
    private fun data() {
        act.modeAt.isChecked = !trigger.at.isNullOrBlank()
        act.modePattern.isChecked = trigger.at.isNullOrBlank()
        act.at.text = if (!trigger.at.isNullOrBlank()) desc(trigger) else ""
        act.hours.setText(trigger.hours)
        act.minutes.setText(trigger.minutes)
        act.seconds.setText(trigger.seconds)
        updateChecked()
    }
    private fun updateChecked() {
        act.at.visibility = if (act.modeAt.isChecked) View.VISIBLE else View.GONE
        act.patternPanel.visibility = if (act.modePattern.isChecked) View.VISIBLE else View.GONE
    }

    companion object {
        fun desc(trigger: TimeTrigger): String? {
            if (!trigger.at.isNullOrBlank()) return trigger.at!!
            val loopPattern = Regex("""/(\d{1,3})""")
            val numberPattern = Regex("""(\d{1,3})""")
            val result = StringBuilder()
            if (trigger.hours != null) {
                var matchResult = loopPattern.matchEntire(trigger.hours!!)
                if (matchResult != null) return@desc "每隔${matchResult.groupValues.get(1)}小时"
                matchResult = numberPattern.matchEntire(trigger.hours!!)
                if (matchResult != null) {
                    result.append("${matchResult.groupValues.get(1)}点")
                } else if (trigger.hours === "*") {
                    result.append("每小时")
                } else {
                    return null
                }
            }
            if (trigger.minutes != null) {
                var matchResult = loopPattern.matchEntire(trigger.minutes!!)
                if (matchResult != null) return@desc "每隔${matchResult.groupValues.get(1)}分"
                matchResult = numberPattern.matchEntire(trigger.minutes!!)
                if (matchResult != null) {
                    if (result.isBlank()) result.append("每小时")
                    result.append("第${matchResult.groupValues.get(1)}分")
                } else if (trigger.minutes === "*") {
                    result.append("每分")
                } else {
                    return null
                }
            }
            if (trigger.seconds != null) {
                var matchResult = loopPattern.matchEntire(trigger.seconds!!)
                if (matchResult != null) return@desc "每隔${matchResult.groupValues.get(1)}秒"
                matchResult = numberPattern.matchEntire(trigger.seconds!!)
                if (matchResult != null) {
                    if (result.isBlank()) result.append("每分钟")
                    result.append("第${matchResult.groupValues.get(1)}秒")
                } else if (trigger.seconds === "*") {
                    result.append("每秒")
                } else {
                    return null
                }
            }
            if (result.length < 1) return null
            else return result.toString()
        }
    }
}

