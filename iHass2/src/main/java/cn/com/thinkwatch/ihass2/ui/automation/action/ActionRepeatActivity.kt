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
import kotlinx.android.synthetic.main.activity_hass_automation_action_repeat.*
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

class ActionRepeatActivity : BaseActivity() {

    private val whileConditions = mutableListOf<Condition>()
    private val untilConditions = mutableListOf<Condition>()
    private val actions = mutableListOf<Action>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_action_repeat)
        setTitle("循环", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val action = intent.getStringExtra("action")
        if (action != null) {
            Gsons.gson.fromJson(action, RepeatAction::class.java)?.repeat?.let {
                it.count?.let {
                    act.count.setText(it.toString())
                    act.byTimes.isChecked = true
                }
                it.whiles?.let {
                    whileConditions.addAll(it)
                    act.byWhile.isChecked = true
                }
                it.until?.let {
                    untilConditions.addAll(it)
                    act.byUntil.isChecked = true
                }
                it.sequence?.let {
                    actions.addAll(it)
                }
            }
        }
        ui()
    }
    override fun doRight() {
        if (this.actions.size < 1) return showError("请至少设置一个默认动作！")
        val item = if (act.byTimes.isChecked) {
            val count = act.count.text().toIntOrNull() ?: 0
            if (count < 1) return showError("请设置循环次数！")
            RepeatItem(null, null, count, actions)
        } else if (act.byWhile.isChecked) {
            if (whileConditions.size < 1) return showError("请至少设置一个判断条件！")
            RepeatItem(this.whileConditions, null, null, actions)
        } else if (act.byUntil.isChecked) {
            if (untilConditions.size < 1) return showError("请至少设置一个判断条件！")
            RepeatItem(null, this.untilConditions, null, actions)
        } else {
            return showError("请选择循环方式！")
        }
        val action = RepeatAction(item)
        val json = AutomationEditActivity.gsonBuilder.toJson(action)
        setResult(Activity.RESULT_OK, Intent().putExtra("action", json))
        finish()
    }

    private fun ui() {
        setupWhileConditions()
        setupUntilConditions()
        setupAction()
        act.byTimes.onClick { updateChecked() }
        act.byWhile.onClick { updateChecked() }
        act.byUntil.onClick { updateChecked() }
        updateChecked()
    }
    private fun updateChecked() {
        act.byTimesPanel.visibility = if (act.byTimes.isChecked) View.VISIBLE else View.GONE
        act.whileConditions.visibility = if (act.byWhile.isChecked) View.VISIBLE else View.GONE
        act.untilConditions.visibility = if (act.byUntil.isChecked) View.VISIBLE else View.GONE
    }

    private var focusIndex: Int? = null
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

    private val conditionTypes = mapOf(
            "任一满足条件" to ConditionOrActivity::class.java,
            "同时满足条件" to ConditionAndActivity::class.java,
            "数字量条件" to ConditionNumbericActivity::class.java,
            "状态条件" to ConditionStateActivity::class.java,
            "日出条件" to ConditionSunActivity::class.java,
            "时间条件" to ConditionTimeActivity::class.java,
            "模板条件" to ConditionTemplateActivity::class.java,
            "区域条件" to ConditionZoneActivity::class.java)

    private lateinit var whileConditionTouchHelper: ItemTouchHelper
    private lateinit var whileConditionAdatper: AddableRecylerAdapter<Condition>
    private fun setupWhileConditions() {
        this.whileConditionAdatper = AddableRecylerAdapter(R.layout.listitem_automation_condition_item, whileConditions) {
            view, index, item, viewHolder ->
            view.name.text = item.desc()
            MDIFont.get().setIcon(view.icon, item.icon())
            view.delete.onClick {
                whileConditions.remove(item)
                whileConditionAdatper.notifyDataSetChanged()
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
            addWhileCondition()
        }
        act.whileConditions.adapter = this.whileConditionAdatper
        act.whileConditions.layoutManager = LinearLayoutManager(ctx)
        act.whileConditions.addOnItemTouchListener(SwipeItemLayout.OnSwipeItemTouchListener(this))
        act.whileConditions.addItemDecoration(RecyclerViewDivider()
                .setColor(0xfff2f2f2.toInt())
                .setSize(dip2px(1f)))
        val callback = SimpleItemTouchHelperCallback(this.whileConditionAdatper, false)
        whileConditionTouchHelper = ItemTouchHelper(callback)
        whileConditionTouchHelper.attachToRecyclerView(act.whileConditions)
    }
    @ActivityResult(requestCode = 200)
    private fun afterWhileCondition(data: Intent?) {
        val json = data?.getStringExtra("condition") ?: return
        if (focusIndex == null) return
        val condition = Gsons.gson.fromJson<Condition>(json, Condition::class.java)
        whileConditions.set(focusIndex!!, condition)
        whileConditionAdatper.notifyDataSetChanged()
        focusIndex = null
    }
    @ActivityResult(requestCode = 201)
    private fun afterWhileAddCondition(data: Intent?) {
        val json = data?.getStringExtra("condition") ?: return
        val condition = Gsons.gson.fromJson<Condition>(json, Condition::class.java)
        if (focusIndex != null) whileConditions.add(focusIndex!!, condition)
        else whileConditions.add(condition)
        whileConditionAdatper.notifyDataSetChanged()
    }
    private fun addWhileCondition(position: Int? = null) {
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


    private lateinit var untilConditionTouchHelper: ItemTouchHelper
    private lateinit var untilConditionAdatper: AddableRecylerAdapter<Condition>
    private fun setupUntilConditions() {
        this.untilConditionAdatper = AddableRecylerAdapter(R.layout.listitem_automation_condition_item, untilConditions) {
            view, index, item, viewHolder ->
            view.name.text = item.desc()
            MDIFont.get().setIcon(view.icon, item.icon())
            view.delete.onClick {
                untilConditions.remove(item)
                untilConditionAdatper.notifyDataSetChanged()
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
                    startActivityForResult(Intent(ctx, it).putExtra("condition", AutomationEditActivity.gsonBuilder.toJson(item)), 400)
                }
            }
        }.setOnCreateClicked(R.layout.listitem_automation_entity_add) {
            addUntilCondition()
        }
        act.untilConditions.adapter = this.untilConditionAdatper
        act.untilConditions.layoutManager = LinearLayoutManager(ctx)
        act.untilConditions.addOnItemTouchListener(SwipeItemLayout.OnSwipeItemTouchListener(this))
        act.untilConditions.addItemDecoration(RecyclerViewDivider()
                .setColor(0xfff2f2f2.toInt())
                .setSize(dip2px(1f)))
        val callback = SimpleItemTouchHelperCallback(this.untilConditionAdatper, false)
        untilConditionTouchHelper = ItemTouchHelper(callback)
        untilConditionTouchHelper.attachToRecyclerView(act.untilConditions)
    }
    @ActivityResult(requestCode = 400)
    private fun afterUntilCondition(data: Intent?) {
        val json = data?.getStringExtra("condition") ?: return
        if (focusIndex == null) return
        val condition = Gsons.gson.fromJson<Condition>(json, Condition::class.java)
        untilConditions.set(focusIndex!!, condition)
        untilConditionAdatper.notifyDataSetChanged()
        focusIndex = null
    }
    @ActivityResult(requestCode = 401)
    private fun afterUntilAddCondition(data: Intent?) {
        val json = data?.getStringExtra("condition") ?: return
        val condition = Gsons.gson.fromJson<Condition>(json, Condition::class.java)
        if (focusIndex != null) untilConditions.add(focusIndex!!, condition)
        else untilConditions.add(condition)
        untilConditionAdatper.notifyDataSetChanged()
    }
    private fun addUntilCondition(position: Int? = null) {
        focusIndex = position
        showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                contentView.recyclerView.layoutManager = LinearLayoutManager(act)
                val adaptar = RecyclerAdapter(R.layout.listitem_textview, conditionTypes.keys.toList()) {
                    view, index, item ->
                    view.text.text = item
                    view.onClick {
                        startActivityForResult(Intent(ctx, conditionTypes.get(item)), 401)
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
        fun desc(action: RepeatItem): String {
            return if (action.count != null) "循环${action.count}次，执行${action.sequence?.size ?: 0}个动作"
            else if (action.whiles?.size ?: 0 > 0) "满足条件就执行${action.sequence?.size ?: 0}个动作"
            else if (action.until?.size ?: 0 > 0) "执行${action.sequence?.size ?: 0}个动作，直到满足条件退出"
            else "循环"
        }
    }
}

