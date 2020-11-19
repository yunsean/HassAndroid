package cn.com.thinkwatch.ihass2.fragment

import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import android.view.inputmethod.EditorInfo
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.bus.EntityChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.ui.AutomationEditActivity
import cn.com.thinkwatch.ihass2.ui.ScriptEditActivity
import cn.com.thinkwatch.ihass2.utils.AddableRecylerAdapter
import cn.com.thinkwatch.ihass2.utils.SimpleItemTouchHelperCallback
import com.dylan.common.rx.RxBus2
import com.dylan.common.utils.Utility
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.dylan.uiparts.recyclerview.SwipeItemLayout
import com.yunsean.dynkotlins.extensions.*
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_hass_script.*
import kotlinx.android.synthetic.main.fragment_hass_script.loading
import kotlinx.android.synthetic.main.fragment_hass_script.view.*
import kotlinx.android.synthetic.main.listitem_script_item.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.sdk25.coroutines.onLongClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx
import java.text.SimpleDateFormat

class ScriptFragment : BaseFragment() {

    var showBack: Boolean = false
    private var dataDisposable: Disposable? = null
    override val layoutResId: Int = R.layout.fragment_hass_script
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment.back.visibility = if (showBack) View.VISIBLE else View.GONE
        ui()
        data()
    }
    override fun onDestroy() {
        dataDisposable?.dispose()
        super.onDestroy()
    }

    private var allEntities: MutableList<JsonEntity>? = null
    private lateinit var adapter: AddableRecylerAdapter<JsonEntity>
    private lateinit var touchHelper: ItemTouchHelper
    private var filterRunnable: Runnable = object: Runnable {
        override fun run() {
            filter()
        }
    }
    private fun ui() {
        this.back.onClick {
            act.finish()
        }
        this.adapter = AddableRecylerAdapter(R.layout.listitem_script_item, null) {
            view, index, item, viewHolder ->
            view.name.setText(item.friendlyName)
            view.entityId.setText(item.entityId)
            view.latestTrigger.setText(try { "最后触发：" + SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZZZZZ").parse(item.attributes?.lastTriggered)?.kdateTime() } catch (_: Exception) { item.attributes?.lastTriggered } ?: "最后触发：无")
            view.start.onClick {
                view.view_mine_left_layout.close()
                RxBus2.getDefault().post(ServiceRequest("homeassistant", "turn_on", item.entityId))
                showInfo("已执行")
            }
            view.contentView.onClick {
                Intent(act, ScriptEditActivity::class.java)
                        .putExtra("entityId", item.entityId)
                        .start(act)
            }
        }
        this.recyclerView.layoutManager = LinearLayoutManager(act)
        this.recyclerView.addOnItemTouchListener(SwipeItemLayout.OnSwipeItemTouchListener(ctx))
        this.recyclerView.adapter = this.adapter
        this.recyclerView.addItemDecoration(RecyclerViewDivider()
                .setColor(0xffeeeeee.toInt())
                .setSize(act.dip2px(1f)))
        val callback = SimpleItemTouchHelperCallback(adapter, false)
        this.touchHelper = ItemTouchHelper(callback)
        this.touchHelper.attachToRecyclerView(recyclerView)
        this.pullable.setOnRefreshListener {
            data()
            pullable.isRefreshing = false
        }
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
        this.add.onClick {
            Intent(act, ScriptEditActivity::class.java)
                    .start(act)
        }
    }

    private fun filter() {
        val keyword = act.keyword.text()
        adapter.items = if (keyword.isNullOrBlank()) allEntities?.toMutableList()
        else allEntities?.filter { it.entityId.contains(keyword, true) or it.friendlyName.contains(keyword, true) or (it.state?.contains(keyword) ?: false) }?.toMutableList()
        adapter.notifyDataSetChanged()
    }
    private fun data() {
        this.loading.visibility = View.VISIBLE
        this.adapter.items = null
        adapter.notifyDataSetChanged()
        db.async {
            db.listEntity("script.%").sortedBy { it.friendlyName }
        }.withNext {
            allEntities = it.toMutableList()
            this.loading.visibility = View.GONE
            filter()
        }.subscribeOnMain {
            dataDisposable = it
        }
    }
}