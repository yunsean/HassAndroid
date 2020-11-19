package cn.com.thinkwatch.ihass2.ui

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.network.BaseApi
import cn.com.thinkwatch.ihass2.network.http.HttpRestApi
import cn.com.thinkwatch.ihass2.ui.automation.trigger.TriggerEventActivity
import cn.com.thinkwatch.ihass2.utils.AddableRecylerAdapter
import cn.com.thinkwatch.ihass2.utils.SimpleItemTouchHelperCallback
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.common.sketch.Dialogs
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_hass_automation_trigger_event.*
import kotlinx.android.synthetic.main.activity_hass_entity_config.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import kotlinx.android.synthetic.main.listitem_entity_config.view.*
import kotlinx.android.synthetic.main.listitem_textview.view.*
import okhttp3.MediaType
import okhttp3.RequestBody
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.layoutInflater
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.json.JSONObject

class EntityConfigActivity : BaseActivity() {

    private lateinit var entityId: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithLoadable(R.layout.activity_hass_entity_config)
        setTitle("自定义", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val entityId = intent.getStringExtra("entityId")
        if (entityId == null) return finish()
        this.entityId = entityId

        ui()
        data()
    }
    override fun doRight() {
        val json = JSONObject()
        datas.forEach {
            json.put(it.key, it.value)
        }
        val body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json.toString())
        val waiting = Dialogs.showWait(ctx)
        BaseApi.jsonApi(cfg.haHostUrl, HttpRestApi::class.java)
                .setConfigEntity(cfg.haPassword, cfg.haToken, entityId, body)
                .withNext {
                    waiting.dismiss()
                    finish()
                }
                .error {
                    it.toastex()
                    waiting.dismiss()
                }
                .subscribeOnMain {
                    if (disposable == null) disposable = CompositeDisposable(it)
                    else disposable?.add(it)
                }
    }

    private val datas = mutableListOf<ParamItem>()
    private lateinit var dataAdapter: LocalRecylerAdapter<ParamItem>
    private var removing = false
    private fun ui() {
        act.entityId.text = this.entityId
        this.dataAdapter = LocalRecylerAdapter(R.layout.listitem_entity_config, datas) {
            view, index, item, viewHolder ->
            viewHolder.paramItem = item
            var values: Map<String, String>? = null
            if (ParamNames.containsKey(item.key)) {
                view.field.setText("")
                view.field.setHint(ParamNames.get(item.key)?.name)
                values = ParamNames.get(item.key)?.values
            } else {
                view.field.setText(item.key)
                view.field.setHint("")
            }
            if (values != null) {
                view.pick.onClick {
                    showDialog(R.layout.dialog_list_view, object : OnSettingDialogListener {
                        override fun onSettingDialog(dialog: Dialog, contentView: View) {
                            contentView.recyclerView.layoutManager = LinearLayoutManager(act)
                            val adaptar = RecyclerAdapter(R.layout.listitem_textview, values.keys.toList()) {
                                view, index, item2 ->
                                view.text.text = values.get(item2)
                                view.onClick {
                                    dialog.dismiss()
                                    item.value = item2
                                    dataAdapter.notifyDataSetChanged()
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
            } else {
                view.pick.onClick {
                    showDialog(R.layout.dialog_list_view, object : OnSettingDialogListener {
                        override fun onSettingDialog(dialog: Dialog, contentView: View) {
                            contentView.recyclerView.layoutManager = LinearLayoutManager(act)
                            val adaptar = RecyclerAdapter(R.layout.listitem_textview, listOf("选择图标", "选择实体")) {
                                view, action, item3 ->
                                view.text.text = item3
                                view.onClick {
                                    dialog.dismiss()
                                    focusIndex = index
                                    when (action) {
                                        0-> Intent(act, MdiListActivity::class.java)
                                                .putExtra("icon", item.value)
                                                .start(act, 106)
                                        1-> Intent(act, EntityListActivity::class.java)
                                                .putExtra("singleOnly", true)
                                                .start(act, 107)
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
            }
            view.value.setText(item.value)
            view.remove.onClick {
                datas.removeAt(index)
                dataAdapter.notifyDataSetChanged()
            }
        }.setOnCreateClicked(R.layout.listitem_automation_entity_add) {
            showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.recyclerView.layoutManager = LinearLayoutManager(act)
                    var has = datas.map { it.key }
                    val keys = ParamNames.keys.filter { !has.contains(it) }.toMutableList()
                    keys.add("")
                    val adaptar = RecyclerAdapter(R.layout.listitem_textview, keys) {
                        view, index, item ->
                        view.text.text = if (item.isBlank()) "自定义" else ParamNames.get(item)?.name
                        view.onClick {
                            dialog.dismiss()
                            datas.add(ParamItem(item, ""))
                            dataAdapter.notifyDataSetChanged()
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
        act.params.layoutManager = LinearLayoutManager(ctx)
        act.params.adapter = this.dataAdapter
    }

    private var focusIndex: Int = -1
    @ActivityResult(requestCode = 106)
    private fun afterMdi(data: Intent?) {
        if (focusIndex < 0 || focusIndex >= datas.size) return
        val icon = data?.getStringExtra("icon")
        if (icon.isNullOrBlank()) return
        datas.get(focusIndex).value = icon ?: ""
        dataAdapter.notifyDataSetChanged()
        focusIndex = -1
    }
    @ActivityResult(requestCode = 107)
    private fun afterEntity(data: Intent?) {
        if (focusIndex < 0 || focusIndex >= datas.size) return
        val entityIds = data?.getStringArrayExtra("entityIds")
        if (entityIds == null || entityIds.size < 1) return
        entityIds.get(0)?.let {
            datas.get(focusIndex).value = it
            dataAdapter.notifyDataSetChanged()
        }
        focusIndex = -1
    }

    private fun data() {
        BaseApi.jsonApi(cfg.haHostUrl, HttpRestApi::class.java)
                .getConfigEntity(cfg.haPassword, cfg.haToken, entityId)
                .withNext {
                    datas.clear()
                    it.global?.forEach { datas.add(ParamItem(it.key, it.value)) }
                    var has = datas.map { it.key }
                    it.global?.filter { !has.contains(it.key) }?.forEach { datas.add(ParamItem(it.key, it.value)) }
                    dataAdapter.notifyDataSetChanged()
                    loadable?.dismissLoading()
                }
                .error {
                    it.toastex()
                    finish()
                }
                .subscribeOnMain {
                    if (disposable == null) disposable = CompositeDisposable(it)
                    else disposable?.add(it)
                }
    }

    companion object {
        private data class ParamValue(val name: String,
                                      val values: Map<String, String>? = null)
        private val ParamNames = mapOf(
                "friendly_name" to ParamValue("名称"),
                "icon" to ParamValue("图标"),
                "unit_of_measurement" to ParamValue("单位"),
                "ihass_detail" to ParamValue("HASS属性名"),
                "ihass_state" to ParamValue("HASS状态名"),
                "ihass_total" to ParamValue("HASS统计状态"),
                "ihass_total_extra" to ParamValue("HASS统计减除秒"),
                "ihass_icon" to ParamValue("HASS图标"),
                "ihass_detail_reduce" to ParamValue("HASS状态反转秒"),
                "ihass_days" to ParamValue("历史展示天数"),
                "hagenie_deviceName" to ParamValue("TMALL设备名"),
                "hagenie_deviceType" to ParamValue("TMALL设备类型", mapOf(
                        "television" to "电视",
                        "light" to "灯",
                        "aircondition" to "空调",
                        "airpurifier" to "空气净化器",
                        "outlet" to "插座",
                        "switch" to "开关",
                        "roboticvacuum" to "扫地机器人",
                        "curtain" to "窗帘",
                        "humidifier" to "加湿器",
                        "fan" to "风扇",
                        "bottlewarmer" to "暖奶器",
                        "soymilkmaker" to "豆浆机",
                        "kettle" to "电热水壶",
                        "watercooler" to "饮水机",
                        "cooker" to "电饭煲",
                        "waterheater" to "热水器",
                        "oven" to "烤箱",
                        "waterpurifier" to "净水器",
                        "fridge" to "冰箱",
                        "STB" to "机顶盒",
                        "sensor" to "传感器",
                        "washmachine" to "洗衣机",
                        "smartbed" to "智能床",
                        "aromamachine" to "香薰机",
                        "window" to "窗",
                        "kitchenventilator" to "抽油烟机",
                        "fingerprintlock" to "指纹锁",
                        "telecontroller" to "万能遥控器",
                        "dishwasher" to "洗碗机",
                        "dehumidifier" to "除湿机")),
                "hagenie_zone" to ParamValue("TMALL区域", mapOf(
                        "门口" to "门口",
                        "客厅" to "客厅",
                        "卧室" to "卧室",
                        "客房" to "客房",
                        "主卧" to "主卧",
                        "次卧" to "次卧",
                        "书房" to "书房",
                        "餐厅" to "餐厅",
                        "厨房" to "厨房",
                        "洗手间" to "洗手间",
                        "浴室" to "浴室",
                        "阳台" to "阳台",
                        "宠物房" to "宠物房",
                        "老人房" to "老人房",
                        "儿童房" to "儿童房",
                        "婴儿房" to "婴儿房",
                        "保姆房" to "保姆房",
                        "玄关" to "玄关",
                        "一楼" to "一楼",
                        "二楼" to "二楼",
                        "三楼" to "三楼",
                        "四楼" to "四楼",
                        "楼梯" to "楼梯",
                        "走廊" to "走廊",
                        "过道" to "过道",
                        "楼上" to "楼上",
                        "楼下" to "楼下",
                        "影音室" to "影音室",
                        "娱乐室" to "娱乐室",
                        "工作间" to "工作间",
                        "杂物间" to "杂物间",
                        "衣帽间" to "衣帽间",
                        "吧台" to "吧台",
                        "花园" to "花园",
                        "办公室" to "办公室"
                )))

        private data class ParamItem(var key: String,
                                     var value: String = "")
        private class LocalRecylerAdapter<T>(val layoutResourceId: Int,
                                               var items: MutableList<T>? = null,
                                               val init: (View, Int, T, ViewHolder<T>) -> Unit) :
                RecyclerView.Adapter<LocalRecylerAdapter.ViewHolder<T>>(),
                SimpleItemTouchHelperCallback.ItemTouchHelperAdapter {

            private var createLayoutResId: Int = 0
            private var onCreateClicked: (()->Unit)? = null

            fun setOnCreateClicked(createLayoutResId: Int, onCreate: ()->Unit): LocalRecylerAdapter<T> {
                this.createLayoutResId = createLayoutResId
                this.onCreateClicked = onCreate
                return this
            }
            override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
                items?.let {
                    val panel = it.get(fromPosition)
                    it.removeAt(fromPosition)
                    it.add(toPosition, panel)
                    notifyItemMoved(fromPosition, toPosition)
                }
                return false
            }
            override fun onItemDismiss(position: Int) {
                items?.removeAt(position)
                notifyItemRemoved(position)
            }
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<T> {
                val view = parent.context.layoutInflater.inflate(viewType, parent, false)
                return ViewHolder(view, init)
            }
            override fun onBindViewHolder(holder: ViewHolder<T>, position: Int) {
                if (position < (items?.size ?: 0)) holder.bindForecast(position, items?.get(position))
                else holder.itemView.onClick { onCreateClicked?.invoke() }
            }
            override fun getItemViewType(position: Int): Int {
                if (position < (items?.size ?: 0)) return layoutResourceId
                else return createLayoutResId
            }
            override fun getItemCount() = (items?.size ?: 0) + (if (createLayoutResId == 0) 0 else 1)

            class ViewHolder<T>(view: View, val init: (View, Int, T, ViewHolder<T>) -> Unit) : RecyclerView.ViewHolder(view) {
                var paramItem: ParamItem? = null
                init {
                    view.field.onChanged { if (!it.isNullOrBlank()) paramItem?.key = it.toString() }
                    view.value.onChanged { paramItem?.value = it.toString() }
                }
                fun bindForecast(index: Int, item: T?) {
                    item?.apply {
                        try { init(itemView, index, item, this@ViewHolder) } catch (ex: Exception) { ex.printStackTrace() }
                    }
                }
            }
        }
    }
}

