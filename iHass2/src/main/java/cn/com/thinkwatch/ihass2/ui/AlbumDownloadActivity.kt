package cn.com.thinkwatch.ihass2.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.aidl.IAlbumSyncCallback
import cn.com.thinkwatch.ihass2.aidl.IAlbumSyncService
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.fragment.album.AlbumItemsFragment
import cn.com.thinkwatch.ihass2.fragment.album.AlbumRemoteFragment
import cn.com.thinkwatch.ihass2.model.album.AlbumDownloadItem
import cn.com.thinkwatch.ihass2.service.AlbumSyncService
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.dip2px
import com.yunsean.dynkotlins.extensions.withNext
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_album_download.*
import kotlinx.android.synthetic.main.listitem_album_download_item.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.imageResource
import java.io.File


class AlbumDownloadActivity : BaseActivity() {

    private var downloadPaused = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album_download)
        setTitle("相册下载", true)

        ui()
        data()
        showAction()

        val intent = Intent(act, AlbumSyncService::class.java)
        act.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    override fun onResume() {
        super.onResume()
        adapter?.notifyDataSetChanged()
        showAction()
    }
    override fun onDestroy() {
        act.unbindService(serviceConnection)
        super.onDestroy()
    }

    private var pageIndex = 0
    private val items = mutableListOf<AlbumDownloadItem>()
    private var adapter: RecyclerAdapter<AlbumDownloadItem>? = null
    private var percent: Int = 0
    private fun ui() {
        this.checkAll.setOnClickListener {
            val check = !checkAll.isSelected
            items.forEach { it.checked = check }
            adapter?.notifyDataSetChanged()
            showAction()
        }
        this.delete.setOnClickListener {
            db.deleteDownloadItems(items.filter { it.checked }.map { it.id })
            items.removeAll { it.checked }
            adapter?.notifyDataSetChanged()
        }
        this.retry.setOnClickListener {
            db.resetDownloadItems(items.filter { it.checked }.map { it.id })
            items.forEach { if (it.checked) { it.failed = false; it.downloading = false } }
            adapter?.notifyDataSetChanged()
            syncService?.resumeDownload()
        }
        this.toggle.setOnClickListener {
            downloadPaused = !downloadPaused
            act.toggle.isSelected = downloadPaused
            if (downloadPaused) syncService?.pauseDownload()
            else syncService?.resumeDownload()
        }
        this.clean.setOnClickListener {
            db.cleanDownloadItems()
            pullable?.isRefreshing = true
        }
        this.adapter = RecyclerAdapter(R.layout.listitem_album_download_item, items) { view, index, item ->
            view.name.text = item.name
            view.icon.imageResource = if (item.failed) R.drawable.album_status_error else if (item.downloading) R.drawable.album_status_downloading else R.drawable.album_status_waiting
            view.size.text = AlbumItemsFragment.formatSize(item.size)
            view.status.text = if (item.failed) (if (item.reason.isNullOrBlank()) "错误" else item.reason) else if (item.downloading) "$percent%" else "等待"
            view.checked.isSelected = item.checked
            view.checked.setOnClickListener {
                item.checked = !item.checked
                adapter?.notifyDataSetChanged()
                showAction()
            }
            view.locate.setOnClickListener {

            }
            view.setOnClickListener {
                val ext = File(item.name).extension
                if (AlbumRemoteFragment.ImageExtensions.contains(ext)) {
                    AlbumImageViewActivity.imageList = listOf(item)
                    startActivity(Intent(act, AlbumImageViewActivity::class.java)
                            .putExtra("imageIndex", 0)
                            .putExtra("userStub", item.userStub)
                            .putExtra("path", ""))
                } else if (AlbumRemoteFragment.MovieExtensions.contains(ext)) {
                    val url = "${cfg.haHostUrl}/api/alumb/preview?user=${item.userStub}&path=${item.path}"
                    startActivity(Intent(act, AlbumVideoViewActivity::class.java).putExtra("url", url))
                }
            }
        }
        this.recyclerView.adapter = adapter
        this.recyclerView.layoutManager = LinearLayoutManager(act)
        this.recyclerView.addItemDecoration(RecyclerViewDivider()
                .setColor(0xfff2f2f2.toInt())
                .setSize(act.dip2px(1f)))
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
            db.getDownloadItems(pageIndex)
        }.withNext {
            if (pageIndex == 0) {
                count.text = "共${db.getDownloadItemsCount()}个项目！"
                items.clear()
            }
            val downloadingId = syncService?.downloadingId() ?: 0L
            it.forEach { if (it.id == downloadingId) it.downloading = true }
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

    private var syncService: IAlbumSyncService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            syncService = IAlbumSyncService.Stub.asInterface(service)
            syncService?.registerCallback(syncCallback)
            downloadPaused = syncService?.isDownloadPaused() ?: true
            act.toggle.isSelected = downloadPaused
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            syncService = null
        }
    }
    private val syncCallback = object: IAlbumSyncCallback.Stub() {
        override fun onUploadChanged() = Unit
        override fun onActionProgress(percent: Int, message: String?) = Unit
        override fun onActionCompleted() = Unit
        override fun onFileDownloaded(id: Long) {
            val item = items.find { it.id == id } ?: return
            item.downloading = false
            val dbItem = db.getDownloadItem(id)
            if (dbItem == null) {
                items.remove(item)
            } else {
                item.failed = dbItem.failed
                item.reason = dbItem.reason
            }
            runOnUiThread { adapter?.notifyDataSetChanged() }
        }
        override fun onFileDownloading(id: Long, percent: Int) {
            items.forEach { it.downloading = false }
            val item = items.find { it.id == id } ?: return
            item.downloading = true
            this@AlbumDownloadActivity.percent = percent
            runOnUiThread { adapter?.notifyDataSetChanged() }
        }
        override fun onFileDownloadPaused(paused: Boolean) {
            downloadPaused = paused
            act.toggle.isSelected = downloadPaused
        }
    }
}

