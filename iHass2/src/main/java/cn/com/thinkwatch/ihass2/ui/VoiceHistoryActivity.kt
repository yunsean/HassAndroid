package cn.com.thinkwatch.ihass2.ui

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.VoiceHistory
import com.yunsean.dynkotlins.extensions.kdateTime
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.activity_hass_voice_history.*
import kotlinx.android.synthetic.main.listitem_voice_history.view.*


class VoiceHistoryActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_voice_history)
        setTitle("语音控制历史", true)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)
        ui()
        query()
    }

    private var histories = mutableListOf<VoiceHistory>()
    private var adapter: RecyclerAdapter<VoiceHistory>? = null
    private fun ui() {
        this.adapter = RecyclerAdapter(R.layout.listitem_voice_history, histories) {
            view, index, item ->
            view.shellPanel.visibility = if (item.speech) View.VISIBLE else View.GONE
            view.selfPanel.visibility = if (item.speech) View.GONE else View.VISIBLE
            view.time.visibility = if (item.time != null) View.VISIBLE else View.GONE
            view.time.text = item.time?.kdateTime("yyyy-MM-dd HH:mm")
            (if (item.speech) view.shellContent else view.selfContent).text = item.content
        }
        this.recyclerView.adapter = adapter
        val layoutManager = LinearLayoutManager(this)
        layoutManager.reverseLayout = true
        this.recyclerView.layoutManager = layoutManager
        this.pullable.setOnRefreshListener {
            pageIndex++
            query()
        }
        this.pullable.setOnLoadMoreListener {
            pageIndex = 0
            query()
        }
        this.pullable.isLoadMoreEnabled = true
    }
    private var pageIndex = 0
    private fun query() {
        if (pageIndex == 0) histories.clear()
        val result = db.listVoiceHistory(pageIndex)
        histories.addAll(result)
        this.adapter?.notifyDataSetChanged()
        this.pullable?.isRefreshEnabled = result.size > 0
        this.pullable?.isRefreshing = false
        this.pullable?.isLoadingMore = false
    }
}

