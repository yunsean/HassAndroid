package cn.com.thinkwatch.ihass2.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.support.v4.app.NotificationCompat
import android.view.View
import android.widget.RemoteViews
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.aidl.IAlbumSyncCallback
import cn.com.thinkwatch.ihass2.aidl.IAlbumSyncService
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.enums.AlbumSyncStatus
import cn.com.thinkwatch.ihass2.fragment.album.AlbumItemsFragment
import cn.com.thinkwatch.ihass2.model.album.AlbumDownloadItem
import cn.com.thinkwatch.ihass2.model.album.AlbumLocalItem
import cn.com.thinkwatch.ihass2.network.BaseApi
import cn.com.thinkwatch.ihass2.network.http.HttpRawApi
import cn.com.thinkwatch.ihass2.network.http.HttpRestApi
import cn.com.thinkwatch.ihass2.retrofit.FileRequestBody
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import com.yunsean.dynkotlins.extensions.*
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.jetbrains.anko.ctx
import org.jetbrains.anko.runOnUiThread
import java.io.*
import java.lang.Exception
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.experimental.and


class AlbumSyncService : Service() {

    override fun onBind(intent: Intent): IBinder? = binder
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY_COMPATIBILITY
    override fun onCreate() {
        super.onCreate()
        setupNotificationChannel()
        binder.configChanged()
        registerReceiver()
        registerBroadcast()
    }
    override fun onDestroy() {
        unregisterReceiver()
        unregisterBroadcast()
        super.onDestroy()
    }

