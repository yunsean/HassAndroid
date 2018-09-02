package cn.com.thinkwatch.ihass2

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.support.v4.app.Fragment
import cn.com.thinkwatch.ihass2.aidl.IDataSyncCallback
import cn.com.thinkwatch.ihass2.aidl.IDataSyncService
import cn.com.thinkwatch.ihass2.api.BaseApi
import cn.com.thinkwatch.ihass2.api.RestApi
import cn.com.thinkwatch.ihass2.api.hassRawApi
import cn.com.thinkwatch.ihass2.bus.*
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.enums.WidgetType
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.Location
import cn.com.thinkwatch.ihass2.model.broadcast.Channel
import cn.com.thinkwatch.ihass2.service.DataSyncService
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import cn.com.thinkwatch.ihass2.widget.DetailWidgetProvider
import cn.com.thinkwatch.ihass2.widget.RowWidgetProvider
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.dylan.common.application.Application
import com.dylan.common.rx.RxBus2
import com.facebook.stetho.Stetho
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yunsean.dynkotlins.extensions.nextOnMain
import com.yunsean.dynkotlins.extensions.readPref
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import org.jetbrains.anko.ctx
import org.json.JSONArray
import org.xutils.x
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

open class HassApplication: Application() {

    private var disposable: CompositeDisposable? = null
    override fun onCreate() {
        super.onCreate()
        x.Ext.init(this)
        application = this

        if (isMainProcess()) {
            migratePreference()
        }

        SDKInitializer.initialize(this);
        SDKInitializer.setCoordType(CoordType.GCJ02)

        val intent = Intent(ctx, DataSyncService::class.java)
        ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        Stetho.initialize(Stetho.newInitializerBuilder(this)
                .enableWebKitInspector(Stetho.defaultInspectorModulesProvider(this))
                .build())

        disposable = RxBus2.getDefault().register(ServiceRequest::class.java, {
            callService(it)
        }, RxBus2.getDefault().register(HassConfiged::class.java, {
            RestApi.jsonApi = null
            syncService?.hassChanged()
        }, RxBus2.getDefault().register(ConfigChanged::class.java, {
            syncService?.configChanged()
        }, disposable)))
    }

    private fun migratePreference() {
        if (cfg.get(HassConfig.Hass_HostUrl).isNullOrBlank() && !readPref("Hass_HostUrl").isNullOrBlank()) {
            cfg.set(HassConfig.Hass_HostUrl, readPref("Hass_HostUrl"))
            cfg.set(HassConfig.Hass_Password, readPref("Hass_Password"))
            cfg.set(HassConfig.Ui_PullRefresh, readPref("pullRefresh"))
            cfg.set(HassConfig.Ui_HomePanels, readPref("homePanels"))
            cfg.set(HassConfig.Gps_Logger, readPref("gps.Logger"))
            cfg.set(HassConfig.Gps_DeviceName, readPref("gps.deviceName"))
            cfg.set(HassConfig.Gps_DeviceId, readPref("Gps_DeviceId"))
            cfg.set(HassConfig.Gps_Password, readPref("gps.password"))
        }
    }
    private fun isMainProcess(): Boolean {
        val pid = android.os.Process.myPid()
        val manager = getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val process = manager.runningAppProcesses.find { it.pid == pid }
        return process?.processName == packageName
    }

    fun callService(request: ServiceRequest) {
        syncService?.let {
            if (it.isRunning && it.callService(request.domain, request.service, request)) {
                return
            }
        }
        RxBus2.getDefault().post(NetBusyEvent(true))
        BaseApi.api(app.haHostUrl, RestApi::class.java)
                .callService(app.haPassword, request.domain, request.service, request)
                .flatMap {
                    it.forEach { db.saveEntity(it) }
                    Observable.just(it)
                }
                .nextOnMain {
                    RxBus2.getDefault().post(NetBusyEvent(false))
                    it.forEach { RxBus2.getDefault().post(it) }
                }
                .error {
                    RxBus2.getDefault().post(NetBusyEvent(false))
                    RxBus2.getDefault().post(HassErrorEvent(it.message ?: "访问HA出现错误"))
                }
    }

    val location: Location?
    get() = syncService?.location
    fun requestLocation() {
        syncService?.requestLocation()
    }

    var refreshAt = 0L
    fun refreshState(onOver: ((String?)-> Unit)? = null) {
        if (haHostUrl.isBlank()) return onOver?.invoke("Hass尚未正确配置") ?: Unit
        if (System.currentTimeMillis() - refreshAt < 60_000) return onOver?.invoke(null) ?: Unit
        this.refreshAt = System.currentTimeMillis()
        hassRawApi.rawStates(app.haPassword)
                .flatMap {
                    val entities = JSONArray(it)
                    db.saveEntities(entities)
                    Observable.just(it)
                }
                .nextOnMain {
                    onOver?.invoke(null)
                    RxBus2.getDefault().post(EntityUpdated())
                    updateWidget()
                }
                .error {
                    it.printStackTrace()
                    onOver?.invoke(it.message ?: "未知错误")
                }
    }
    private fun updateWidget() {
        db.getWidgets().forEach {
            val entities = db.getWidgetEntity(it.widgetId)
            when (it.widgetType) {
                WidgetType.row-> RowWidgetProvider.updateEntityWidget(ctx, it.widgetId, entities)
                else-> if (entities.size > 0) DetailWidgetProvider.updateEntityWidget(ctx, it.widgetId, entities.get(0))
            }
        }
    }

    private var syncService: IDataSyncService? = null
    private var serviceBound: Boolean = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            try {
                syncService = IDataSyncService.Stub.asInterface(service)
                serviceBound = true
                syncService?.registerCallback(object: IDataSyncCallback.Stub() {
                    override fun onEntityChanged(entity: JsonEntity?) {
                        if (entity == null) return
                        RxBus2.getDefault().post(EntityChanged(entity))
                    }
                })
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            serviceBound = false
        }
    }

    val haHostUrl: String
        get() = cfg.get(HassConfig.Hass_HostUrl) ?: ""
    val haPassword: String
        get() = cfg.get(HassConfig.Hass_Password) ?: ""

    val xmlyChannels: Map<Int, Channel> by lazy {
        try {
            val stream = resources?.openRawResource(R.raw.xmly)
            val reader = BufferedReader(InputStreamReader(stream))
            var jsonStr = ""
            var line = reader.readLine()
            try {
                while (line != null) {
                    jsonStr += line
                    line = reader.readLine()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            val channels = Gson().fromJson<List<Channel>>(jsonStr, object: TypeToken<List<Channel>>(){}.type)
            channels.associateBy(Channel::id)
        } catch (_: Exception) {
            mapOf<Int, Channel>()
        }
    }

    companion object {
        lateinit var application: HassApplication
            private set
    }
}


inline val Context.app: HassApplication
    get() = HassApplication.application
inline val Fragment.app: HassApplication
    get() = HassApplication.application