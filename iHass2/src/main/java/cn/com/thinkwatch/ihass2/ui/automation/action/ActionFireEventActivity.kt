package cn.com.thinkwatch.ihass2.ui.automation.action

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
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import kotlinx.android.synthetic.main.activity_hass_automation_action_event.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import kotlinx.android.synthetic.main.listitem_automation_event_data.view.*
import kotlinx.android.synthetic.main.listitem_textview.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.lang.StringBuilder

class ActionFireEventActivity : BaseActivity() {

    private lateinit var action: FireEventAction
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_action_event)
        setTitle("发送事件", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val action = intent.getStringExtra("action")
        if (action == null) this.action = FireEventAction()
        else this.action = Gsons.gson.fromJson(action, FireEventAction::class.java)

        ui()
        data()
    }
    override fun doRight() {
        action.event = act.event.text().let { if (it.isBlank()) return showError("请填写需要发送的事件！") else it }
        action.eventDataTemplate = templates.associateBy({it.first}, {it.second})
        action.eventData = datas.associateBy({it.first}, {it.second})
        setResult(Activity.RESULT_OK, Intent().putExtra("action", AutomationEditActivity.gsonBuilder.toJson(action)))
        finish()
    }

    private val datas = mutableListOf<DataItem>()
    private lateinit var dataAdapter: LocalRecylerAdapter<DataItem>
    private val templates = mutableListOf<DataItem>()
    private lateinit var templateAdapter: LocalRecylerAdapter<DataItem>
    private fun ui() {
        this.dataAdapter = LocalRecylerAdapter(R.layout.listitem_automation_event_data, datas) {
            view, index, item, viewHolder ->
            view.field.setText(item.first)
            view.value.setText(item.second)
            view.field.onChanged { item.first = it.toString() }
            view.value.onChanged { item.second = it.toString() }
            view.remove.onClick {
                datas.removeAt(index)
                dataAdapter.notifyDataSetChanged()
            }
            view.edit.onClick {
                editValue(view.value)
            }
        }.setOnCreateClicked(R.layout.listitem_automation_entity_add) {
            datas.add(DataItem("", ""))
            dataAdapter.notifyDataSetChanged()
        }
        act.datas.layoutManager = LinearLayoutManager(ctx)
        act.datas.adapter = this.dataAdapter

        this.templateAdapter = LocalRecylerAdapter(R.layout.listitem_automation_event_data, templates) {
            view, index, item, viewHolder ->
            view.field.setText(item.first)
            view.value.setText(item.second)
            view.field.onChanged { item.first = it.toString() }
            view.value.onChanged { item.second = it.toString() }
            view.remove.onClick {
                templates.removeAt(index)
                templateAdapter.notifyDataSetChanged()
            }
            view.edit.onClick {
                editValue(view.value)
            }
        }.setOnCreateClicked(R.layout.listitem_automation_entity_add) {
            templates.add(DataItem("", ""))
            templateAdapter.notifyDataSetChanged()
        }
        act.templates.layoutManager = LinearLayoutManager(ctx)
        act.templates.adapter = this.templateAdapter

        act.pickEvent.onClick {
            showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.recyclerView.layoutManager = LinearLayoutManager(act)
                    val adaptar = RecyclerAdapter(R.layout.listitem_textview, systemEvents) {
                        view, index, item ->
                        view.text.text = item.event
                        view.onClick {
                            dialog.dismiss()
                            event.setText(item.event)
                            datas.clear()
                            datas.addAll(item.datas)
                            templates.clear()
                            templates.addAll(item.datas)
                            dataAdapter.notifyDataSetChanged()
                            templateAdapter.notifyDataSetChanged()
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

    private fun data() {
        act.event.setText(action.event)
        action.eventDataTemplate?.let {
            this.templates.clear()
            this.templates.addAll(it.entries.map { DataItem(it.key, it.value) }.toList())
            templateAdapter.notifyDataSetChanged()
        }
        action.eventData?.let {
            this.datas.clear()
            this.datas.addAll(it.entries.map { DataItem(it.key, it.value) }.toList())
            dataAdapter.notifyDataSetChanged()
        }
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