    private val binder = AlbumSyncServiceStub()
    private var used = false
    private var userStub: String? = null
    private var autoUpload = HassConfig.UploadMode_None
    private var autoOverride = false
    private var uploadWifis : List<String>? = null
    private var scanInterval = 0
    private var lastScan = 0L
    inner class AlbumSyncServiceStub: IAlbumSyncService.Stub() {
        private val callbackList = RemoteCallbackList<IAlbumSyncCallback>()
        private var isScaning = false

        override fun configChanged() {
            cfg.reload()
            used = cfg.getBoolean(HassConfig.Album_Used)
            if (!used) {
                pauseSync()
                return notificationManager.cancel(-2)
            }
            userStub = cfg.getString(HassConfig.Album_UserStub)
            autoOverride = cfg.getBoolean(HassConfig.Album_Override)
            autoUpload = cfg.getInt(HassConfig.Album_AutoUpload)
            uploadWifis = cfg.getString(HassConfig.Album_UploadWifi).split("*").filter { it.isNotBlank() }
            scanInterval = cfg.getInt(HassConfig.Album_ScanInterval)
            lastScan = cfg.getLong(HassConfig.Album_LastScan)
            if (scanInterval > 0) scanInterval = (scanInterval * 24 - 6) * 3_600_000
            binder.scanAll(false)
            checkAutoUpload()
        }
        override fun resumeUpload() {
        }
        override fun itemChanged() {
            refreshNotification()
            if (failedCount > 0 || waitingCount > 0) checkAutoUpload()
        }
        override fun scanFolder(path: String?) {
            db.async {
                isScaning = true
                try { scanFolder(path, true, 0L) } catch (_: Throwable) { }
                isScaning = false
                return@async true
            }.nextOnMain {
                notifyCompleted()
                checkAutoUpload()
            }
        }
        override fun addFile(path: String?) {
            if (path.isNullOrBlank()) return
            if (db.addAlbumItems(listOf(path), false)) {
                checkAutoUpload()
            }
        }
        override fun scanAll(arefresh: Boolean) {
            if (isScaning) return
            db.async {
                isScaning = true
                try {
                    val earlyTime = cfg.getLong(HassConfig.Album_EarlyTime)
                    cfg.getString(HassConfig.Album_Folders).split("*").filter { it.isNotBlank() }.map { path ->
                        scanFolder(path, arefresh, earlyTime)
                    }
                } catch (_: Throwable) {
                }
                isScaning = false
                return@async true
            }.nextOnMain {
                notifyCompleted()
                refreshNotification()
            }
        }
        override fun registerCallback(cb: IAlbumSyncCallback) {
            callbackList.register(cb)
        }
        override fun unregisterCallback(cb: IAlbumSyncCallback) {
            callbackList.unregister(cb)
        }

        private fun scanFolder(path: String?, afresh: Boolean, earlyTime: Long) {
            if (path.isNullOrBlank()) return
            val f = File(path)
            if (!f.exists()) return
            val files = f.listFiles() ?: return
            val newFiles = mutableListOf<String>()
            val newPaths = mutableListOf<String>()
            notifyProgress("正在扫描 $path")
            files.forEach {
                if (it.name.startsWith(".")) return@forEach
                if (it.name.startsWith("chinadb.db")) return@forEach
                if (it.isFile && it.lastModified() > earlyTime) {
                    newFiles.add(it.absolutePath)
                } else if (it.isDirectory) {
                    newPaths.add(it.absolutePath)
                }
            }
            if (db.addAlbumItems(newFiles, afresh)) notifyChanged()
            newFiles.clear()
            newPaths.forEach { scanFolder(it, afresh, earlyTime) }
        }
        private fun notifyChanged() {
            val count = callbackList.beginBroadcast()
            try { for (i in 0 until count) callbackList.getBroadcastItem(i).onUploadChanged() } catch (ex: RemoteException) { ex.printStackTrace() }
            callbackList.finishBroadcast()
        }
        private fun notifyProgress(message: String) {
            val count = callbackList.beginBroadcast()
            try { for (i in 0 until count) callbackList.getBroadcastItem(i).onActionProgress(0, message) } catch (ex: RemoteException) { ex.printStackTrace() }
            callbackList.finishBroadcast()
        }
        private fun notifyCompleted() {
            val count = callbackList.beginBroadcast()
            try { for (i in 0 until count) callbackList.getBroadcastItem(i).onActionCompleted() } catch (ex: RemoteException) { ex.printStackTrace() }
            callbackList.finishBroadcast()
        }

        internal fun attempSync() {
            if (!used) return
            if (isUploading) return
            if (syncStatus == SyncStatus.paused) return refreshNotification(true)
            isUploading = true
            Observable.create<AlbumLocalItem> {
                val item = db.getAlbumFirstWaiting() ?: return@create it.onError(NoSyncItemException())
                if (!File(item.path).exists()) {
                    item.status = AlbumSyncStatus.failed
                    item.reason = "源文件不存在"
                    db.updateAlbumItem(item)
                    return@create it.onComplete()
                }
                uploadingItem = item
                runOnUiThread { refreshNotification() }
                item.md5 = calcMd5(item.path)
                it.onNext(item)
                it.onComplete()
            }.flatMap {
                val file = File(it.path)
                val body = RequestBody.create(MediaType.parse("application/x-www-form-urlencoded"), file)
                val fileBody = FileRequestBody(body) { total, progress, percent ->
                    if (percent == 100 || System.currentTimeMillis() - lastUpdate >= 1000) {
                        lastUpdate = System.currentTimeMillis()
                        uploadPercent = percent
                        runOnUiThread { setupNotification() }
                    }
                }
                val override = autoOverride || it.override
                BaseApi.jsonApi(cfg.haHostUrl, HttpRestApi::class.java)
                        .albumPostSync(cfg.haPassword, cfg.haToken, fileBody, userStub, it.path, it.md5, if (override) true else null, file.lastModified() / 1000)
            }.subscribeOn(Schedulers.computation()).observeOn(AndroidSchedulers.mainThread()).doOnNext { r->
                uploadingItem?.let {
                    when (r.result) {
                        "ok"-> {
                            it.status = AlbumSyncStatus.succeed
                            it.time = Date()
                            uploadedCount++
                        }
                        "exist"-> {
                            it.status = AlbumSyncStatus.failed
                            it.override = true
                            val mtime = r.mtime?.let { (it * 1000).kdateTime() }
                            it.reason = "文件存在： ${AlbumItemsFragment.formatSize(r.size ?: 0)}，${mtime}修改"
                        }
                        else-> {
                            it.status = AlbumSyncStatus.failed
                            it.reason = r.message ?: "远端服务器错误"
                        }
                    }
                    db.updateAlbumItem(it)
                }
            }.doOnComplete {
                isUploading = false
                notifyChanged()
                attempSync()
            }.doOnError { ex->
                isUploading = false
                if (ex is NoSyncItemException) {
                    syncStatus = if (failedCount > 0) SyncStatus.pending
                    else SyncStatus.finished
                    refreshNotification(true)
                } else {
                    notifyChanged()
                    uploadingItem?.let {
                        it.retried++
                        if (it.retried > 5) it.status = AlbumSyncStatus.failed
                        it.reason = ex.localizedMessage ?: "未知错误"
                        db.updateAlbumItem(it)
                    }
                    refreshNotification(true)
                    attempSync()
                }
            }.doOnDispose {
                isUploading = false
            }.doOnSubscribe {
                uploadDisposable = it
            }.subscribe()
        }
        fun pauseSync() {
            uploadDisposable?.dispose()
        }

        private var downloadPaused = true
        private var isDownload = false
        private var downloadingItem: AlbumDownloadItem? = null
        private var downloadDisposable: Disposable? = null
        override fun resumeDownload() {
            if (downloadPaused) {
                downloadPaused = false
                notifyDownloadPaused()
            }
            attempDownload()
        }
        override fun pauseDownload() {
            if (!downloadPaused) {
                downloadPaused = true
                notifyDownloadPaused()
            }
            downloadDisposable?.dispose()
        }
        override fun isDownloadPaused() = downloadPaused
        override fun downloadingId(): Long = downloadingItem?.id ?: 0L
        internal fun attempDownload() {
            if (isDownload || downloadPaused) return
            val item = db.getAlbumFirstDownload() ?: return
            downloadingItem = item
            isDownload = true
            notifyDownloading(item.id, 0)
            val localPath = if (item.localPath.isNullOrBlank()) item.path else item.localPath
            BaseApi.fileApi(cfg.haHostUrl, HttpRawApi::class.java)
                    .albumDownload(cfg.haPassword, cfg.haToken, item.userStub, item.path)
                    .subscribeOn(Schedulers.computation()).doOnNext {
                        val file = File(localPath)
                        val mtime = it.headers()["Modify-Time"]?.toLongOrNull()
                        it.body()?.let { writeToFile(item.id, it.contentLength(), it, file, mtime) }
                    }.doOnComplete {
                        isDownload = false
                        db.addAlbumDownloadedItem(localPath)
                        db.deleteDownloadItems(listOf(item.id))
                        notifyDownloaded(item.id)
                        attempDownload()
                    }.doOnError { ex->
                        isDownload = false
                        downloadingItem?.let {
                            it.failed = true
                            it.reason = if (ex.localizedMessage.isNullOrBlank()) "未知错误" else ex.localizedMessage
                            db.updateDownloadItem(it)
                        }
                        notifyDownloaded(item.id)
                        attempDownload()
                    }.doOnDispose {
                        downloadingItem = null
                        isDownload = false
                        db.updateDownloadItem(item)
                    }.doOnSubscribe {
                        downloadDisposable = it
                    }.subscribe()
        }
        private fun writeToFile(id: Long, totalSize: Long, body: ResponseBody, file: File, mtime: Long?): Boolean {
            try {
                var lastUpdate = System.currentTimeMillis()
                body.use {
                    val inputStream = body.byteStream()
                    FileOutputStream(file).use {
                        val outputStream = FileOutputStream(file)
                        val fileReader = ByteArray(2048)
                        var totalRead = 0L
                        while (true) {
                            val read = inputStream.read(fileReader)
                            if (read == -1) break
                            totalRead += read
                            outputStream.write(fileReader, 0, read)
                            if (System.currentTimeMillis() - lastUpdate > 1000) {
                                lastUpdate = System.currentTimeMillis()
                                val percent = (if (totalSize > 0) totalRead * 100 / totalSize else 0).let { if (it > 100) 100 else it }
                                notifyDownloading(id, percent.toInt())
                            }
                        }
                        outputStream.flush()
                    }
                }
                mtime?.let { file.setLastModified(it * 1000) }
                return true
            } catch (ex: Exception) {
                ex.printStackTrace()
                throw ex
            }
        }
        private fun notifyDownloaded(id: Long) {
            val count = callbackList.beginBroadcast()
            try { for (i in 0 until count) callbackList.getBroadcastItem(i).onFileDownloaded(id) } catch (ex: RemoteException) { ex.printStackTrace() }
            callbackList.finishBroadcast()
        }
        private fun notifyDownloading(id: Long, percent: Int) {
            val count = callbackList.beginBroadcast()
            try { for (i in 0 until count) callbackList.getBroadcastItem(i).onFileDownloading(id, percent) } catch (ex: RemoteException) { ex.printStackTrace() }
            callbackList.finishBroadcast()
        }
        private fun notifyDownloadPaused() {
            val count = callbackList.beginBroadcast()
            try { for (i in 0 until count) callbackList.getBroadcastItem(i).onFileDownloadPaused(downloadPaused) } catch (ex: RemoteException) { ex.printStackTrace() }
            callbackList.finishBroadcast()
        }
    }

