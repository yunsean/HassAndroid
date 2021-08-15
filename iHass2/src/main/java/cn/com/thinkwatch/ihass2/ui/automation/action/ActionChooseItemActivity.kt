package cn.com.thinkwatch.ihass2.ui.automation.action

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.MotionEvent
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.model.automation.*
import cn.com.thinkwatch.ihass2.ui.*
import cn.com.thinkwatch.ihass2.ui.automation.condition.*
import cn.com.thinkwatch.ihass2.utils.AddableRecylerAdapter
import cn.com.thinkwatch.ihass2.utils.Gsons
import cn.com.thinkwatch.ihass2.utils.SimpleItemTouchHelperCallback
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.dylan.uiparts.recyclerview.SwipeItemLayout
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import kotlinx.android.synthetic.main.activity_hass_automation_action_choose_item.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import kotlinx.android.synthetic.main.listitem_automation_condition_item.view.content
import kotlinx.android.synthetic.main.listitem_automation_condition_item.view.delete
import kotlinx.android.synthetic.main.listitem_automation_condition_item.view.icon
import kotlinx.android.synthetic.main.listitem_automation_condition_item.view.name
import kotlinx.android.synthetic.main.listitem_automation_edit_item.view.*
import kotlinx.android.synthetic.main.listitem_textview.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick

class ActionChooseItemActivity : BaseActivity() {

