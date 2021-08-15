package cn.com.thinkwatch.ihass2.ui.automation.condition

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.automation.*
import cn.com.thinkwatch.ihass2.ui.AttributeListActivity
import cn.com.thinkwatch.ihass2.ui.AutomationEditActivity
import cn.com.thinkwatch.ihass2.ui.EntityListActivity
import cn.com.thinkwatch.ihass2.utils.Gsons
import com.dylan.uiparts.activity.ActivityResult
import com.yunsean.dynkotlins.extensions.*
import kotlinx.android.synthetic.main.activity_hass_automation_condition_numberic.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.lang.StringBuilder

class ConditionNumbericActivity : BaseActivity() {

    private lateinit var condition: NumericStateCondition
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_condition_numberic)
        setTitle("数字量条件", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val condition = intent.getStringExtra("condition")
        if (condition == null) this.condition = NumericStateCondition()
        else this.condition = Gsons.gson.fromJson(condition, NumericStateCondition::class.java)

        ui()
        data()
    }
    override fun doRight() {
        if (condition.entityId.isBlank()) return showError("请选择观察的目标！")
        condition.above = act.above.text().trim().toFloatOrNull()?.toBigDecimal()
        condition.below = act.below.text().trim().toFloatOrNull()?.toBigDecimal()
        if (condition.above == null && condition.below == null) return showError("上限和下限必须设置一个！")
        condition.valueTemplate = act.valueTemplate.text().let { if (it.isBlank()) null else it }
        setResult(Activity.RESULT_OK, Intent().putExtra("condition", AutomationEditActivity.gsonBuilder.toJson(condition)))
        finish()
    }

    private fun ui() {
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
        act.above.setText(condition.above?.toString() ?: "")
        act.below.setText(condition.below?.toString() ?: "")
        act.valueTemplate.setText(condition.valueTemplate)
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
        if (entityIds == null || entityIds.size < 1) return
        db.getEntity(entityIds.get(0))?.let {
            condition.entityId = it.entityId
            act.entity.text = it.friendlyName
            act.addAttribute.visibility = View.VISIBLE
        }
    }

    companion object {
        fun desc(condition: NumericStateCondition): String {
            val result = StringBuilder()
            result.append(HassApplication.application.db.getEntity(condition.entityId)?.friendlyName ?: condition.entityId)
            if (!condition.attribute.isNullOrBlank()) result.append(".${condition.attribute}")
            if (condition.valueTemplate.isNullOrBlank()) result.append("的状态")
            else result.append("的值")
            if (condition.above != null) result.append("高于${condition.above}")
            if (condition.below != null) result.append("低于${condition.below}")
            return result.toString()
        }
    }
}

