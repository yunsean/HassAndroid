package cn.com.thinkwatch.ihass2.ui

import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.ObservedChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.enums.ConditionType
import cn.com.thinkwatch.ihass2.model.Observed
import com.dylan.common.rx.RxBus2
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.dylan.uiparts.recyclerview.SwipeItemLayout
import com.yunsean.dynkotlins.extensions.activity
import com.yunsean.dynkotlins.extensions.dip2px
import com.yunsean.dynkotlins.extensions.start
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.activity_hass_observed.*
import kotlinx.android.synthetic.main.listitem_hass_observed.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.backgroundResource
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick


class ObservedActivity : BaseActivity() {

    private var observedChanged = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_observed)
        setTitle("状态通知", true, "新建")

        ui()
        adapter.items = db.getObserved(false)
    }
    override fun doRight() {
        activity(ObservedEditActivity::class.java, 101)
    }
    override fun onDestroy() {
        if (observedChanged) RxBus2.getDefault().post(ObservedChanged())
        super.onDestroy()
    }

    private var observeds = mutableListOf<Observed>()
    private lateinit var adapter: RecyclerAdapter<Observed>
    private fun ui() {
        this.adapter = RecyclerAdapter(R.layout.listitem_hass_observed, observeds) {
            view, index, item ->
            view.name.text = item.name
            view.entity.text = item.entityName
            view.where.text = item.condition.desc
            view.value.visibility = if (item.condition == ConditionType.any) View.GONE
                else if ((item.condition == ConditionType.inc || item.condition == ConditionType.dec) && item.state.isNullOrBlank()) View.GONE
                else View.VISIBLE
            view.value.text = item.state
            view.disable.text = if (item.disabled) "启用" else "禁用"
            if (item.disabled) view.content.backgroundColor = 0xffeeeeee.toInt()
            else view.content.backgroundResource = R.drawable.selector_ffffff_eeeeee
            view.content.onClick {
                if (item.disabled) return@onClick
                Intent(act, ObservedEditActivity::class.java)
                        .putExtra("obervableId", item.id)
                        .start(act, 101)
            }
            view.delete.onClick {
                observedChanged = true
                db.deleteObserved(item.id)
                adapter.items = db.getObserved(false)
            }
            view.disable.onClick {
                observedChanged = true
                item.disabled = !item.disabled
                db.saveObserved(item)
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
        observedChanged = true
        adapter.items = db.getObserved(false)
    }
}

