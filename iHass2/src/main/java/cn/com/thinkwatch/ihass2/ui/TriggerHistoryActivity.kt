package cn.com.thinkwatch.ihass2.ui

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.inputmethod.EditorInfo
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.TriggerHistory
import com.dylan.common.utils.Utility
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.kdateTime
import com.yunsean.dynkotlins.extensions.onChanged
import com.yunsean.dynkotlins.extensions.text
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.activity_hass_trigger_history.*
import kotlinx.android.synthetic.main.listitem_trigger_history.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.imageResource


class TriggerHistoryActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_trigger_history)
        setTitle("场景执行历史", true)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        this.keyword.setText(intent.getStringExtra("keyword"))
        val notifyId = intent.getIntExtra("notifyId", 0)
        if (notifyId != 0) (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notifyId)
        ui()
        query()
    }

    private var filterRunnable: Runnable = object: Runnable {
        override fun run() {
            pageIndex = 0
            query()
        }
    }
    private var histories = mutableListOf<TriggerHistory>()
    private var adapter: RecyclerAdapter<TriggerHistory>? = null
    private fun ui() {
        this.adapter = RecyclerAdapter(R.layout.listitem_trigger_history, histories) {
            view, index, item ->
            view.type.imageResource = item.type.iconResId
            view.name.text = item.name
            view.serviceId.text = item.serviceId
            view.time.text = item.triggerTime.kdateTime()
        }
        this.recyclerView.adapter = adapter
        this.recyclerView.layoutManager = LinearLayoutManager(this)
        this.recyclerView.addItemDecoration(RecyclerViewDivider()
                .setSize(1)
                .setColor(0xffeeeeee.toInt()))
        this.pullable.setOnRefreshListener {
            pageIndex = 0
            query()
        }
        this.pullable.setOnLoadMoreListener {
            query()
        }

        this.keyword.onChanged {
            act.keyword.removeCallbacks(filterRunnable)
            act.keyword.postDelayed(filterRunnable, 1000)
        }
        this.keyword.setOnEditorActionListener() { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                act.keyword.removeCallbacks(filterRunnable)
                Utility.hideSoftKeyboard(act)
                pageIndex = 0
                query()
            }
            false
        }
    }
    private var pageIndex = 0
    private fun query() {
        val keyword = act.keyword.text()
        if (pageIndex == 0) histories.clear()
        val result = db.getTriggerHistories(keyword, pageIndex)
        histories.addAll(result)
        this.adapter?.notifyDataSetChanged()
        this.pullable?.isLoadMoreEnabled = result.size > 0
        this.pullable?.isRefreshing = false
        this.pullable?.isLoadingMore = false
    }
}