    private val conditions = mutableListOf<Condition>()
    private val actions = mutableListOf<Action>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_action_choose_item)
        setTitle("选项", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val item = intent.getStringExtra("item")
        if (item != null) {
            Gsons.gson.fromJson(item, ChooseItem::class.java)?.let {
                it.conditions?.let { this.conditions.addAll(it) }
                it.sequence?.let { this.actions.addAll(it) }
            }
        }
        ui()
    }
    override fun doRight() {
        if (this.actions.size < 1) return showError("请至少设置一个动作！")
        if (this.conditions.size < 1) return showError("请至少设置一个条件！")
        val item = ChooseItem(this.conditions, this.actions)
        val json = AutomationEditActivity.gsonBuilder.toJson(item)
        setResult(Activity.RESULT_OK, Intent().putExtra("item", json))
        finish()
    }

    private var focusIndex: Int? = null
    private fun ui() {
        setupCondition()
        setupAction()
    }

    private lateinit var actionsAdatper: AddableRecylerAdapter<Action>
    private lateinit var actionsTouchHelper: ItemTouchHelper
    private fun setupAction() {
        this.actionsAdatper = AddableRecylerAdapter(R.layout.listitem_automation_edit_item, actions) { 
            view, index, item, holder ->
            view.name.text = item.desc()
            MDIFont.get().setIcon(view.icon, item.icon())
            view.order.setOnTouchListener { v, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) actionsTouchHelper.startDrag(holder)
                false
            }
            view.insert.onClick {
                addAction(index)
            }
            view.delete.onClick {
                actions.remove(item)
                actionsAdatper.notifyDataSetChanged()
            }
            view.content.onClick {
                when (item.action) {
                    ActionType.service-> ActionServiceActivity::class.java
                    ActionType.delay-> ActionDelayActivity::class.java
                    ActionType.wait-> ActionWaitActivity::class.java
                    ActionType.event-> ActionFireEventActivity::class.java
                    ActionType.condition-> {
                        when ((item as ConditionAction).condition?.condition) {
                            ConditionType.or -> ConditionOrActivity::class.java
                            ConditionType.and -> ConditionAndActivity::class.java
                            ConditionType.numeric_state -> ConditionNumbericActivity::class.java
                            ConditionType.state -> ConditionStateActivity::class.java
                            ConditionType.sun -> ConditionSunActivity::class.java
                            ConditionType.template -> ConditionTemplateActivity::class.java
                            ConditionType.time -> ConditionTimeActivity::class.java
                            ConditionType.zone -> ConditionZoneActivity::class.java
                            else-> null
                        }?.let {
                            focusIndex = index
                            startActivityForResult(Intent(ctx, it).putExtra("condition", AutomationEditActivity.gsonBuilder.toJson(item.condition)), 306)
                        }
                        null
                    }
                    else-> null
                }?.let {
                    focusIndex = index
                    startActivityForResult(Intent(ctx, it).putExtra("action", AutomationEditActivity.gsonBuilder.toJson(item)), 300)
                }
            }
        }.setOnCreateClicked(R.layout.listitem_automation_add_item) {
            addAction()
        }
        act.actions.adapter = this.actionsAdatper
        act.actions.layoutManager = LinearLayoutManager(ctx)
        act.actions.addOnItemTouchListener(SwipeItemLayout.OnSwipeItemTouchListener(this))
        act.actions.addItemDecoration(RecyclerViewDivider()
                .setColor(0xfff2f2f2.toInt())
                .setSize(dip2px(1f)))
        val callback = SimpleItemTouchHelperCallback(this.actionsAdatper, false)
        actionsTouchHelper = ItemTouchHelper(callback)
        actionsTouchHelper.attachToRecyclerView(act.actions)
    }
    @ActivityResult(requestCode = 300)
    private fun afterAction(data: Intent?) {
        val json = data?.getStringExtra("action") ?: return
        if (focusIndex == null) return
        val action = Gsons.gson.fromJson<Action>(json, Action::class.java)
        actions.set(focusIndex!!, action)
        actionsAdatper.notifyDataSetChanged()
        focusIndex = null
    }
    @ActivityResult(requestCode = 301)
    private fun afterAddAction(data: Intent?) {
        val json = data?.getStringExtra("action") ?: return
        val action = Gsons.gson.fromJson<Action>(json, Action::class.java)
        if (focusIndex != null) actions.add(focusIndex!!, action)
        else actions.add(action)
        actionsAdatper.notifyDataSetChanged()
    }
    @ActivityResult(requestCode = 305)
    private fun afterAddConditionAction(data: Intent?) {
        val json = data?.getStringExtra("condition") ?: return
        val condition = Gsons.gson.fromJson<Condition>(json, Condition::class.java)
        if (focusIndex != null) actions.add(focusIndex!!, ConditionAction(condition))
        else actions.add(ConditionAction(condition))
        actionsAdatper.notifyDataSetChanged()
    }
    @ActivityResult(requestCode = 306)
    private fun afterConditionAction(data: Intent?) {
        val json = data?.getStringExtra("condition") ?: return
        if (focusIndex == null) return
        val condition = Gsons.gson.fromJson<Condition>(json, Condition::class.java)
        actions.set(focusIndex!!, ConditionAction(condition))
        actionsAdatper.notifyDataSetChanged()
        focusIndex = null
    }
    private val actionTypes = mapOf(
            "调用服务" to ActionServiceActivity::class.java,
            "延迟" to ActionDelayActivity::class.java,
            "等待" to ActionWaitActivity::class.java,
            "发送事件" to ActionFireEventActivity::class.java,
            "任一满足条件" to ConditionOrActivity::class.java,
            "同时满足条件" to ConditionAndActivity::class.java,
            "数字量条件" to ConditionNumbericActivity::class.java,
            "状态条件" to ConditionStateActivity::class.java,
            "日出条件" to ConditionSunActivity::class.java,
            "时间条件" to ConditionTimeActivity::class.java,
            "模板条件" to ConditionTemplateActivity::class.java,
            "区域条件" to ConditionZoneActivity::class.java)
    private fun addAction(position: Int? = null) {
        focusIndex = position
        showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                contentView.recyclerView.layoutManager = LinearLayoutManager(act)
                val adaptar = RecyclerAdapter(R.layout.listitem_textview, actionTypes.keys.toList()) {
                    view, index, item ->
                    view.text.text = item
                    view.onClick {
                        if (index < 4) startActivityForResult(Intent(ctx, actionTypes.get(item)), 301)
                        else startActivityForResult(Intent(ctx, actionTypes.get(item)), 305)
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

    private lateinit var conditionTouchHelper: ItemTouchHelper
    private lateinit var conditionAdatper: AddableRecylerAdapter<Condition>
    private fun setupCondition() {
        this.conditionAdatper = AddableRecylerAdapter(R.layout.listitem_automation_condition_item, conditions) {
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
        act.conditions.adapter = this.conditionAdatper
        act.conditions.layoutManager = LinearLayoutManager(ctx)
        act.conditions.addOnItemTouchListener(SwipeItemLayout.OnSwipeItemTouchListener(this))
        act.conditions.addItemDecoration(RecyclerViewDivider()
                .setColor(0xfff2f2f2.toInt())
                .setSize(dip2px(1f)))
        val callback = SimpleItemTouchHelperCallback(this.conditionAdatper, false)
        conditionTouchHelper = ItemTouchHelper(callback)
        conditionTouchHelper.attachToRecyclerView(act.conditions)
    }
    @ActivityResult(requestCode = 200)
    private fun afterCondition(data: Intent?) {
        val json = data?.getStringExtra("condition") ?: return
        if (focusIndex == null) return
        val condition = Gsons.gson.fromJson<Condition>(json, Condition::class.java)
        conditions.set(focusIndex!!, condition)
        conditionAdatper.notifyDataSetChanged()
        focusIndex = null
    }
    @ActivityResult(requestCode = 201)
    private fun afterAddCondition(data: Intent?) {
        val json = data?.getStringExtra("condition") ?: return
        val condition = Gsons.gson.fromJson<Condition>(json, Condition::class.java)
        if (focusIndex != null) conditions.add(focusIndex!!, condition)
        else conditions.add(condition)
        conditionAdatper.notifyDataSetChanged()
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

    companion object {
        fun desc(item: ChooseItem): String {
            return "${item.conditions?.size ?: 0}个条件，${item.sequence?.size ?: 0}个动作"
        }
    }
}

