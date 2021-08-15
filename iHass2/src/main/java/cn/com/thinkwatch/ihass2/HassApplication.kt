package cn.com.thinkwatch.ihass2

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Resources
import android.graphics.Color
import android.os.DeadObjectException
import android.os.IBinder
import android.support.v4.app.Fragment
import android.support.v7.widget.RecyclerView
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import cn.com.thinkwatch.ihass2.aidl.IDataSyncCallback
import cn.com.thinkwatch.ihass2.aidl.IDataSyncService
import cn.com.thinkwatch.ihass2.bus.*
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.Location
import cn.com.thinkwatch.ihass2.model.Period
import cn.com.thinkwatch.ihass2.model.broadcast.Channel
import cn.com.thinkwatch.ihass2.model.service.Domain
import cn.com.thinkwatch.ihass2.network.base.Api
import cn.com.thinkwatch.ihass2.network.base.Websocket
import cn.com.thinkwatch.ihass2.service.DataSyncService
import cn.com.thinkwatch.ihass2.utils.Gsons
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.dylan.common.application.Application
import com.dylan.common.rx.RxBus2
import com.dylan.common.utils.Utility
import com.facebook.stetho.Stetho
import com.github.promeg.pinyinhelper.Pinyin
import com.github.promeg.pinyinhelper.PinyinMapDict
import com.github.promeg.tinypinyin.lexicons.android.cncity.CnCityDict
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.yunsean.dynkotlins.extensions.*
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.*
import org.xutils.x
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

open class HassApplication: Application() {

    private var disposable: CompositeDisposable? = null
    override fun onCreate() {
        super.onCreate()
        x.Ext.init(this)
        application = this

        toastGravity = Gravity.BOTTOM
        try { SDKInitializer.initialize(this) } catch (_: Exception) {}
        SDKInitializer.setCoordType(CoordType.GCJ02)
        RxJavaPlugins.setErrorHandler { logws("unhandled rx error: ${it.localizedMessage}") }

        Pinyin.init(Pinyin.newConfig().with(CnCityDict.getInstance(this))
                .with(object: PinyinMapDict() {
                    override fun mapping(): MutableMap<String, Array<String>> {
                        return mutableMapOf("贝壳" to arrayOf("BEI", "KE"))
                    }
                }))
        if (isMainProcess()) {
            migratePreference()
            val intent = Intent(ctx, DataSyncService::class.java)
            ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            disposable = RxBus2.getDefault().register(ServiceRequest::class.java, {
                callService(it)
            }, RxBus2.getDefault().register(HassConfiged::class.java, {
                hassChanged()
            }, RxBus2.getDefault().register(ConfigChanged::class.java, {
                configChanged()
            }, RxBus2.getDefault().register(TriggerChanged::class.java, {
                syncService?.triggerChanged()
            }, RxBus2.getDefault().register(ObservedChanged::class.java, {
                syncService?.observedChanged()
            }, RxBus2.getDefault().register(WidgetChanged::class.java, {
                syncService?.widgetChanged()
            }, RxBus2.getDefault().register(NotificationChanged::class.java, {
                syncService?.notificationChanged()
            }, disposable)))))))
        }

        Stetho.initialize(Stetho.newInitializerBuilder(this)
                .enableWebKitInspector(Stetho.defaultInspectorModulesProvider(this))
                .build())
    }
    val fontScale by lazy { cfg.getInt(HassConfig.Ui_FontScale, 85) / 100f }
    override fun getResources(): Resources {
        val resources = super.getResources()
        if (resources != null && resources.configuration.fontScale != fontScale) {
            val configuration = resources.configuration
            configuration.fontScale = fontScale
            resources.updateConfiguration(configuration, resources.displayMetrics)
        }
        return resources
    }

    private fun migratePreference() {
        if (cfg.get(HassConfig.Hass_HostUrl).isNullOrBlank() && !readPref("Hass_HostUrl").isNullOrBlank()) {
            cfg.set(HassConfig.Hass_HostUrl, readPref("Hass_HostUrl"))
            cfg.set(HassConfig.Hass_Password, readPref("Hass_Password"))
            cfg.set(HassConfig.Ui_PullRefresh, readPref("pullRefresh"))
            cfg.set(HassConfig.Gps_Logger, readPref("gps.Logger"))
            cfg.set(HassConfig.Gps_DeviceName, readPref("gps.deviceName"))
            cfg.set(HassConfig.Gps_DeviceId, readPref("Gps_DeviceId"))
            cfg.set(HassConfig.Gps_Password, readPref("gps.password"))
        }
    }
    fun isMainProcess(): Boolean {
        val pid = android.os.Process.myPid()
        val manager = getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val process = manager.runningAppProcesses.find { it.pid == pid }
        return process?.processName == packageName
    }

