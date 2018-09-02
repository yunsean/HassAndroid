package cn.com.thinkwatch.ihass2.fragment.qtfm

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.bus.broadcast.FavoriteChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.broadcast.Favorite
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.dylan.common.rx.RxBus2
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.nextOnMain
import com.yunsean.dynkotlins.extensions.toastex
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.listitem_hass_broadcast_channel.view.*
import kotlinx.android.synthetic.main.pager_hass_broadcast.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx


class FavoriteFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.pager_hass_broadcast
    private var entityId: String = ""
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        entityId = arguments?.getString("entityId") ?: ""
        disposable = RxBus2.getDefault().register(FavoriteChanged::class.java, {
            pullable?.isRefreshing = true
        }, disposable)

        ui()
        pullable.postDelayed({ pullable?.isRefreshing = true }, 500)
    }
    override fun onDestroy() {
        super.onDestroy()
    }

    private var pageIndex = 0
    private var adapter: RecyclerAdapter<Favorite>? = null
    private fun ui() {
        adapter = RecyclerAdapter(R.layout.listitem_hass_broadcast_channel, null) {
            view, index, channel ->
            view.channelName.text = channel.name
            view.channelProgram.visibility = View.GONE
            view.channelHit.text = formatCount(channel.playCount)
            view.channelFavor.isChecked = true
            Glide.with(ctx).load(channel.coverSmall)
                    .apply(RequestOptions().placeholder(R.drawable.radio_channel_icon))
                    .into(view.channelIcon)
            view.onClick {
                RxBus2.getDefault().post(ServiceRequest("broadcast", "play", entityId, url = "http://lhttp.qingting.fm/live/${channel.id}/64k.mp3"))
            }
            view.channelFavor.onClick {
                db.delXmlyFavorite(channel.id)
                RxBus2.getDefault().post(FavoriteChanged())
            }
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(ctx)
        recyclerView.addItemDecoration(RecyclerViewDivider()
                .setSize(1)
                .setColor(0xffcccccc.toInt()))
        pullable.setOnRefreshListener {
            pageIndex = 0
            data()
        }
        pullable.setOnLoadMoreListener {
            data()
        }
    }
    private fun data() {
        db.async {
            db.getXmlyFavorite(entityId)
        }.nextOnMain {
            adapter?.items = it
            recyclerView.adapter.notifyDataSetChanged()
            pullable.isLoadMoreEnabled = false
            pullable.isRefreshing = false
            pullable.isLoadingMore = false
        }.error {
            it.toastex()
            pullable.isRefreshing = false
            pullable.isLoadingMore = false
        }
    }
    private fun formatCount(value: Int): String {
        if (value > 1_0000_0000) return String.format("%.2f亿人", value / 1_0000_0000f)
        else if (value > 1_0000) return String.format("%.2f万人", value / 1_0000f)
        else return String.format("%d人", value)
    }
}