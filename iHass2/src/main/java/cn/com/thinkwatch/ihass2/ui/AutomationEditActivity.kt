package cn.com.thinkwatch.ihass2.ui

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.dto.AutomationResponse
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.model.automation.*
import cn.com.thinkwatch.ihass2.network.BaseApi
import cn.com.thinkwatch.ihass2.network.http.HttpRestApi
import cn.com.thinkwatch.ihass2.ui.automation.action.ActionDelayActivity
import cn.com.thinkwatch.ihass2.ui.automation.action.ActionFireEventActivity
import cn.com.thinkwatch.ihass2.ui.automation.action.ActionServiceActivity
import cn.com.thinkwatch.ihass2.ui.automation.action.ActionWaitActivity
import cn.com.thinkwatch.ihass2.ui.automation.condition.*
import cn.com.thinkwatch.ihass2.ui.automation.trigger.*
import cn.com.thinkwatch.ihass2.utils.Gsons
import cn.com.thinkwatch.ihass2.utils.SimpleItemTouchHelperCallback
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.common.sketch.Dialogs
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.dylan.uiparts.recyclerview.SwipeItemLayout
import com.google.gson.*
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_hass_automation_edit.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import kotlinx.android.synthetic.main.listitem_automation_edit_item.view.*
import kotlinx.android.synthetic.main.listitem_textview.view.*
import okhttp3.MediaType
import okhttp3.RequestBody
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.layoutInflater
import org.jetbrains.anko.sdk25.coroutines.onClick
import retrofit2.HttpException
import java.lang.reflect.Type

class AutomationEditActivity : BaseActivity() {

