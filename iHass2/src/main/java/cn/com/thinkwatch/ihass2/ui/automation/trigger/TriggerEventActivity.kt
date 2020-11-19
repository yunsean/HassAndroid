package cn.com.thinkwatch.ihass2.ui.automation.trigger

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.model.automation.*
import cn.com.thinkwatch.ihass2.ui.*
import cn.com.thinkwatch.ihass2.ui.automation.DataItem
import cn.com.thinkwatch.ihass2.ui.automation.LocalRecylerAdapter
import cn.com.thinkwatch.ihass2.utils.Gsons
import com.dylan.common.utils.Utility
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.OnSettingDialogListener
import com.yunsean.dynkotlins.extensions.onChanged
import com.yunsean.dynkotlins.extensions.showDialog
import com.yunsean.dynkotlins.extensions.text
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import kotlinx.android.synthetic.main.activity_hass_automation_trigger_event.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import kotlinx.android.synthetic.main.listitem_automation_event_data.view.*
import kotlinx.android.synthetic.main.listitem_textview.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick

class TriggerEventActivity : BaseActivity() {

    private lateinit var trigger: EventTrigger
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_trigger_event)
        setTitle("事件触发", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val trigger = intent.getStringExtra("trigger")
        if (trigger == null) this.trigger = EventTrigger()
        else this.trigger = Gsons.gson.fromJson(trigger, EventTrigger::class.java)

        ui()
        data()
    }
    override fun doRight() {
        Utility.hideSoftKeyboard(act)
        trigger.eventType = act.event.text().let { if (it.isBlank()) return showError("请输入事件！") else it }
        if (eventDatas.any { it.first.isBlank() || it.second.isBlank() }) return showError("请正确填写事件过滤数据！")
        trigger.eventData = eventDatas.associateBy({it.first}, {it.second})
        setResult(Activity.RESULT_OK, Intent().putExtra("trigger", AutomationEditActivity.gsonBuilder.toJson(trigger)))
        finish()
    }

    private val eventDatas = mutableListOf<DataItem>()
    private lateinit var adapter: LocalRecylerAdapter<DataItem>
    private fun ui() {
        this.adapter = LocalRecylerAdapter(R.layout.listitem_automation_event_data, eventDatas) {
            view, index, item, viewHolder ->
            view.field.setText(item.first)
            view.value.setText(item.second)
            view.field.onChanged { item.first = it.toString() }
            view.value.onChanged { item.second = it.toString() }
            view.remove.onClick {
                eventDatas.removeAt(index)
                adapter.notifyDataSetChanged()
            }
            view.edit.onClick {
                editValue(view.value)
            }
        }.setOnCreateClicked(R.layout.listitem_automation_entity_add) {
            eventDatas.add(DataItem("", ""))
            adapter.notifyDataSetChanged()
        }
        this.datasView.layoutManager = LinearLayoutManager(ctx)
        this.datasView.adapter = this.adapter
        this.pickEvent.onClick {
            showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.recyclerView.layoutManager = LinearLayoutManager(act)
                    val adaptar = RecyclerAdapter(R.layout.listitem_textview, systemEvents) {
                        view, index, item ->
                        view.text.text = item.event
                        view.onClick {
                            dialog.dismiss()
                            event.setText(item.event)
                            eventDatas.clear()
                            eventDatas.addAll(item.datas)
                            adapter.notifyDataSetChanged()
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
    private fun data() {
        act.event.setText(trigger.eventType)
        trigger.eventData?.let {
            this.eventDatas.clear()
            this.eventDatas.addAll(it.entries.map { DataItem(it.key, it.value) }.toList())
        }
    }

    private var focusTextView: TextView? = null
    private val editModes = listOf("选择对象", "模板编辑", "文本输入", "通知输入")
    private fun editValue(textView: TextView) {
        showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                contentView.recyclerView.layoutManager = LinearLayoutManager(act)
                val adaptar = RecyclerAdapter(R.layout.listitem_textview, editModes) {
                    view, index, item ->
                    view.text.text = item
                    view.onClick {
                        focusTextView = textView
                        dialog.dismiss()
                        when (index) {
                            0-> startActivityForResult(Intent(ctx, EntityListActivity::class.java).putExtra("singleOnly", true), 700)
                            1-> startActivityForResult(Intent(ctx, TemplateEditActivity::class.java).putExtra("template", textView?.text()), 701)
                            2-> startActivityForResult(Intent(ctx, TextEditActivity::class.java).putExtra("content", textView?.text()), 702)
                            3-> startActivityForResult(Intent(ctx, NotificationInputActivity::class.java).putExtra("content", textView?.text()), 702)
                        }
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
    @ActivityResult(requestCode = 700)
    private fun afterEditService(data: Intent?) {
        val entityIds = data?.getStringArrayExtra("entityIds")
        if (entityIds == null || entityIds.isEmpty()) return
        entityIds.get(0)?.let { focusTextView?.setText(it) }
        focusTextView = null
    }
    @ActivityResult(requestCode = 701)
    private fun afterEditTemplate(data: Intent?) {
        data?.getStringExtra("template")?.let { focusTextView?.setText(it) }
        focusTextView = null
    }
    @ActivityResult(requestCode = 702)
    private fun afterEditContent(data: Intent?) {
        data?.getStringExtra("content")?.let { focusTextView?.setText(it) }
        focusTextView = null
    }

    companion object {
        private data class SystemEvent(val event: String,
                                       val datas: List<DataItem> = listOf())
        private val systemEvents = listOf(SystemEvent("HOMEASSISTANT_START"),
                SystemEvent("HOMEASSISTANT_STOP"),
                SystemEvent("STATE_CHANGED", listOf(DataItem("entity_id"), DataItem("old_state"), DataItem("new_state"))),
                SystemEvent("TIME_CHANGED", listOf(DataItem("now"))),
                SystemEvent("SERVICE_REGISTERED", listOf(DataItem("domain"), DataItem("service"))),
                SystemEvent("TIME_CHANGED", listOf(DataItem("now"))),
                SystemEvent("CALL_SERVICE", listOf(DataItem("domain"), DataItem("service"), DataItem("service_data"), DataItem("service_call_id"))),
                SystemEvent("SERVICE_EXECUTED", listOf(DataItem("service_call_id"))),
                SystemEvent("PLATFORM_DISCOVERED", listOf(DataItem("service"), DataItem("discovered"))),
                SystemEvent("COMPONENT_LOADED", listOf(DataItem("component"))))
    }
}