    var serviceIntercepting: Boolean = false
    fun interceptService(request: ServiceRequest) {
        val serviceId = "${request.domain}.${request.service}"
        val content = Gsons.gson.toJson(request)
        RxBus2.getDefault().post(ServiceIntercepted(serviceId, content))
    }

    protected data class InvokeItem(val type: Type,
                                    val emitter: ObservableEmitter<*>?)
    private var invokeIndex: Long = 10L
    private val invokeItems = mutableMapOf<Long, InvokeItem>()
    @Synchronized private fun nextIndex(): Long = ++invokeIndex
    private fun onCallResult(index: Long, result: String?, error: String?) {
        invokeItems.get(index)?.let {
            synchronized(invokeItems) { invokeItems.remove(index) }
            if (it.emitter?.isDisposed ?: true) return@let
            (it.emitter as ObservableEmitter<Any?>?)?.apply {
                if (error != null) return@let onError(Exception(error))
                try {
                    result?.let {result-> Gsons.gson.fromJson<Any>(result, it.type)?.let { onNext(it) } }
                    onComplete()
                } catch (ex: Exception) {
                    return@let onError(ex)
                }
            }
        }
    }
    private fun callError(index: Long, emitter: ObservableEmitter<*>?, error: Exception) {
        if (!(emitter?.isDisposed ?: true)) {
            try { emitter?.onError(error) } catch (_: Throwable) { }
        }
        synchronized(invokeItems) { invokeItems.remove(index) }
    }
    fun hassChanged() {
        try {
            cfg.reload()
            Api.api = null
            Websocket.ws = null
            syncService?.hassChanged()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
    fun configChanged() {
        try {
            cfg.reload()
            syncService?.configChanged()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
    fun protectEye(use: Boolean, color: Int, save: Boolean) {
        syncService?.protectEye(use, color, save)
    }

    private var tipsToast: Toast? = null
    fun	callServiceTips(message: String, result: Boolean) {
		val view = layoutInflater.inflate(R.layout.listitem_toast,null);
		view.find<TextView>(R.id.text).apply {
            text = message
            backgroundResource = if (result) R.drawable.shape_2bb964 else R.drawable.shape_ce6240
            textColor = Color.WHITE
            layoutParams = LinearLayout.LayoutParams(Utility.screenWidth(this@HassApplication), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        tipsToast?.cancel()
		val toast = Toast(this);
		toast.setGravity(Gravity.TOP,0,0)
		toast.duration = Toast.LENGTH_SHORT
		toast.view = view
		toast.show()
        view.postDelayed({ toast.cancel(); tipsToast = null }, if (result) 500L else 2000L)
        tipsToast = toast
	}

    fun callService(request: ServiceRequest) {
        if (serviceIntercepting) return interceptService(request)
        RxBus2.getDefault().post(NetBusyEvent(true))
        callService3(request)
                .nextOnMain {
                    RxBus2.getDefault().post(NetBusyEvent(false))
                    callServiceTips("操作成功！", true)
                }
                .error {
                    callServiceTips("操作失败！", false)
                    RxBus2.getDefault().post(NetBusyEvent(false))
                    RxBus2.getDefault().post(HassErrorEvent(it.getMessage() ?: "访问HA出现错误"))
                }
    }
    fun callService3(request: ServiceRequest): Observable<ArrayList<JsonEntity>> {
        return Observable.create<ArrayList<JsonEntity>> {
            val index = nextIndex()
            synchronized(invokeItems) { invokeItems.set(index, InvokeItem(object: TypeToken<ArrayList<JsonEntity>>(){}.type, it)) }
            try {
                syncService!!.callService(index, request.domain, request.service, request)
            } catch (ex: Exception) {
                if (it is DeadObjectException) ctx.bindService(Intent(ctx, DataSyncService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
                callError(index, it, ex)
            }
        }.subscribeOn(Schedulers.computation())
    }
    fun getServices(): Observable<ArrayList<Domain>> {
        return Observable.create<ArrayList<Domain>> {
            val index = nextIndex()
            synchronized(invokeItems) { invokeItems.set(index, InvokeItem(object: TypeToken<ArrayList<Domain>>(){}.type, it)) }
            try {
                syncService!!.getService(index)
            } catch (ex: Exception) {
                if (ex is DeadObjectException) ctx.bindService(Intent(ctx, DataSyncService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
                callError(index, it, ex)
            }
        }.subscribeOn(Schedulers.computation())
    }
    fun getHistory(timestamp: String?, entityId: String?, endTime: String? = null): Observable<ArrayList<ArrayList<Period>>> {
        return Observable.create<ArrayList<ArrayList<Period>>> {
            val index = nextIndex()
            synchronized(invokeItems) { invokeItems.set(index, InvokeItem(object: TypeToken<ArrayList<ArrayList<Period>>>(){}.type, it)) }
            try {
                syncService!!.getHistory(index, timestamp, entityId, endTime)
            } catch (ex: Exception) {
                if (ex is DeadObjectException) ctx.bindService(Intent(ctx, DataSyncService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
                callError(index, it, ex)
            }
        }.subscribeOn(Schedulers.computation())
    }
    fun refreshState(): Observable<String> {
        return Observable.create<String> {
            if (syncService == null) {
                it.onComplete()
                return@create
            }
            val index = nextIndex()
            synchronized(invokeItems) { invokeItems.set(index, InvokeItem(String::class.java, it)) }
            try {
                syncService!!.updateEntity(index)
            } catch (ex: Exception) {
                ex.printStackTrace()
                if (ex is DeadObjectException) {
                    ctx.bindService(Intent(ctx, DataSyncService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
                    it.onComplete()
                } else {
                    it.onError(ex)
                }
            }
        }
                .timeout(15, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.computation())
    }

    val location: Location?
    get() = syncService?.location
    fun requestLocation() {
        syncService?.requestLocation()
    }
    fun nfcTrigger(uid: String) {
        syncService?.nfcTrigger(uid)
    }

    var isConnectByLan: Boolean = false
    get() {
        logis("get isConnectByLan=${field}")
        return field
    }
    set(value) {
        logis("set isConnectByLan from ${field} to ${value}")
        field = value
    }

    private var syncService: IDataSyncService? = null
    private var serviceBound: Boolean = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            try {
                syncService = IDataSyncService.Stub.asInterface(service)
                serviceBound = true
                syncService?.registerCallback(object: IDataSyncCallback.Stub() {
                    override fun onEntityChanged(entityId: String?) {
                        if (entityId == null) return
                        RxBus2.getDefault().post(EntityChanged(entityId))
                    }
                    override fun onLocationChanged(location: Location?) {
                        if (location == null) return
                        RxBus2.getDefault().post(LocationChanged(location.latitude, location.longitude))
                    }
                    override fun onEntityUpdated() {
                        RxBus2.getDefault().post(EntityUpdated())
                    }
                    override fun onCallResult(index: Long, result: String?, error: String?) {
                        this@HassApplication.onCallResult(index, result, error)
                    }
                    override fun onConnectChanged(isLocal: Boolean) {
                        RxBus2.getDefault().post(ConnectChanged(isLocal))
                    }
                })
                refreshState().completeOnMain { RxBus2.getDefault().post(EntityUpdated()) }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            serviceBound = false
        }
    }

    val xmlyChannels: Map<Int, Channel> by lazy {
        val channels = mutableMapOf<Int, Channel>()
        try {
            val stream = resources?.openRawResource(R.raw.xmly)
            val reader = JsonReader(InputStreamReader(stream))
            reader.beginArray()
            var id = 0
            var name = ""
            var conver = ""
            var node = ""
            var token: JsonToken?
            while (reader.hasNext()) {
                reader.beginObject()
                do {
                    node = reader.nextName()
                    if (node == "id") id = reader.nextInt()
                    else if (node == "name") name = reader.nextString()
                    else if (node == "conver") conver = reader.nextString()
                    token = reader.peek()
                } while (token != null && token != JsonToken.END_OBJECT)
                reader.endObject()
                channels.put(id, Channel(id, name, conver))
            }
            reader.endArray()
        } catch (_: Exception) {
        }
        channels
    }


    var relayLauncher: String? = null
        get() {
            if (field == null) field = readPref("RelayLauncher")
            return field
        }
        set(value) {
            savePref("RelayLauncher", value ?: "")
            field = value
        }

    var stubbornTabs = mutableListOf<FragmentItem>()
    var panelViewPool = RecyclerView.RecycledViewPool()

    companion object {
        lateinit var application: HassApplication
            private set

        data class FragmentItem(val name: String,
                                val icon: String,
                                val clazz: String)
    }
}


inline val Context.app: HassApplication
    get() = HassApplication.application
inline val Fragment.app: HassApplication
    get() = HassApplication.application


fun Throwable.getMessage(): String? {
    return this.cause?.let { if (it == this) this.localizedMessage else it.getMessage() } ?: this.localizedMessage
}