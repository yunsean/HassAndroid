package cn.com.thinkwatch.ihass2.ui

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.network.base.Api
import cn.com.thinkwatch.ihass2.network.base.api
import cn.com.thinkwatch.ihass2.ui.automation.DataItem
import cn.com.thinkwatch.ihass2.ui.automation.LocalRecylerAdapter
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.google.gson.JsonObject
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_hass_call_service.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import kotlinx.android.synthetic.main.listitem_automation_event_data.view.*
import kotlinx.android.synthetic.main.listitem_textview.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.json.JSONObject

class CallServiceActivity : BaseActivity() {

    private var serviceId: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_call_service)
        setTitle("调用服务", true, "执行")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        ui()
        data()
    }
    override fun doRight() {
        val regex = Regex("(.*?)\\.(.*)")
        regex.find(serviceId)?.let {
            val domain = it.groupValues[1]
            val service = it.groupValues[2]
            if (domain.isBlank() || service.isBlank()) return showError("请选择服务！")
            val json = JsonObject()
            datas.forEach { json.addProperty(it.first, it.second) }
            disposable?.clear()
            api.callService(domain, service, json.toString())
                    .withNext {
                        app.callServiceTips("执行成功！", true)
                    }
                    .error {
                        showError(it.localizedMessage)
                    }
                    .subscribeOnMain {
                        if (disposable == null) disposable = CompositeDisposable(it)
                        else disposable?.add(it)
                    }
        }
    }

    private val datas = mutableListOf<DataItem>()
    private lateinit var dataAdapter: LocalRecylerAdapter<DataItem>
    private fun ui() {
        this.service.onClick {
            Intent(act, ServiceListActivity::class.java)
                    .putExtra("serviceId", serviceId)
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
    }

    @ActivityResult(requestCode = 105)
    private fun afterService(data: Intent?) {
        data?.getStringExtra("serviceId")?.let {
            serviceId = it
            service.text = serviceId
            try {
                datas.clear()
                val jsonObject = JSONObject(data.getStringExtra("content"))
                val iterator = jsonObject.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    val value = jsonObject.optString(key) ?: ""
                    datas.add(DataItem(key, value))
                }
                dataAdapter.notifyDataSetChanged()
            } catch (_: Exception) {
            }
        }
    }

    @ActivityResult(requestCode = 108)
    private fun serviceIntercepted(data: Intent?) {
        if (data == null || !data.hasExtra("serviceId")) return
        serviceId = data.getStringExtra("serviceId")
        this.service.text = serviceId
        try {
            datas.clear()
            val jsonObject = JSONObject(data.getStringExtra("content"))
            val iterator = jsonObject.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                val value = jsonObject.optString(key) ?: ""
                datas.add(DataItem(key, value))
            }
            dataAdapter.notifyDataSetChanged()
        } catch (_: Exception) {
        }
    }
}

