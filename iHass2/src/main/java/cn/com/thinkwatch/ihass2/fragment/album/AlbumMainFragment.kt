package cn.com.thinkwatch.ihass2.fragment.album

import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Transformation
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.aidl.IAlbumSyncCallback
import cn.com.thinkwatch.ihass2.aidl.IAlbumSyncService
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.bus.UploadChanged
import cn.com.thinkwatch.ihass2.bus.album.AlbumChanged
import cn.com.thinkwatch.ihass2.bus.album.AlbumConfiged
import cn.com.thinkwatch.ihass2.bus.album.AlbumDownloadAdded
import cn.com.thinkwatch.ihass2.network.BaseApi
import cn.com.thinkwatch.ihass2.network.base.api
import cn.com.thinkwatch.ihass2.network.http.HttpRestApi
import cn.com.thinkwatch.ihass2.service.AlbumSyncService
import cn.com.thinkwatch.ihass2.ui.AlbumDownloadActivity
import cn.com.thinkwatch.ihass2.ui.ChoiceFileActivity
import cn.com.thinkwatch.ihass2.ui.SettingAlbumActivity
import cn.com.thinkwatch.ihass2.utils.Gsons
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Dialogs
import com.dylan.common.sketch.Observers
import com.dylan.common.sketch.Sketch
import com.dylan.uiparts.activity.ActivityResult
import com.yunsean.dynkotlins.extensions.OnDialogItemClickedListener
import com.yunsean.dynkotlins.extensions.OnSettingDialogListener
import com.yunsean.dynkotlins.extensions.createDialog
import com.yunsean.dynkotlins.extensions.showDialog
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.dialog_album_working.*
import kotlinx.android.synthetic.main.fragment_album_main.*
import okhttp3.MediaType
import okhttp3.RequestBody
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.runOnUiThread
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.experimental.and


class AlbumMainFragment : BaseFragment() {

    override val layoutResId: Int = R.layout.fragment_album_main
    private var deleteDisposable : Disposable? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle("相册同步", false, null, R.drawable.titlebar_icon_menu)

        ui()
        tabbar()

