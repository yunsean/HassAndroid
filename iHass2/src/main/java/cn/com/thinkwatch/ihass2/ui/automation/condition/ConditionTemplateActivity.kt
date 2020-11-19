package cn.com.thinkwatch.ihass2.ui.automation.condition

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.model.automation.*
import cn.com.thinkwatch.ihass2.ui.AutomationEditActivity
import cn.com.thinkwatch.ihass2.ui.TemplateEditActivity
import cn.com.thinkwatch.ihass2.utils.Gsons
import com.dylan.uiparts.activity.ActivityResult
import com.yunsean.dynkotlins.extensions.*
import kotlinx.android.synthetic.main.activity_hass_automation_condition_template.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick

class ConditionTemplateActivity : BaseActivity() {

    private lateinit var condition: TemplateCondition
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_condition_template)
        setTitle("模板条件", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val condition = intent.getStringExtra("condition")
        if (condition == null) this.condition = TemplateCondition()
        else this.condition = Gsons.gson.fromJson(condition, TemplateCondition::class.java)

        ui()
        data()
    }
    override fun doRight() {
        condition.valueTemplate = act.valueTemplate.text().trim()
        if (condition.valueTemplate.isBlank()) return toastex("请输入模板参数！")
        setResult(Activity.RESULT_OK, Intent().putExtra("condition", AutomationEditActivity.gsonBuilder.toJson(condition)))
        finish()
    }

    private fun ui() {
        this.edit.onClick {
            startActivityForResult(Intent(ctx, TemplateEditActivity::class.java)
                    .putExtra("template", act.valueTemplate.text().trim()), 100)
        }
    }

    private fun data() {
        act.valueTemplate.setText(condition.valueTemplate)
    }

    @ActivityResult(requestCode = 100)
    private fun afterEdit(data: Intent?) {
        if (data == null || !data.hasExtra("template")) return
        act.valueTemplate.setText(data.getStringExtra("template"))
    }
}

