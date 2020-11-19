package cn.com.thinkwatch.ihass2.ui.automation.condition

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.model.automation.*
import cn.com.thinkwatch.ihass2.ui.AutomationEditActivity
import cn.com.thinkwatch.ihass2.utils.AddableRecylerAdapter
import cn.com.thinkwatch.ihass2.utils.Gsons
import cn.com.thinkwatch.ihass2.utils.SimpleItemTouchHelperCallback
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import kotlinx.android.synthetic.main.activity_hass_automation_condition_or.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import kotlinx.android.synthetic.main.listitem_automation_condition_item.view.*
import kotlinx.android.synthetic.main.listitem_textview.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.lang.StringBuilder

class ConditionOrActivity : BaseActivity() {

    private lateinit var condition: OrCondition
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_condition_or)
        setTitle("任一满足条件", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val condition = intent.getStringExtra("condition")
        if (condition == null) this.condition = OrCondition()
        else this.condition = Gsons.gson.fromJson(condition, OrCondition::class.java)

        ui()
        data()
    }
    override fun doRight() {
        if (conditions.isEmpty()) return toastex("请至少指定两个条件！")
        val condition = OrCondition(conditions)
        val json = AutomationEditActivity.gsonBuilder.toJson(condition)
        setResult(Activity.RESULT_OK, Intent().putExtra("condition", json))
        finish()
    }

    private var focusIndex: Int? = null
    private val conditions = mutableListOf<Condition>()
    private lateinit var touchHelper: ItemTouchHelper
    private lateinit var adatper: AddableRecylerAdapter<Condition>
    private fun ui() {
        this.adatper = AddableRecylerAdapter(R.layout.listitem_automation_condition_item, conditions) {
            view, index, item, viewHolder ->
            view.name.text = item.desc()
            MDIFont.get().setIcon(view.icon, item.icon())
            view.delete.onClick {
                conditions.remove(item)
                act.conditions.adapter?.notifyDataSetChanged()
            }
            view.content.onClick {
                val clazz = when (item.condition) {
                    ConditionType.or-> ConditionOrActivity::class.java
                    ConditionType.and-> ConditionAndActivity::class.java
                    ConditionType.numeric_state-> ConditionNumbericActivity::class.java
                    ConditionType.state-> ConditionStateActivity::class.java
                    ConditionType.sun-> ConditionSunActivity::class.java
                    ConditionType.template-> ConditionTemplateActivity::class.java
                    ConditionType.time-> ConditionTimeActivity::class.java
                    ConditionType.zone-> ConditionZoneActivity::class.java
                    else-> null
                }
                clazz?.let {
                    focusIndex = index
                    startActivityForResult(Intent(ctx, it).putExtra("condition", AutomationEditActivity.gsonBuilder.toJson(item)), 200)
                }
            }
        }.setOnCreateClicked(R.layout.listitem_automation_entity_add) {
            addCondition()
        }
        act.conditions.adapter = this.adatper
        act.conditions.layoutManager = LinearLayoutManager(ctx)
        act.conditions.addItemDecoration(RecyclerViewDivider()
                .setColor(0xfff2f2f2.toInt())
                .setSize(dip2px(1f)))
        val callback = SimpleItemTouchHelperCallback(this.adatper, false)
        touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(act.conditions)
    }
    private fun data() {
        this.conditions.clear()
        this.conditions.addAll(this.condition.conditions)
        this.adatper.notifyDataSetChanged()
    }

    @ActivityResult(requestCode = 200)
    private fun afterCondition(data: Intent?) {
        val json = data?.getStringExtra("condition") ?: return
        if (focusIndex == null) return
        val condition = Gsons.gson.fromJson<Condition>(json, Condition::class.java)
        conditions.set(focusIndex!!, condition)
        adatper.notifyDataSetChanged()
        focusIndex = null
    }
    private val conditionTypes = mapOf(
            "任一满足条件" to ConditionOrActivity::class.java,
            "同时满足条件" to ConditionAndActivity::class.java,
            "数字量条件" to ConditionNumbericActivity::class.java,
            "状态条件" to ConditionStateActivity::class.java,
            "日出条件" to ConditionSunActivity::class.java,
            "时间条件" to ConditionTimeActivity::class.java,
            "模板条件" to ConditionTemplateActivity::class.java,
            "区域条件" to ConditionZoneActivity::class.java)
    private fun addCondition(position: Int? = null) {
        focusIndex = position
        showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                contentView.recyclerView.layoutManager = LinearLayoutManager(act)
                val adaptar = RecyclerAdapter(R.layout.listitem_textview, conditionTypes.keys.toList()) {
                    view, index, item ->
                    view.text.text = item
                    view.onClick {
                        startActivityForResult(Intent(ctx, conditionTypes.get(item)), 201)
                        dialog.dismiss()
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
    @ActivityResult(requestCode = 201)
    private fun afterAddCondition(data: Intent?) {
        val json = data?.getStringExtra("condition") ?: return
        val condition = Gsons.gson.fromJson<Condition>(json, Condition::class.java)
        if (focusIndex != null) conditions.add(focusIndex!!, condition)
        else conditions.add(condition)
        adatper.notifyDataSetChanged()
    }
}