        val intent = Intent(ctx, AlbumSyncService::class.java)
        ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        disposable = RxBus2.getDefault().register(AlbumChanged::class.java, { event ->
            syncService?.itemChanged()
        }, RxBus2.getDefault().register(AlbumConfiged::class.java, {
            syncService?.configChanged()
        }, RxBus2.getDefault().register(AlbumDownloadAdded::class.java, {
            syncService?.resumeDownload()
        }, disposable)))
    }
    override fun onDestroyView() {
        deleteDisposable?.dispose()
        ctx.unbindService(serviceConnection)
        super.onDestroyView()
    }
    override fun doRight() {
        if (syncService == null) return
        ctx.createDialog(R.layout.dialog_album_actions, null,
                intArrayOf(R.id.increment, R.id.afresh, R.id.addFile, R.id.addFolder, R.id.deleteLocal, R.id.setting, R.id.downloadItems, R.id.cancel),
                object : OnDialogItemClickedListener {
                    override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                        dialog.dismiss()
                        syncService?.let { syncService ->
                            when (clickedView.id) {
                                R.id.increment -> syncService.scanAll(false)
                                R.id.afresh -> syncService.scanAll(true)
                                R.id.addFile -> return@let addFile()
                                R.id.addFolder -> return@let addFolder()
                                R.id.deleteLocal -> return@let deleteLocal()
                                R.id.setting -> return@let startActivity(Intent(ctx, SettingAlbumActivity::class.java))
                                R.id.downloadItems -> return@let startActivity(Intent(ctx, AlbumDownloadActivity::class.java))
                                else -> return@let
                            }
                            waiting?.dismiss()
                            waiting = Dialogs.showWait(ctx, R.layout.dialog_album_working)
                        }
                    }
                }).show()
    }

    private data class FileItem(val path: String,
                                var md5: String? = null)
    private fun addFolder(path: String, newFiles: MutableList<FileItem>, emitter: ObservableEmitter<*>) {
        if (path.isBlank()) return
        val f = File(path)
        if (!f.exists()) return
        val files = f.listFiles() ?: return
        val newPaths = mutableListOf<String>()
        files.forEach {
            if (emitter.isDisposed) return
            if (it.name.startsWith(".")) return@forEach
            if (it.name.startsWith("chinadb.db")) return@forEach
            if (it.isFile) {
                newFiles.add(FileItem(it.absolutePath))
            } else if (it.isDirectory) {
                newPaths.add(it.absolutePath)
            }
        }
        newPaths.forEach {
            if (emitter.isDisposed) return
            addFolder(it, newFiles, emitter)
        }
    }
    private fun calcMd5(filePath: String?): String? {
        return try {
            val input = FileInputStream(filePath)
            val buffer = ByteArray(1024)
            val md5Hash = MessageDigest.getInstance("MD5")
            var numRead = 0
            while (numRead != -1) {
                numRead = input.read(buffer)
                if (numRead > 0) md5Hash.update(buffer, 0, numRead)
            }
            input.close()
            val md5Bytes: ByteArray = md5Hash.digest()
            return md5Bytes.fold(StringBuilder()) { sb, it->
                sb.append(Integer.toString((it and 0xff.toByte()) + 0x100, 16).substring(1))
            }.toString().toLowerCase()
        } catch (t: Throwable) {
            t.printStackTrace()
            ""
        }
    }
    private fun deleteLocal() {
        ctx.showDialog(R.layout.dialog_hass_confirm, object : OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                Sketch.set_tv(contentView, R.id.title, "删除已备份文件")
                Sketch.set_tv(contentView, R.id.content, "    删除时是否判断文件内容一致（如果不判断，将只根据文件路径和文件名相同则判定为已备份）？\n这将消耗很多时间。")
                Sketch.set_tv(contentView, R.id.cancel, "不判断")
                Sketch.set_tv(contentView, R.id.ok, "判断")
            }
        }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
            private var totalCount = 0
            override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                dialog.dismiss()
                val useMd5 = clickedView.id == R.id.ok
                val waiting = Dialogs.showWait(ctx, "正在扫描文件，请稍候...") { deleteDisposable?.dispose() }
                deleteDisposable?.dispose()
                deleteDisposable = Observable.create<RequestBody> {
                    val newFiles = mutableListOf<FileItem>()
                    cfg.getString(HassConfig.Album_Folders).split("*").filter { it.isNotBlank() }.map { path ->
                        addFolder(path, newFiles, it)
                    }
                    if (newFiles.isEmpty()) return@create it.onComplete()
                    if (useMd5) {
                        var percent = 0
                        val count = newFiles.size
                        newFiles.forEachIndexed { index, item ->
                            item.md5 = calcMd5(item.path)
                            (index * 50 / count).let {
                                if (it > percent) {
                                    percent = it
                                    runOnUiThread { waiting.setTips("正在计算MD5，${percent}%...") }
                                }
                            }
                        }
                    }
                    totalCount = newFiles.size
                    val json = Gsons.gson.toJson(newFiles)
                    it.onNext(RequestBody.create(MediaType.parse("application/json"), json))
                    it.onComplete()
                }.flatMap {
                    runOnUiThread { waiting.setTips("服务端正在运算，请稍候...") }
                    val userStub = cfg.getString(HassConfig.Album_UserStub)
                    BaseApi.jsonApi(cfg.haHostUrl, HttpRestApi::class.java)
                            .albumCheckFile(cfg.haPassword, cfg.haToken, userStub, it)
                }.flatMap {
                    runOnUiThread { waiting.setTips("正在删除本地文件，请稍候...") }
                    var deleted = 0
                    var failed = 0
                    it.forEach {
                        try {
                            File(it).delete()
                            deleted++
                        } catch (_: Exception) {
                            failed++
                        }
                    }
                    Observable.just(Pair(deleted, failed))
                }.subscribeOn(Schedulers.computation()).observeOn(AndroidSchedulers.mainThread())
                        .doOnNext {
                            val sb = java.lang.StringBuilder("共${totalCount}个文件，${it.first}个被清理")
                            if (it.second > 0) sb.append("，${it.second}个删除失败")
                            sb.append("！")
                            showInfo(sb.toString())
                        }
                        .doOnComplete {
                            waiting.dismiss()
                        }
                        .doOnError {
                            waiting.dismiss()
                            showError(it.localizedMessage ?: "删除已备份项失败！")
                        }
                        .doOnDispose {
                            waiting.dismiss()
                        }
                        .subscribe()
            }
        })
    }
    private fun addFolder() {
        startActivityForResult(Intent(ctx, ChoiceFileActivity::class.java)
                .putExtra("title", "添加同步目录")
                .putExtra("forFolder", true), 1078)
    }
    @ActivityResult(requestCode = 1078)
    private fun afterPickFolder(data: Intent) {
        val path = data.getStringExtra("path")
        if (path.isNullOrBlank()) return
        waiting?.dismiss()
        waiting = Dialogs.showWait(ctx, R.layout.dialog_album_working)
        syncService?.scanFolder(path)
    }
    private fun addFile() {
        startActivityForResult(Intent(ctx, ChoiceFileActivity::class.java)
                .putExtra("title", "添加同步文件")
                .putExtra("forFolder", false), 1079)
    }
    @ActivityResult(requestCode = 1079)
    private fun afterPickFile(data: Intent) {
        val path = data.getStringExtra("path")
        if (path.isNullOrBlank()) return
        syncService?.addFile(path)
    }
    private fun ui() {
        this.remote.setOnClickListener { viewPager.currentItem = 0 }
        this.working.setOnClickListener { viewPager.currentItem = 1 }
        this.failed.setOnClickListener { viewPager.currentItem = 2 }
        this.succeed.setOnClickListener { viewPager.currentItem = 3 }
    }
    private fun tabbar() {
        Observers.observeLayout(viewPager) { v ->
            val lp = flags.layoutParams as ViewGroup.MarginLayoutParams
            lineWidth = v.width / 4
            marginOffset = 0
            lp.leftMargin = marginOffset
            lp.width = lineWidth
            updateTab()
            false
        }
        viewPager.adapter = object : FragmentPagerAdapter(childFragmentManager) {
            override fun getPageTitle(position: Int): CharSequence = titleName.get(position)
            override fun getCount(): Int = titleName.size
            override fun getItem(position: Int): Fragment {
                return when (position) {
                    1 -> AlbumItemsFragment.newInstance(AlbumItemsFragment.AlbumListType_Working)
                    2 -> AlbumItemsFragment.newInstance(AlbumItemsFragment.AlbumListType_Failed)
                    3 -> AlbumItemsFragment.newInstance(AlbumItemsFragment.AlbumListType_Succeed)
                    else-> AlbumRemoteFragment()
                }
            }
        }
        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {
                updateTab()
            }
        })
        viewPager.setCanScroll(true)
    }

    private val titleName = arrayOf("远端", "进行中", "失败", "已完成")
    private val titleView = intArrayOf(R.id.remote, R.id.working, R.id.failed, R.id.succeed)
    private var marginOffset = 0
    private var lineWidth = 0
    private fun updateTab() {
        titleView.forEachIndexed { index, it ->
            fragment.findViewById<View>(it).isSelected = viewPager.currentItem == index
        }
        val lp = flags.layoutParams as ViewGroup.MarginLayoutParams
        val fromX = lp.leftMargin
        val toX = lineWidth * viewPager.currentItem + marginOffset
        val anim = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                lp.leftMargin = (fromX + (toX - fromX) * interpolatedTime).toInt()
                flags.requestLayout()
            }
            override fun willChangeBounds(): Boolean = true
        }
        anim.duration = 300
        flags.startAnimation(anim)
    }

    private var syncService: IAlbumSyncService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            syncService = IAlbumSyncService.Stub.asInterface(service)
            syncService?.registerCallback(syncCallback)
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            syncService = null
        }
    }
    private var waiting: Dialog? = null
    private val syncCallback = object: IAlbumSyncCallback.Stub() {
        override fun onUploadChanged() {
            RxBus2.getDefault().post(UploadChanged())
        }
        override fun onActionProgress(percent: Int, message: String?) {
            waiting?.let { it.tips.text = message }
        }
        override fun onActionCompleted() {
            waiting?.dismiss()
            waiting = null
        }
        override fun onFileDownloaded(id: Long) = Unit
        override fun onFileDownloading(id: Long, percent: Int) = Unit
        override fun onFileDownloadPaused(paused: Boolean) = Unit
    }
}