package cn.com.thinkwatch.ihass2.ui.automation.trigger

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import android.widget.TextView
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.model.automation.*
import cn.com.thinkwatch.ihass2.ui.AutomationEditActivity
import cn.com.thinkwatch.ihass2.ui.EntityListActivity
import cn.com.thinkwatch.ihass2.utils.AddableRecylerAdapter
import cn.com.thinkwatch.ihass2.utils.Gsons
import cn.com.thinkwatch.ihass2.utils.SimpleItemTouchHelperCallback
import com.dylan.dyn3rdparts.pickerview.DateTimePicker
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.*
import kotlinx.android.synthetic.main.activity_hass_automation_trigger_state.*
import kotlinx.android.synthetic.main.listitem_automation_entity_item.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.lang.StringBuilder
import java.util.*

class TriggerStateActivity : BaseActivity() {

    private lateinit var trigger: StateTrigger
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_trigger_state)
        setTitle("状态触发", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val trigger = intent.getStringExtra("trigger")
        if (trigger == null) this.trigger = StateTrigger()
        else this.trigger = Gsons.gson.fromJson(trigger, StateTrigger::class.java)

        ui()
        data()
    }
    override fun doRight() {
        if (entities.size < 1) return toastex("请选择需要观察的目标")
        trigger.entityId = entities.map { it.entityId }.joinToString(",")
        trigger.from = act.from.text().let { if (it.isBlank()) null else it }
        trigger.to = act.to.text().let { if (it.isBlank()) null else it }
        trigger.lasted = act.lasted.text().let { if (it.isBlank()) null else it }
        setResult(Activity.RESULT_OK, Intent().putExtra("trigger", AutomationEditActivity.gsonBuilder.toJson(trigger)))
        finish()
    }

    private val entities = mutableListOf<JsonEntity>()
    private lateinit var touchHelper: ItemTouchHelper
    private fun ui() {
        act.lasted.onClick {
            showDialog(R.layout.dialog_choice_time, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.findViewById<TextView>(R.id.title).setText("持续时长")
                    contentView.findViewById<DateTimePicker>(R.id.picker).time = trigger.lasted?.ktime()?.time ?: -TimeZone.getDefault().rawOffset.toLong()
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        trigger.lasted = contentView.findViewById<DateTimePicker>(R.id.picker).date.ktime()
                        act.lasted.setText(trigger.lasted)
                    } else if (clickedView.id == R.id.cancel) {
                        trigger.lasted = null
                        act.lasted.text = ""
                    }
                    dialog.dismiss()
                }
            })
        }
        val adapter = AddableRecylerAdapter(R.layout.listitem_automation_entity_item, entities) {
            view, index, item, viewHolder ->
            view.name.text = item.friendlyName
            MDIFont.get().setIcon(view.icon, item.mdiIcon)
            view.entityId.text = item.entityId
            view.delete.onClick {
                entities.remove(item)
                act.entityIdView.adapter?.notifyDataSetChanged()
            }
        }
        adapter.setOnCreateClicked(R.layout.listitem_automation_entity_add) {
            startActivityForResult(Intent(ctx, EntityListActivity::class.java), 105)
        }
        act.entityIdView.adapter = adapter
        act.entityIdView.layoutManager = LinearLayoutManager(ctx)
        act.entityIdView.addItemDecoration(RecyclerViewDivider()
                .setColor(0xfff2f2f2.toInt())
                .setSize(dip2px(1f)))
        val callback = SimpleItemTouchHelperCallback(adapter, false)
        touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(entityIdView)
    }
    private fun data() {
        act.from.setText(trigger.from)
        act.to.setText(trigger.to)
        act.lasted.setText(trigger.lasted)
        entities.clear()
        entities.addAll(trigger.entityId.split(",").map { db.getEntity(it) }.filterNotNull())
        act.entityIdView.adapter?.notifyDataSetChanged()
    }

    @ActivityResult(requestCode = 105)
    private fun afterAddEntity(data: Intent?) {
        val entityIds = data?.getStringArrayExtra("entityIds")
        var position = data?.getIntExtra("position", -1) ?: -1
        if (entityIds == null || entityIds.size < 1) return
        entities.addAll(entityIds.map { db.getEntity(it) }.filterNotNull())
        act.entityIdView.adapter?.notifyDataSetChanged()
    }

    companion object {
        fun desc(trigger: StateTrigger): String {
            val result = StringBuilder()
            var from: String? = null
            var to: String? = null
            trigger.entityId.split(",").forEach {
                HassApplication.application.db.getEntity(it)?.let {
                    if (result.isNotBlank()) result.append(" ")
                    trigger.from?.let {t-> from = it.getFriendlyState(t) }
                    trigger.to?.let {t-> to = it.getFriendlyState(t) }
                    result.append(it.friendlyName)
                }
            }
            result.append("状态")
            from?.let { result.append("从$it") }
            to?.let { result.append("变更为$it") }
            trigger.lasted?.trim()?.let { if (it.isNotBlank()) result.append("，持续$it") }
            return result.toString()
        }
    }
}

