package cn.com.thinkwatch.ihass2.fragment.album

import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.bus.album.AlbumChanged
import cn.com.thinkwatch.ihass2.bus.UploadChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.enums.AlbumSyncStatus
import cn.com.thinkwatch.ihass2.model.album.AlbumLocalItem
import cn.com.thinkwatch.ihass2.model.album.AlbumMediaType
import cn.com.thinkwatch.ihass2.ui.AlbumImageViewActivity
import cn.com.thinkwatch.ihass2.ui.AlbumVideoViewActivity
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.common.rx.RxBus2
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.dip2px
import com.yunsean.dynkotlins.extensions.kdateTime
import com.yunsean.dynkotlins.extensions.withNext
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_album_items.*
import kotlinx.android.synthetic.main.listitem_album_sync_item.view.*
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.textColor
import java.io.File


class AlbumItemsFragment : BaseFragment() {

    private var listType = AlbumListType_Working
    private val status = mutableListOf<AlbumSyncStatus>()
    override val layoutResId: Int = R.layout.fragment_album_items
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        this.listType = arguments?.getInt("listType", AlbumListType_Working) ?: AlbumListType_Working
        when (this.listType) {
            AlbumListType_Working -> {
                status.addAll(listOf(AlbumSyncStatus.waiting, AlbumSyncStatus.working))
                this.retry.visibility = View.GONE
                this.clean.visibility = View.GONE
            }
            AlbumListType_Succeed -> {
                status.addAll(listOf(AlbumSyncStatus.succeed, AlbumSyncStatus.download))
                this.retry.visibility = View.GONE
                this.delete.visibility = View.GONE
            }
            AlbumListType_Failed -> {
                status.add(AlbumSyncStatus.failed)
                this.delete.visibility = View.GONE
                this.clean.visibility = View.GONE
            }
        }
        ui()
        disposable = RxBus2.getDefault().register(UploadChanged::class.java, {
            pullable?.isRefreshing = true
        }, RxBus2.getDefault().register(AlbumChanged::class.java, {
            if (listType != it.listType) pullable?.isRefreshing = true
        }, disposable))
    }
    override fun onResume() {
        super.onResume()
        adapter?.notifyDataSetChanged()
        showAction()
    }
    override fun onDestroyView() {
        dataDisposable?.dispose()
        disposable?.clear()
        super.onDestroyView()
    }

    private var pageIndex = 0
    private val items = mutableListOf<AlbumLocalItem>()
    private var adapter: RecyclerAdapter<AlbumLocalItem>? = null
    private fun ui() {
        this.checkAll.setOnClickListener {
            val check = !checkAll.isSelected
            items.forEach { if (it.type != AlbumMediaType.folder) it.checked = check }
            adapter?.notifyDataSetChanged()
            showAction()
        }
        this.delete.setOnClickListener {
            db.deleteAlbumItems(items.filter { it.checked }.map { it.id })
            items.removeAll { it.checked }
            adapter?.notifyDataSetChanged()
            RxBus2.getDefault().post(AlbumChanged(listType))
        }
        this.retry.setOnClickListener {
            db.resetAlbumFailedItems(items.filter { it.checked }.map { it.id })
            items.removeAll { it.checked }
            adapter?.notifyDataSetChanged()
            RxBus2.getDefault().post(AlbumChanged(listType))
        }
        this.clean.setOnClickListener {
            db.cleanAlbumSucceed()?.let { cfg.set(HassConfig.Album_EarlyTime, it) }
            pullable?.isRefreshing = true
        }
        this.checkAll.visibility = if (this.listType != AlbumListType_Succeed) View.VISIBLE else View.GONE
        this.adapter = RecyclerAdapter(R.layout.listitem_album_sync_item, items) { view, index, item ->
            view.name.text = item.name
            view.icon.text = item.type.text
            view.icon.textColor = item.type.color
            view.size.text = formatSize(item.size)
            view.status.text = if (item.status == AlbumSyncStatus.failed) item.reason else if (item.status == AlbumSyncStatus.succeed) "${item.time.kdateTime()} 上传" else "${item.mtime.kdateTime()} 修改"
            view.checked.visibility = if (this.listType != AlbumListType_Succeed) View.VISIBLE else View.GONE
            view.checked.isEnabled = item.status != AlbumSyncStatus.working
            view.checked.isSelected = item.checked
            view.type.visibility = if (this.listType == AlbumListType_Succeed) View.VISIBLE else View.GONE
            view.type.imageResource = if (item.status == AlbumSyncStatus.download) R.drawable.album_type_download else R.drawable.album_type_upload
            view.checked.setOnClickListener {
                item.checked = !item.checked
                adapter?.notifyDataSetChanged()
                showAction()
            }
            view.setOnClickListener {
                when (item.type) {
                    AlbumMediaType.image-> {
                        AlbumImageViewActivity.imageList = items.filter { it.type == AlbumMediaType.image }
                        startActivity(Intent(ctx, AlbumImageViewActivity::class.java)
                                .putExtra("isLocal", true)
                                .putExtra("showChecked", listType != AlbumListType_Succeed)
                                .putExtra("imageIndex", AlbumImageViewActivity.imageList?.indexOf(item) ?: 0))
                    }
                    AlbumMediaType.movie-> {
                        startActivity(Intent(ctx, AlbumVideoViewActivity::class.java).putExtra("url", item.path))
                    }
                }
            }
        }
        this.recyclerView.adapter = adapter
        this.recyclerView.layoutManager = LinearLayoutManager(ctx)
        this.recyclerView.addItemDecoration(RecyclerViewDivider()
                .setColor(0xfff2f2f2.toInt())
                .setSize(ctx.dip2px(1f)))
        this.pullable.setOnRefreshListener { pageIndex = 0; data() }
        this.pullable.setOnLoadMoreListener { data() }
        this.pullable.postDelayed({ data() }, 500)
        showAction()
    }

    private var dataDisposable: Disposable? = null
    private fun data() {
        val pageIndex = this.pageIndex++
        dataDisposable?.dispose()
        db.async {
            db.getAlbumItems(pageIndex, status, listType == AlbumListType_Succeed)
                    .apply {
                        forEach {
                            val ext = File(it.name).extension
                            if (AlbumRemoteFragment.ImageExtensions.contains(ext)) it.type = AlbumMediaType.image
                            else if (AlbumRemoteFragment.MovieExtensions.contains(ext)) it.type = AlbumMediaType.movie
                        }
                    }
        }.withNext {
            if (pageIndex == 0) {
                count.text = "共${db.getAlbumItemsCount(status)}个项目！"
                items.clear()
            }
            items.addAll(it)
            adapter?.notifyDataSetChanged()
            pullable.isRefreshing = false
            pullable.isLoadingMore = false
            pullable.isLoadMoreEnabled = it.isNotEmpty()
            showAction()
        }.subscribeOnMain {
            dataDisposable = it
        }
    }
    private fun showAction() {
        val hasChecked = adapter?.items?.find { it.checked } != null
        this.actions.visibility = if (hasChecked) View.VISIBLE else View.GONE
        val hasUnchecked = adapter?.items?.find { !it.checked } != null
        this.checkAll.isSelected = !hasUnchecked && hasChecked
    }

    companion object {
        const val AlbumListType_Working = 0
        const val AlbumListType_Succeed = 1
        const val AlbumListType_Failed = 2
        fun newInstance(listType: Int): AlbumItemsFragment {
            val fragment = AlbumItemsFragment()
            val args = Bundle()
            args.putInt("listType", listType)
            fragment.arguments = args
            return fragment
        }

        fun formatSize(size: Long): String {
            if (size > 10_000_000_000) return "${size / 1_000_000_000} GB"
            else if (size > 500_000_000) return String.format("%.1f GB", size / 1_000_000_000F)
            else if (size > 10_000_000) return "${size / 1_000_000} MB"
            else if (size > 500_000) return String.format("%.1f MB", size / 1_000_000F)
            else if (size > 10_000) return "${size / 1_000} KB"
            else if (size > 500) return String.format("%.1f KB", size / 1_000F)
            else return "$size Bytes"
        }
    }
}