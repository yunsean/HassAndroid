package cn.com.thinkwatch.ihass2.fragment.xmly

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.bus.broadcast.FavoriteChanged
import cn.com.thinkwatch.ihass2.bus.broadcast.XmlyFilterChange
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.dto.xmly.Channel
import cn.com.thinkwatch.ihass2.model.broadcast.Cached
import cn.com.thinkwatch.ihass2.model.broadcast.Favorite
import cn.com.thinkwatch.ihass2.network.external.xmlyApi
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.dylan.common.rx.RxBus2
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.readPref
import com.yunsean.dynkotlins.extensions.toastex
import com.yunsean.dynkotlins.extensions.withNext
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.listitem_hass_broadcast_channel.view.*
import kotlinx.android.synthetic.main.pager_hass_broadcast.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx


class ChannelFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.pager_hass_broadcast
    private var showType: Int = R.id.local
    private var filterValue: Int = 0
    private var favoritedChannels = listOf<Int>()
    private var dataDisposable: Disposable? = null
    private var entityId: String = ""
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showType = arguments?.getInt("type", R.id.local) ?: R.id.local
        entityId = arguments?.getString("entityId") ?: ""
        if (showType == R.id.local) filterValue = ctx.readPref("xmly.provinceCode", "110000", "DEFAULT")?.toIntOrNull() ?: 110000
        else if (showType == R.id.category) filterValue = ctx.readPref("xmly.categoryCode", "14", "DEFAULT")?.toIntOrNull() ?: 14
        disposable = RxBus2.getDefault().register(XmlyFilterChange::class.java, {
            if (showType == R.id.local) {
                filterValue = ctx.readPref("xmly.provinceCode", "110000", "DEFAULT")?.toIntOrNull() ?: 110000
                pageIndex = 0
                data()
            }
            else if (showType == R.id.category) {
                filterValue = ctx.readPref("xmly.categoryCode", "14", "DEFAULT")?.toIntOrNull() ?: 14
                pageIndex = 0
                data()
            }
        }, RxBus2.getDefault().register(FavoriteChanged::class.java, {
            favoritedChannels = db.getXmlyFavorite(entityId, Favorite.Type_Xmly).map { it.id }
            adapter?.notifyDataSetChanged()
        }, disposable))

        ui()
        favoritedChannels = db.getXmlyFavorite(entityId, Favorite.Type_Xmly).map { it.id }
        pullable.postDelayed({ pullable?.isRefreshing = true }, 500)
    }
    override fun onDestroy() {
        dataDisposable?.dispose()
        super.onDestroy()
    }

    private var pageIndex = 0
    private val channels = mutableListOf<Channel>()
    private var adapter: RecyclerAdapter<Channel>? = null
    private fun ui() {
        adapter = RecyclerAdapter(R.layout.listitem_hass_broadcast_channel, channels) {
            view, index, channel ->
            view.channelName.text = channel.name
            view.channelProgram.text = "正在直播  ${channel.programName}"
            view.channelHit.text = formatCount(channel.playCount)
            view.channelFavor.isChecked = favoritedChannels.contains(channel.id)
            Glide.with(ctx).load(channel.coverLarge)
                    .apply(RequestOptions().placeholder(R.drawable.radio_channel_icon))
                    .into(view.channelIcon)
            view.onClick {
                db.addXmlyCached(Cached(channel.id, channel.name, channel.coverLarge, channel.playUrl.aac64, channel.playCount))
                if (entityId.startsWith("media_player.")) RxBus2.getDefault().post(ServiceRequest("media_player", "play_media", entityId, mediaContentId = channel.playUrl.aac64, mediaContentType = "music"))
                else RxBus2.getDefault().post(ServiceRequest("broadcast", "play", entityId, url = channel.playUrl.aac64))
                act?.finish()
            }
            view.channelFavor.onClick {
                if (view.channelFavor.isChecked) db.addXmlyFavorite(Favorite(channel.id, Favorite.Type_Xmly, entityId, channel.name, channel.coverLarge, channel.playUrl.aac64, channel.playCount))
                else db.delXmlyFavorite(channel.id)
                db.addXmlyCached(Cached(channel.id, channel.name, channel.coverLarge, channel.playUrl.aac64, channel.playCount))
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
        if (recyclerView == null) return
        val pageIndex = ++this.pageIndex
        val query = if (showType == R.id.local) xmlyApi.getProvinceChannel(filterValue, pageIndex)
        else if (showType == R.id.category) xmlyApi.getCategoryChannel(filterValue, pageIndex)
        else if (showType == R.id.country) xmlyApi.getNationalChannel(pageIndex)
        else xmlyApi.getNetworkChannel(pageIndex)
        query.withNext {
            if (pageIndex == 1) channels.clear()
            it.data?.let {
                channels.addAll(it.data)
                recyclerView.adapter?.notifyDataSetChanged()
                pullable.isLoadMoreEnabled = (it.totalSize + it.pageSize - 1) / it.pageSize > it.page
            }
            pullable.isRefreshing = false
            pullable.isLoadingMore = false
        }.error {
            it.toastex()
            pullable.isRefreshing = false
            pullable.isLoadingMore = false
        }.subscribeOnMain {
            dataDisposable = it
        }
    }
    private fun formatCount(value: Int): String {
        if (value > 1_0000_0000) return String.format("%.2f亿人", value / 1_0000_0000f)
        else if (value > 1_0000) return String.format("%.2f万人", value / 1_0000f)
        else return String.format("%d人", value)
    }
}