    private var automationId: String = ""
    private var entityId: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithLoadable(R.layout.activity_hass_automation_edit)
        setTitle("自动化编辑", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        entityId = intent.getStringExtra("entityId") ?: ""
        automationId = intent.getStringExtra("automationId") ?: ""
        ui()
        if (automationId.isNotBlank()) {
            data()
        } else {
            loadable?.dismissLoading()
            load(Automation())
        }
    }
    override fun doRight() {
        val id = if (this.automationId.isBlank()) System.currentTimeMillis().toString() else this.automationId
        val name = act.name.text()
        if (name.isBlank()) return showError("请输入自动化名称")
        val automation = Automation(id, name, triggers, conditions, actions)
        val json = gsonBuilder.toJson(automation)
        val waiting = Dialogs.showWait(ctx)
        val body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json)
        BaseApi.jsonApi(cfg.haHostUrl, HttpRestApi::class.java)
                .setAutomation(cfg.haPassword, cfg.haToken, id, body)
                .withNext {
                    waiting.dismiss()
                    if (it.result?.equals("ok") ?: false) {
                        finish()
                    } else {
                        showError(it.message ?: "保存自动化失败！")
                    }
                }
                .error {
                    it.printStackTrace()
                    if (it is HttpException) {
                        val json = it.response().errorBody()?.string()
                        val result = Gsons.gson.fromJson<AutomationResponse>(json, AutomationResponse::class.java)
                        showError(result?.message ?: "保存自动化失败！")
                    } else {
                        showError(it.message ?: "保存自动化失败！")
                    }
                    waiting.dismiss()
                }
                .subscribeOnMain {
                    if (disposable == null) disposable = CompositeDisposable(it)
                    else disposable?.add(it)
                }
    }

    private var automation : Automation? = null
    private var triggers = mutableListOf<Trigger>()
    private lateinit var triggersAdapter: RecyclerAdapter<Trigger>
    private lateinit var triggerTouchHelper: ItemTouchHelper
    private var conditions = mutableListOf<Condition>()
    private lateinit var conditionsAdatper: RecyclerAdapter<Condition>
    private lateinit var conditisetOnTouchListenerHelper: ItemTouchHelper
    private var actions = mutableListOf<Action>()
    private lateinit var actionsAdatper: RecyclerAdapter<Action>
    private lateinit var actisetOnTouchListenerHelper: ItemTouchHelper
    private var focusTrigger: Int? = null
    private var focusCondition: Int? = null
    private var focusAction: Int? = null
    private fun ui() {
        this.triggersAdapter = RecyclerAdapter(triggers) { view, index, item, holder ->
            view.name.text = item.desc()
            MDIFont.get().setIcon(view.icon, item.icon())
            view.order.setOnTouchListener { v, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) triggerTouchHelper.startDrag(holder)
                false
            }
            view.insert.onClick {
                addTrigger(index)
            }
            view.delete.onClick {
                triggers.remove(item)
                triggersAdapter.notifyDataSetChanged()
            }
            view.content.onClick {
                when (item.platform) {
                    TriggerPlatform.event-> TriggerEventActivity::class.java
                    TriggerPlatform.homeassistant-> TriggerHassActivity::class.java
                    TriggerPlatform.mqtt-> TriggerMqttActivity::class.java
                    TriggerPlatform.numeric_state-> TriggerNumbericActivity::class.java
                    TriggerPlatform.state-> TriggerStateActivity::class.java
                    TriggerPlatform.sun-> TriggerSunActivity::class.java
                    TriggerPlatform.template-> TriggerTemplateActivity::class.java
                    TriggerPlatform.time-> TriggerTimeActivity::class.java
                    TriggerPlatform.time_pattern-> TriggerPatternActivity::class.java
                    TriggerPlatform.webhook-> TriggerWebHookActivity::class.java
                    TriggerPlatform.zone-> TriggerZoneActivity::class.java
                    else-> null
                }?.let {
                    focusTrigger = index
                    startActivityForResult(Intent(ctx, it).putExtra("trigger", gsonBuilder.toJson(item)), 100)
                }
            }
        }.setOnCreateClicked {
            addTrigger()
        }
        this.triggerTouchHelper = setupRecyclerView(this.triggersView, this.triggersAdapter)

        this.conditionsAdatper = RecyclerAdapter(conditions) { view, index, item, holder ->
            view.name.text = item.desc()
            MDIFont.get().setIcon(view.icon, item.icon())
            view.order.setOnTouchListener { v, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) conditisetOnTouchListenerHelper.startDrag(holder)
                false
            }
            view.insert.onClick {
                addCondition(index)
            }
            view.delete.onClick {
                conditions.remove(item)
                conditionsAdatper.notifyDataSetChanged()
            }
            view.content.onClick {
                when (item.condition) {
                    ConditionType.or-> ConditionOrActivity::class.java
                    ConditionType.and-> ConditionAndActivity::class.java
                    ConditionType.numeric_state-> ConditionNumbericActivity::class.java
                    ConditionType.state-> ConditionStateActivity::class.java
                    ConditionType.sun-> ConditionSunActivity::class.java
                    ConditionType.template-> ConditionTemplateActivity::class.java
                    ConditionType.time-> ConditionTimeActivity::class.java
                    ConditionType.zone-> ConditionZoneActivity::class.java
                    else-> null
                }?.let {
                    focusCondition = index
                    startActivityForResult(Intent(ctx, it).putExtra("condition", gsonBuilder.toJson(item)), 200)
                }
            }
        }.setOnCreateClicked {
            addCondition()
        }
        this.conditisetOnTouchListenerHelper = setupRecyclerView(this.conditionsView, this.conditionsAdatper)

        this.actionsAdatper = RecyclerAdapter(actions) { view, index, item, holder ->
            view.name.text = item.desc()
            MDIFont.get().setIcon(view.icon, item.icon())
            view.order.setOnTouchListener { v, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) actisetOnTouchListenerHelper.startDrag(holder)
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
                            focusAction = index
                            startActivityForResult(Intent(ctx, it).putExtra("condition", gsonBuilder.toJson(item.condition)), 306)
                        }
                        null
                    }
                    else-> null
                }?.let {
                    focusAction = index
                    startActivityForResult(Intent(ctx, it).putExtra("action", gsonBuilder.toJson(item)), 300)
                }
            }
        }.setOnCreateClicked {
            addAction()
        }
        this.actisetOnTouchListenerHelper = setupRecyclerView(this.actionsView, this.actionsAdatper)
    }
    @ActivityResult(requestCode = 100)
    private fun afterTrigger(data: Intent?) {
        val json = data?.getStringExtra("trigger") ?: return
        if (focusTrigger == null) return
        val trigger = Gsons.gson.fromJson<Trigger>(json, Trigger::class.java)
        triggers.set(focusTrigger!!, trigger)
        triggersAdapter.notifyDataSetChanged()
        focusTrigger = null
    }
    private val triggerTypes = mapOf("事件触发" to TriggerEventActivity::class.java,
            "Home Assistant触发" to TriggerHassActivity::class.java,
            "MQTT触发" to TriggerMqttActivity::class.java,
            "数字量触发" to TriggerNumbericActivity::class.java,
            "状态触发" to TriggerStateActivity::class.java,
            "日出日落触发" to TriggerSunActivity::class.java,
            "模板触发" to TriggerTemplateActivity::class.java,
            "时间触发" to TriggerTimeActivity::class.java,
            "时间模板触发" to TriggerPatternActivity::class.java,
            "WebHook触发" to TriggerWebHookActivity::class.java,
            "区域触发" to TriggerZoneActivity::class.java)
    private fun addTrigger(position: Int? = null) {
        focusTrigger = position
        showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                contentView.recyclerView.layoutManager = LinearLayoutManager(act)
                val adaptar = RecyclerAdapter(R.layout.listitem_textview, triggerTypes.keys.toList()) {
                    view, index, item ->
                    view.text.text = item
                    view.onClick {
                        startActivityForResult(Intent(ctx, triggerTypes.get(item)), 101)
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
    @ActivityResult(requestCode = 101)
    private fun afterAddTrigger(data: Intent?) {
        val json = data?.getStringExtra("trigger") ?: return
        val trigger = Gsons.gson.fromJson<Trigger>(json, Trigger::class.java)
        if (focusTrigger != null) triggers.add(focusTrigger!!, trigger)
        else triggers.add(trigger)
        triggersAdapter.notifyDataSetChanged()
    }

    @ActivityResult(requestCode = 200)
    private fun afterCondition(data: Intent?) {
        val json = data?.getStringExtra("condition") ?: return
        if (focusCondition == null) return
        val condition = Gsons.gson.fromJson<Condition>(json, Condition::class.java)
        conditions.set(focusCondition!!, condition)
        conditionsAdatper.notifyDataSetChanged()
        focusCondition = null
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
        focusCondition = position
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
        if (focusCondition != null) conditions.add(focusCondition!!, condition)
        else conditions.add(condition)
        conditionsAdatper.notifyDataSetChanged()
    }

    @ActivityResult(requestCode = 300)
    private fun afterAction(data: Intent?) {
        val json = data?.getStringExtra("action") ?: return
        if (focusAction == null) return
        val action = Gsons.gson.fromJson<Action>(json, Action::class.java)
        actions.set(focusAction!!, action)
        actionsAdatper.notifyDataSetChanged()
        focusAction = null
    }
    @ActivityResult(requestCode = 306)
    private fun afterConditionAction(data: Intent?) {
        val json = data?.getStringExtra("condition") ?: return
        if (focusAction == null) return
        val condition = Gsons.gson.fromJson<Condition>(json, Condition::class.java)
        actions.set(focusAction!!, ConditionAction(condition))
        actionsAdatper.notifyDataSetChanged()
        focusAction = null
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
        focusAction = position
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
    @ActivityResult(requestCode = 301)
    private fun afterAddAction(data: Intent?) {
        val json = data?.getStringExtra("action") ?: return
        val action = Gsons.gson.fromJson<Action>(json, Action::class.java)
        if (focusAction != null) actions.add(focusAction!!, action)
        else actions.add(action)
        actionsAdatper.notifyDataSetChanged()
    }
    @ActivityResult(requestCode = 305)
    private fun afterAddConditionAction(data: Intent?) {
        val json = data?.getStringExtra("condition") ?: return
        val condition = Gsons.gson.fromJson<Condition>(json, Condition::class.java)
        if (focusAction != null) actions.add(focusAction!!, ConditionAction(condition))
        else actions.add(ConditionAction(condition))
        actionsAdatper.notifyDataSetChanged()
    }

    private fun setupRecyclerView(recyclerView: RecyclerView, adapter: RecyclerAdapter<*>): ItemTouchHelper {
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.addOnItemTouchListener(SwipeItemLayout.OnSwipeItemTouchListener(this))
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(RecyclerViewDivider()
                .setColor(0xfff2f2f2.toInt())
                .setMarginStart(dip2px(10f))
                .setSize(dip2px(1f)))
        val callback = SimpleItemTouchHelperCallback(adapter, false)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(recyclerView)
        return touchHelper
    }
    private fun load(it: Automation) {
        automation = it
        triggers.clear()
        triggers.addAll(it.trigger)
        conditions.clear()
        conditions.addAll(it.condition)
        actions.clear()
        actions.addAll(it.action)
        triggersAdapter.notifyDataSetChanged()
        conditionsAdatper.notifyDataSetChanged()
        actionsAdatper.notifyDataSetChanged()
        act.contentView.visibility = View.VISIBLE
        act.name.setText(it.alias)
        loadable?.dismissLoading()
    }
    private fun data() {
        BaseApi.jsonApi(cfg.haHostUrl, HttpRestApi::class.java)
                .getAutomation(cfg.haPassword, cfg.haToken, this.automationId)
                .withNext {
                    load(it)
                }
                .error {
                    it.toastex()
                }
                .subscribeOnMain {
                    if (disposable == null) disposable = CompositeDisposable(it)
                    else disposable?.add(it)
                }
    }

    private inner class RecyclerAdapter<T>(val items: MutableList<T>, val init: (View, Int, T, ViewHolder<T>) -> Unit) :
            RecyclerView.Adapter<ViewHolder<T>>(), SimpleItemTouchHelperCallback.ItemTouchHelperAdapter {
        private var onCreateClicked: (()->Unit)? = null
        fun setOnCreateClicked(onCreate: ()->Unit): RecyclerAdapter<T> {
            this.onCreateClicked = onCreate
            return this
        }
        override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
            items.let {
                val panel = it.get(fromPosition)
                it.removeAt(fromPosition)
                it.add(toPosition, panel)
                notifyItemMoved(fromPosition, toPosition)
            }
            return false
        }
        override fun onItemDismiss(position: Int) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<T> {
            val view = parent.context.layoutInflater.inflate(viewType, parent, false)
            return ViewHolder(view, init)
        }
        override fun onBindViewHolder(holder: ViewHolder<T>, position: Int) {
            if (position < items.size) holder.bindForecast(position, items[position])
            else holder.itemView.onClick { onCreateClicked?.invoke() }
        }
        override fun getItemViewType(position: Int): Int {
            if (position < items.size) return R.layout.listitem_automation_edit_item
            else return R.layout.listitem_automation_add_item
        }
        override fun getItemCount() = items.size + 1
    }
    private class ViewHolder<T>(view: View, val init: (View, Int, T, ViewHolder<T>) -> Unit) : RecyclerView.ViewHolder(view) {
        fun bindForecast(index: Int, item: T) {
            with(item) {
                try { init(itemView, index, item, this@ViewHolder) } catch (ex: Exception) { ex.printStackTrace() }
            }
        }
    }

    companion object {
        private data class DelayAction1(
                var delay: String? = null
        ) : Action(ActionType.delay)
        private data class DelayAction2(
                var delay: DelayTime? = null
        ) : Action(ActionType.delay)
        val gsonBuilder: Gson by lazy {
            GsonBuilder()
                    .enableComplexMapKeySerialization()
                    .setVersion(1.0)
                    .registerTypeAdapter(ConditionAction::class.java, object : JsonSerializer<ConditionAction> {
                        override fun serialize(src: ConditionAction?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement? {
                            return context?.serialize(src?.condition)
                        }
                    })
                    .registerTypeAdapter(DelayAction::class.java, object : JsonSerializer<DelayAction> {
                        override fun serialize(src: DelayAction?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement? {
                            return if (src?.delay.isNullOrBlank()) context?.serialize(DelayAction2(src?.delayValue))
                            else context?.serialize(DelayAction1(src?.delay))
                        }
                    })
                    .create()
        }
    }
}

