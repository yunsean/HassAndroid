package cn.com.thinkwatch.ihass2.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.inputmethod.EditorInfo
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.network.base.api
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.model.service.Service
import com.dylan.common.utils.Utility
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import io.reactivex.Observable
import kotlinx.android.synthetic.main.activity_hass_entity_list.*
import kotlinx.android.synthetic.main.listitem_service_item.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.json.JSONObject


class ServiceListActivity : BaseActivity() {

    private var checkedService: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_service_list)
        setTitle("服务列表", true)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)
        checkedService = intent.getStringExtra("serviceId") ?: ""

        ui()
        data()
    }

    private var showServices = mutableListOf<Service>()
    private var allServices: List<Service>? = null
    private lateinit var adapter: RecyclerAdapter<Service>
    private var filterRunnable: Runnable = object: Runnable {
        override fun run() {
            filter()
        }
    }
    private fun ui() {
        this.loading.visibility = View.VISIBLE
        this.adapter = RecyclerAdapter(R.layout.listitem_service_item, showServices) {
            view, index, item ->
            view.name.text = item.name
            view.desc.text = item.description
            view.checked.visibility = if (checkedService == item.name) View.VISIBLE else View.INVISIBLE
            view.onClick {
                checkedService = item.name
                adapter.notifyDataSetChanged()
                val data = Intent()
                data.putExtra("serviceId", item.name)
                data.putExtra("content", generateJson(item))
                setResult(Activity.RESULT_OK, data)
                finish()
            }
        }
        this.recyclerView.adapter = adapter
        this.recyclerView.layoutManager = LinearLayoutManager(this)
        this.recyclerView.addItemDecoration(RecyclerViewDivider()
                .setColor(0xffeeeeee.toInt())
                .setSize(dip2px(1f)))
        this.keyword.onChanged {
            act.keyword.removeCallbacks(filterRunnable)
            act.keyword.postDelayed(filterRunnable, 1000)
        }
        this.keyword.setOnEditorActionListener() { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                act.keyword.removeCallbacks(filterRunnable)
                Utility.hideSoftKeyboard(act)
                filter()
            }
            false
        }
    }
    private fun filter() {
        val keyword = act.keyword.text()
        showServices.clear()
        allServices?.filter {
            keyword.isBlank() or it.name.contains(keyword, true) or it.description.contains(keyword, true)
        }?.let {
            showServices.addAll(it)
        }
        adapter.notifyDataSetChanged()
    }
    private fun generateJson(service: Service): String {
        try {
            val json = JSONObject()
            service.fields?.fields?.keys?.forEach {
                json.put(it, "")
            }
            return json.toString(2)
        } catch (_: Exception) {
            return ""
        }
    }

    private fun data() {
        app.getServices()
                .flatMap {
                    val services = mutableListOf<Service>()
                    it.forEach {
                        val domain = it.domain
                        it.services?.services?.apply{
                            keys.forEach { name ->
                                val service = get(name)
                                service?.name = "${domain}.${name}"
                                services.add(service!!)
                            }
                        }
                    }
                    Observable.just(services)
                }
                .nextOnMain {
                    allServices = it
                    loading?.visibility = View.GONE
                    filter()
                }.error {
                    it.printStackTrace()
                    toastex(it.message ?: "未知错误")
                    finish()
                }
    }
}