    class NoSyncItemException : RuntimeException()
    enum class SyncStatus { working, paused, pending, finished }
    private var uploadedCount = 0
    private var failedCount = 0L
    private var waitingCount = 0L
    private var uploadingItem: AlbumLocalItem? = null
    private var lastUpdate = 0L
    private var isUploading = false
    private var uploadPercent = 0
    private var syncStatus = SyncStatus.paused
    private var uploadDisposable: Disposable? = null
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val wifiManager by lazy {  applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    private fun setupNotification() {
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_APP)
        val parentView = RemoteViews(packageName, R.layout.notification_album)
        parentView.setOnClickPendingIntent(R.id.status, PendingIntent.getBroadcast(ctx, 101, Intent("cn.com.thinkwatch.ihass2.AlbumSync"), 0))
        parentView.setTextViewText(R.id.succeedCount, "S$uploadedCount")
        parentView.setTextViewText(R.id.failedCount, "F$failedCount")
        parentView.setTextViewText(R.id.watingCount, "W$waitingCount")
        when (syncStatus) {
            SyncStatus.working-> {
                parentView.setImageViewResource(R.id.status, R.drawable.album_status_sync)
                parentView.setTextViewText(R.id.file, uploadingItem?.name ?: "正在同步")
                parentView.setProgressBar(R.id.progressBar, 100, uploadPercent, false)
                parentView.setTextViewText(R.id.size, uploadingItem?.let { "${AlbumItemsFragment.formatSize(it.size)} / ${it.mtime.kdateTime("yyyy-MM-dd HH:mm")}" } ?: "未知大小")
                parentView.setViewVisibility(R.id.size, View.VISIBLE)
            }
            SyncStatus.paused-> {
                parentView.setImageViewResource(R.id.status, R.drawable.album_status_paused)
                parentView.setTextViewText(R.id.file, "已暂停同步，点击右侧按钮开始")
                val percent = if (failedCount + waitingCount + uploadedCount > 0) (uploadedCount * 100 / (failedCount + waitingCount + uploadedCount)).toInt() else 100
                parentView.setProgressBar(R.id.progressBar, 100, percent, false)
                parentView.setViewVisibility(R.id.size, View.GONE)
            }
            SyncStatus.pending-> {
                parentView.setImageViewResource(R.id.status, R.drawable.album_status_pending)
                parentView.setTextViewText(R.id.file, "同步成功${uploadedCount}个文件，部分任务失败，点击查看详情")
                parentView.setProgressBar(R.id.progressBar, 100, 0, false)
                parentView.setViewVisibility(R.id.size, View.GONE)
            }
            SyncStatus.finished-> {
                parentView.setImageViewResource(R.id.status, R.drawable.album_status_finished)
                parentView.setTextViewText(R.id.file, "${uploadedCount}个文件全部同步完成，点击右侧按钮关闭")
                parentView.setProgressBar(R.id.progressBar, 100, 100, false)
                parentView.setViewVisibility(R.id.size, View.GONE)
            }
        }
        parentView.setOnClickPendingIntent(R.id.container, PendingIntent.getActivity(ctx, 203, Intent()
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .setAction("cn.com.thinkwatch.ihass2.MainActivity")
                .putExtra("event", "showAlbum"), PendingIntent.FLAG_CANCEL_CURRENT))

        builder.setSmallIcon(R.mipmap.ic_launcher)
                .setDefaults(0)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContent(parentView)
        notificationManager.notify(-2, builder.build())
    }
    private fun isNotificationShown(): Boolean {
        if (Build.VERSION.SDK_INT > 22) {
            val notifications = notificationManager.getActiveNotifications()
            for (notification in notifications) {
                if (notification.id == -2) return true
            }
            return false
        } else {
            return false
        }
    }
    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(NOTIFICATION_CHANNEL_APP, "应用快捷操作", NotificationManager.IMPORTANCE_DEFAULT).let {
                notificationManager.createNotificationChannel(it)
            }
        }
    }
    private val onAlbumSyncClicked = object: BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            when (syncStatus) {
                SyncStatus.working-> {
                    syncStatus = SyncStatus.paused
                    setupNotification()
                    uploadDisposable?.dispose()
                }
                SyncStatus.paused-> {
                    uploadPercent = 0
                    syncStatus = SyncStatus.working
                    setupNotification()
                    binder.attempSync()
                }
                SyncStatus.pending-> {
                    db.resetAlbumFailedItems()
                    failedCount = db.getAlbumFailedCount()
                    waitingCount = db.getAlbumUnsyncCount()
                    syncStatus = SyncStatus.working
                    setupNotification()
                    binder.attempSync()
                }
                SyncStatus.finished-> {
                    uploadedCount = 0
                    notificationManager.cancel(-2)
                }
            }
        }
    }
    private fun registerReceiver() {
        registerReceiver(onAlbumSyncClicked, IntentFilter("cn.com.thinkwatch.ihass2.AlbumSync"))
    }
    private fun unregisterReceiver() {
        unregisterReceiver(onAlbumSyncClicked)
    }
    private var broadcastReceiver: BroadcastReceiver? = null
    private fun registerBroadcast() {
        broadcastReceiver?.let { unregisterReceiver(it) }
        broadcastReceiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                if (!used) return
                checkAutoUpload()
            }
        }
        val filter = IntentFilter()
        filter.priority = IntentFilter.SYSTEM_HIGH_PRIORITY - 9
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        filter.addAction(Intent.ACTION_SCREEN_ON)
        registerReceiver(broadcastReceiver, filter)
    }
    private fun unregisterBroadcast() {
        broadcastReceiver?.let { unregisterReceiver(it) }
        broadcastReceiver = null
    }

    private fun refreshNotification(forceSetup: Boolean = false) {
        failedCount = db.getAlbumFailedCount()
        waitingCount = db.getAlbumUnsyncCount()
        if (waitingCount > 0 && (syncStatus == SyncStatus.finished || syncStatus == SyncStatus.pending)) {
            syncStatus = SyncStatus.paused
        }
        if (waitingCount < 1 && failedCount > 0) {
            syncStatus = SyncStatus.pending
        }
        if (waitingCount + failedCount < 1) {
            syncStatus = SyncStatus.finished
        }
        if (failedCount + waitingCount > 0 || forceSetup || isNotificationShown()) {
            setupNotification()
        }
    }
    private fun checkNeedScan() {
        if (scanInterval != 0) {
            val before = System.currentTimeMillis() - scanInterval
            if (lastScan < before) {
                lastScan = System.currentTimeMillis()
                cfg.set(HassConfig.Album_LastScan, lastScan)
                binder.scanAll(false)
            }
        }
    }
    private fun checkNeedUpload() {
        failedCount = db.getAlbumFailedCount()
        waitingCount = db.getAlbumUnsyncCount()
        if (failedCount + waitingCount < 1) {
            syncStatus = SyncStatus.finished
            return
        }
        if (waitingCount > 0 && (syncStatus == SyncStatus.finished || syncStatus == SyncStatus.pending)) syncStatus = SyncStatus.paused
        if (waitingCount > 0 && syncStatus != SyncStatus.working) {
            syncStatus = SyncStatus.working
            binder.attempSync()
        }
    }
    private var delayDisposable: Disposable? = null
    internal fun checkAutoUpload() {
        checkNeedScan()
        val inLan = when (autoUpload) {
            HassConfig.UploadMode_None-> return
            HassConfig.UploadMode_Always-> true
            HassConfig.UploadMode_AllWifi-> wifiManager.connectionInfo?.bssid != null
            else-> wifiManager.connectionInfo?.bssid?.let { uploadWifis?.contains(it) == true } == true
        }
        delayDisposable?.dispose()
        if (!inLan && syncStatus == SyncStatus.working) {
            syncStatus = SyncStatus.paused
            binder.pauseSync()
            setupNotification()
        } else if (inLan) {
            Observable.timer(10, TimeUnit.SECONDS)
                    .withNext {
                        checkNeedUpload()
                    }
                    .subscribe {
                        delayDisposable = it
                    }
        }
    }

    fun calcMd5(filePath: String?): String? {
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
            return md5Bytes.fold(StringBuilder()) {
                sb, it->
                sb.append(Integer.toString((it and 0xff.toByte()) + 0x100, 16).substring(1))
            }.toString().toLowerCase()
        } catch (t: Throwable) {
            t.printStackTrace()
            ""
        }
    }

    companion object {
        private val NOTIFICATION_CHANNEL_APP = "notifyApp"
    }
}