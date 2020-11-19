package cn.com.thinkwatch.ihass2.ui

import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.TriggerChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.EventTrigger
import com.dylan.common.rx.RxBus2
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.dylan.uiparts.recyclerview.SwipeItemLayout
import com.yunsean.dynkotlins.extensions.dip2px
import com.yunsean.dynkotlins.extensions.start
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.activity_hass_trigger.*
import kotlinx.android.synthetic.main.listitem_trigger.view.*
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk25.coroutines.onClick


class TriggerActivity : BaseActivity() {

    private var triggerChanged = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_trigger)
        setTitle("编辑触发器", true, "新建")

        ui()
        adapter.items = db.getTriggers(false)
    }
    override fun doRight() {
        Intent(this, TriggerEditActivity::class.java)
                .start(this)
    }
    override fun onDestroy() {
        if (triggerChanged) RxBus2.getDefault().post(TriggerChanged())
        super.onDestroy()
    }

    private lateinit var adapter: RecyclerAdapter<EventTrigger>
    private fun ui() {
        this.adapter = RecyclerAdapter(R.layout.listitem_trigger, null) {
            view, index, trigger ->
            view.type.imageResource = trigger.type.iconResId
            view.name.text = trigger.name
            view.serviceId.text = trigger.serviceId
            view.disable.text = if (trigger.disabled) "启用" else "禁用"
            if (trigger.disabled) view.content.backgroundColor = 0xffeeeeee.toInt()
            else view.content.backgroundResource = R.drawable.selector_ffffff_eeeeee
            view.content.onClick {
                if (trigger.disabled) return@onClick
                Intent(act, TriggerEditActivity::class.java)
                        .putExtra("triggerId", trigger.id)
                        .start(act, 101)
            }
            view.delete.onClick {
                triggerChanged = true
                db.deleteTrigger(trigger.id)
                adapter.items = db.getTriggers(false)
            }
            view.disable.onClick {
                triggerChanged = true
                trigger.disabled = !trigger.disabled
                db.saveTrigger(trigger)
                adapter.notifyDataSetChanged()
            }
        }
        this.recyclerView.adapter = this.adapter
        this.recyclerView.layoutManager = LinearLayoutManager(ctx)
        this.recyclerView.addOnItemTouchListener(SwipeItemLayout.OnSwipeItemTouchListener(this))
        this.recyclerView.addItemDecoration(RecyclerViewDivider()
                .setColor(0xffeeeeee.toInt())
                .setSize(dip2px(1f)))
    }

    @ActivityResult(requestCode = 101)
    private fun afterChanged(){
        triggerChanged = true
        adapter.items = db.getTriggers(false)
    }
}

