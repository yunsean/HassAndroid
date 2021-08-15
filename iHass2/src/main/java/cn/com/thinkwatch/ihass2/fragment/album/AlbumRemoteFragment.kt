package cn.com.thinkwatch.ihass2.fragment.album

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.bus.album.AlbumDownloadAdded
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.global.GlobalConfig
import cn.com.thinkwatch.ihass2.model.album.AlbumRemoteItem
import cn.com.thinkwatch.ihass2.model.album.AlbumMediaType
import cn.com.thinkwatch.ihass2.network.BaseApi
import cn.com.thinkwatch.ihass2.network.http.HttpRestApi
import cn.com.thinkwatch.ihass2.ui.AlbumImageViewActivity
import cn.com.thinkwatch.ihass2.ui.AlbumVideoViewActivity
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Dialogs
import com.dylan.uiparts.layout.LoadableLayout
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_hass_chart.*
import kotlinx.android.synthetic.main.fragment_album_remote.*
import kotlinx.android.synthetic.main.fragment_album_remote.view.*
import kotlinx.android.synthetic.main.listitem_album_remote_item.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.textColor
import java.io.File
import java.util.*


class AlbumRemoteFragment : BaseFragment() {

    private lateinit var userStub: String
    private var pathLevel = mutableListOf<String>()
    override val layoutResId: Int = R.layout.fragment_album_remote
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userStub = cfg.getString(HassConfig.Album_UserStub)
        ui()
    }

    override fun viewCreated() {
        ctx.readPref("remotePath")?.split("/")?.let { pathLevel.clear(); pathLevel.addAll(it) }
        fragment.pullable.postDelayed({ pullable.isRefreshing = true }, 500)
    }
    override fun onResume() {
        super.onResume()
        adapter?.notifyDataSetChanged()
        showAction()
    }
    override fun onDestroyView() {
        apiDisposable?.dispose()
        deleteDisposable?.clear()
        super.onDestroyView()
    }
    override fun onBackPressed(): Boolean {
        if (pathLevel.isEmpty()) return false
        pathLevel.removeAt(pathLevel.size - 1)
        data(true)
        return true
    }

    private var deleteDisposable: CompositeDisposable? = null
    private var adapter: RecyclerAdapter<AlbumRemoteItem>? = null
    private val albumItems = mutableListOf<AlbumRemoteItem>()
    private lateinit var layoutManager: LinearLayoutManager
    private fun ui() {
        this.delete.setOnClickListener {
            val waiting = Dialogs.showWait(ctx, "正在删除，请耐心等待...")
            adapter?.items?.filter { it.checked }?.let {
                Observable.fromIterable(it)
                        .flatMap {
                            BaseApi.jsonApi(cfg.haHostUrl, HttpRestApi::class.java)
                                    .albumDeleteFile(cfg.haPassword, cfg.haToken, userStub, currentPathLine(it.name))
                        }
                        .withComplete {
                            waiting.dismiss()
                            pullable.isRefreshing = true
                        }
                        .error {
                            waiting.dismiss()
                            pullable.isRefreshing = true
                        }
                        .subscribeOnMain {
                            if (deleteDisposable == null) deleteDisposable = CompositeDisposable()
                            deleteDisposable?.add(it)
                        }
            }
        }
        this.download.setOnClickListener {
            adapter?.items?.filter { it.checked }?.let {
                if (it.isNotEmpty()) {
                    db.addDownloadItems(it, userStub) { currentPathLine(it) }
                    showInfo("添加到下载任务成功！")
                    RxBus2.getDefault().post(AlbumDownloadAdded())
                }
            }
        }
        this.checkAll.setOnClickListener {
            val check = !checkAll.isSelected
            adapter?.items?.forEach { if (it.type != AlbumMediaType.folder) it.checked = check }
            adapter?.notifyDataSetChanged()
            showAction()
        }
        this.locate.setOnClickListener {
            val index = layoutManager.findFirstVisibleItemPosition()
            val item = if (index >= 0 && index < albumItems.size) albumItems[index] else null
            val calendar = Calendar.getInstance().apply { if (item != null && item.type != AlbumMediaType.folder) timeInMillis = item.mtime * 1000 }
            DatePickerDialog(ctx, DatePickerDialog.THEME_HOLO_LIGHT, { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                val time = calendar.timeInMillis / 1000
                (albumItems.firstOrNull { it.mtime != 0L && it.mtime <= time } ?: albumItems.last()).let {
                    layoutManager.scrollToPositionWithOffset(albumItems.indexOf(it), 0)
                }
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
                setTitle("跳转日期")
                datePicker.maxDate = Calendar.getInstance(TimeZone.getTimeZone("GMT+8")).timeInMillis
                show()
            }
        }
        this.adapter = RecyclerAdapter(R.layout.listitem_album_remote_item, albumItems) {
            view, index, item ->
            view.icon.text = item.type.text
            view.icon.textColor = item.type.color
            view.name.text = item.name
            view.size.text = AlbumItemsFragment.formatSize(item.size)
            view.mtime.text = (item.mtime * 1000).kdateTime("yyyy-MM-dd HH:mm")
            view.actionPanel.visibility = if (item.type == AlbumMediaType.folder) View.GONE else View.VISIBLE
            view.checked.visibility = if (item.type == AlbumMediaType.folder) View.GONE else View.VISIBLE
            view.mtime.visibility = if (item.mtime == 0L) View.GONE else View.VISIBLE
            view.checked.isSelected = item.checked
            view.checked.setOnClickListener {
                item.checked = !item.checked
                adapter?.notifyDataSetChanged()
                showAction()
            }
            view.setOnClickListener {
                if (item.type == AlbumMediaType.folder && item.name == ".." && pathLevel.isNotEmpty()) {
                    pathLevel.removeAt(pathLevel.size - 1)
                    data(true)
                } else if (item.type == AlbumMediaType.folder) {
                    pathLevel.add(item.name)
                    data(true)
                } else if (item.type == AlbumMediaType.image) {
                    AlbumImageViewActivity.imageList = adapter?.items?.filter { it.type == AlbumMediaType.image }
                    startActivity(Intent(ctx, AlbumImageViewActivity::class.java)
                            .putExtra("imageIndex", AlbumImageViewActivity.imageList?.indexOf(item) ?: 0)
                            .putExtra("userStub", userStub)
                            .putExtra("path", currentPathLine()))
                } else if (item.type == AlbumMediaType.movie) {
                    val path = currentPathLine(item.name)
                    val url = "${cfg.haHostUrl}/api/alumb/preview?user=${userStub}&path=${path}"
                    startActivity(Intent(ctx, AlbumVideoViewActivity::class.java).putExtra("url", url))
                }
            }
        }
        this.recyclerView.adapter = adapter
        this.recyclerView.layoutManager = LinearLayoutManager(ctx).also { layoutManager = it }
        this.recyclerView.addItemDecoration(RecyclerViewDivider()
                .setColor(0xfff2f2f2.toInt())
                .setSize(ctx.dip2px(1f)))
        this.pullable.setOnRefreshListener { data() }
        this.pullable.setOnLoadMoreListener { more() }
        this.pullable.isLoadMoreEnabled = false
        showAction()
    }
    private fun showAction() {
        val hasChecked = adapter?.items?.find { it.checked } != null
        this.delete.visibility = if (hasChecked) View.VISIBLE else View.GONE
        this.download.visibility = if (hasChecked) View.VISIBLE else View.GONE
        val hasUnchecked = adapter?.items?.find { it.type != AlbumMediaType.folder && !it.checked } != null
        this.checkAll.isSelected = !hasUnchecked && hasChecked
    }

    private var waiting: Dialog? = null
    private var apiDisposable: Disposable? = null
    private var pageIndex = 0
    private fun data(showWaiting: Boolean = false) {
        this.path.text = currentPathLine()
        waiting?.dismiss()
        waiting = if (showWaiting) Dialogs.showWait(ctx, "正在加载...") else null
        apiDisposable?.dispose()
        pageIndex = 0
        more()
        ctx.savePref("remotePath", currentPathLine())
    }
    private fun more() {
        val pageIndex = this.pageIndex++
        val isRoot = pathLevel.isEmpty()
        BaseApi.jsonApi(cfg.haHostUrl, HttpRestApi::class.java)
                .albumListFile(cfg.haPassword, cfg.haToken, userStub, currentPathLine(), pageIndex)
                .flatMap { r->
                    if (pageIndex == 0) {
                        albumItems.clear()
                        if (!isRoot) albumItems.add(AlbumRemoteItem("..", type = AlbumMediaType.folder))
                    }
                    r.folders?.let {
                        it.forEach { it.type = AlbumMediaType.folder }
                        albumItems.addAll(it)
                    }
                    r.files?.sortedBy { it.name }?.sortedByDescending { it.mtime }?.let {
                        it.forEach {
                            val ext = File(it.name).extension
                            if (ImageExtensions.contains(ext)) it.type = AlbumMediaType.image
                            else if (MovieExtensions.contains(ext)) it.type = AlbumMediaType.movie
                        }
                        albumItems.addAll(it)
                    }
                    Observable.just(r.folders?.isEmpty() ?: true && r.files?.isEmpty() ?: true)
                }
                .withNext {
                    adapter?.notifyDataSetChanged()
                    showAction()
                    pullable.isRefreshing = false
                    pullable.isLoadingMore = false
                    pullable.isLoadMoreEnabled = !it
                    waiting?.dismiss()
                }
                .error {
                    showError(it.localizedMessage ?: "访问服务器错误")
                    pullable.isRefreshing = false
                    pullable.isLoadingMore = false
                    pullable.isLoadMoreEnabled = false
                    waiting?.dismiss()
                }
                .subscribeOnMain {
                    apiDisposable = it
                }
    }
    private fun currentPathLine(file: String? = null) : String {
        var path = pathLevel.joinToString("/")
        if (file != null) path += "/$file"
        if (path.startsWith("/")) return path
        return "/" + path
    }

    companion object {
        internal val ImageExtensions = setOf("jpg", "jpeg", "bmp", "png", "webp", "tiff")
        internal val MovieExtensions = setOf("mp4", "3gp", "mov")
    }
}