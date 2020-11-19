package cn.com.thinkwatch.ihass2.ui.automation.action

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.model.automation.*
import cn.com.thinkwatch.ihass2.ui.*
import cn.com.thinkwatch.ihass2.ui.automation.DataItem
import cn.com.thinkwatch.ihass2.ui.automation.LocalRecylerAdapter
import cn.com.thinkwatch.ihass2.utils.Gsons
import cn.com.thinkwatch.ihass2.utils.SimpleItemTouchHelperCallback
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import kotlinx.android.synthetic.main.activity_hass_automation_action_service.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import kotlinx.android.synthetic.main.listitem_automation_event_data.view.*
import kotlinx.android.synthetic.main.listitem_textview.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.layoutInflater
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.json.JSONObject

class ActionServiceActivity : BaseActivity() {

    private lateinit var action: ServiceAction
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_automation_action_service)
        setTitle("调用服务", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val action = intent.getStringExtra("action")
        if (action == null) this.action = ServiceAction()
        else this.action = Gsons.gson.fromJson(action, ServiceAction::class.java)

        ui()
        data()
    }
    override fun doRight() {
        action.service = act.service.text().let { if (it.isBlank()) return showError("请选择调用的服务！") else it }
        action.dataTemplate = templates.associateBy({it.first}, {it.second})
        action.data = datas.associateBy({it.first}, {it.second})
        setResult(Activity.RESULT_OK, Intent().putExtra("action", AutomationEditActivity.gsonBuilder.toJson(action)))
        finish()
    }

    private val datas = mutableListOf<DataItem>()
    private lateinit var dataAdapter: LocalRecylerAdapter<DataItem>
    private val templates = mutableListOf<DataItem>()
    private lateinit var templateAdapter: LocalRecylerAdapter<DataItem>
    private fun ui() {
        this.service.onClick {
            Intent(act, ServiceListActivity::class.java)
                    .putExtra("serviceId", action.service)
                    .start(act, 105)
        }
        this.choiceService.onClick {
            startActivityForResult(Intent(ctx, InterceptGuideActivity::class.java), 108)
        }
        this.dataAdapter = LocalRecylerAdapter(R.layout.listitem_automation_event_data, datas) {
            view, index, item, viewHolder ->
            viewHolder.dataItem = item
            view.field.setText(item.first)
            view.value.setText(item.second)
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
            viewHolder.dataItem = item
            view.field.setText(item.first)
            view.value.setText(item.second)
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
        act.service.setText(action.service)
        action.dataTemplate?.let {
            this.templates.clear()
            this.templates.addAll(it.entries.map { DataItem(it.key, it.value) }.toList())
            templateAdapter.notifyDataSetChanged()
        }
        action.data?.let {
            this.datas.clear()
            this.datas.addAll(it.entries.map { DataItem(it.key, it.value) }.toList())
            dataAdapter.notifyDataSetChanged()
        }
    }

    @ActivityResult(requestCode = 105)
    private fun afterService(data: Intent?) {
        data?.getStringExtra("serviceId")?.let {
            action.service = it
            service.text = action.service
            try {
                datas.clear()
                templates.clear()
                val jsonObject = JSONObject(data.getStringExtra("content"))
                val iterator = jsonObject.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    val value = jsonObject.optString(key) ?: ""
                    datas.add(DataItem(key, value))
                    templates.add(DataItem(key, value))
                }
                dataAdapter.notifyDataSetChanged()
                templateAdapter.notifyDataSetChanged()
            } catch (_: Exception) {
            }
        }
    }

    @ActivityResult(requestCode = 108)
    private fun serviceIntercepted(data: Intent?) {
        if (data == null || !data.hasExtra("serviceId")) return
        action.service = data.getStringExtra("serviceId")
        this.service.text = action.service
        try {
            datas.clear()
            val jsonObject = JSONObject(data.getStringExtra("content"))
            val iterator = jsonObject.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                val value = jsonObject.optString(key) ?: ""
                datas.add(DataItem(key, value))
            }
            this.templates.clear()
            dataAdapter.notifyDataSetChanged()
            templateAdapter.notifyDataSetChanged()
        } catch (_: Exception) {
        }
    }
}

