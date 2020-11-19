package cn.com.thinkwatch.ihass2.service

import android.app.*
import android.app.Notification
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.*
import android.support.v4.app.NotificationCompat
import android.support.v4.app.TaskStackBuilder
import android.support.v4.content.ContextCompat
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.aidl.IDataSyncCallback
import cn.com.thinkwatch.ihass2.aidl.IDataSyncService
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.bus.AllowWakeupChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.enums.*
import cn.com.thinkwatch.ihass2.getMessage
import cn.com.thinkwatch.ihass2.model.*
import cn.com.thinkwatch.ihass2.network.base.Api
import cn.com.thinkwatch.ihass2.network.base.Websocket
import cn.com.thinkwatch.ihass2.network.base.api
import cn.com.thinkwatch.ihass2.network.base.ws
import cn.com.thinkwatch.ihass2.ui.EmptyActivity
import cn.com.thinkwatch.ihass2.ui.ObservedEditActivity
import cn.com.thinkwatch.ihass2.ui.TriggerHistoryActivity
import cn.com.thinkwatch.ihass2.ui.VoiceActivity
import cn.com.thinkwatch.ihass2.utils.*
import cn.com.thinkwatch.ihass2.voice.VoiceWindow
import cn.com.thinkwatch.ihass2.widget.DetailWidgetProvider
import cn.com.thinkwatch.ihass2.widget.RowWidgetProvider
import com.baidu.aip.asrwakeup3.core.wakeup.MyWakeup
import com.baidu.aip.asrwakeup3.core.wakeup.WakeUpResult
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.speech.EventListener
import com.baidu.speech.asr.SpeechConstant
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleScanCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.scan.BleScanRuleConfig
import com.dylan.common.data.GpsUtil
import com.dylan.common.rx.RxBus2
import com.dylan.common.utils.Utility
import com.google.gson.reflect.TypeToken
import com.yunsean.dynkotlins.extensions.*
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.internal.util.HalfSerializer.onNext
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.alarmManager
import org.jetbrains.anko.audioManager
import org.jetbrains.anko.ctx
import org.jetbrains.anko.dip
import org.json.JSONArray
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DataSyncService : Service() {

    override fun onBind(intent: Intent): IBinder? = binder
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY_COMPATIBILITY

    private fun callService(serviceId: String, content: String?) {
        val regex = Regex("(.*?)\\.(.*)")
        regex.find(serviceId)?.let {
            val domain = it.groupValues[1]
            val service = it.groupValues[2]
            api.callService(domain, service, content)
                    .subscribeOn(Schedulers.computation())
                    .next { binder.entityChanged(it, true, false) }
        }
    }

    private var eventNotificationChannelCreated = false
    private fun handleTrigger(trigger: EventTrigger) {
        val now = Date()
        if (!trigger.timeIsIn(now)) return
        callService(trigger.serviceId, trigger.content)
        db.addTriggerHistory(TriggerHistory(trigger.id, trigger.type, trigger.name, trigger.serviceId, now))
        if (trigger.notify) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !eventNotificationChannelCreated) {
                val importance = NotificationManager.IMPORTANCE_LOW
                val notificationChannel = NotificationChannel("APP_Event", "APP Event", importance)
                notificationManager.createNotificationChannel(notificationChannel)
                eventNotificationChannelCreated = true
            }
            val builder = NotificationCompat.Builder(ctx, NOTIFICATION_CHANNEL_TRIGGER)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("场景 ${trigger.name} 已执行")
                    .setContentText("执行于${now.kdateTime()}")
            val resultIntent = Intent(ctx, TriggerHistoryActivity::class.java)
            resultIntent.putExtra("keyword", trigger.name)
            resultIntent.putExtra("notifyId", trigger.id.toInt())
            val stackBuilder = TaskStackBuilder.create(ctx)
            stackBuilder.addParentStack(TriggerHistoryActivity::class.java)
            stackBuilder.addNextIntent(resultIntent)
            val resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
            builder.setContentIntent(resultPendingIntent)
            notificationManager.notify(trigger.id.toInt(), builder.build())
        }
    }
    private fun handleScreenOnTrigger() {
        db.getTrigger(TriggerType.screenOn, null)?.forEach { handleTrigger(it) }
    }
    private fun handleScreenOffTrigger() {
        db.getTrigger(TriggerType.screenOff, null)?.forEach { handleTrigger(it) }
    }
    private fun handleNfcTrigger(uid: String) {
        db.getTrigger(TriggerType.nfc, uid)?.forEach { handleTrigger(it) }
    }
    private fun handleBleTrigger(address: String, lose: Boolean) {
        db.getTrigger(if (lose) TriggerType.loseBluetooth else TriggerType.foundBluetooth, address)?.forEach { handleTrigger(it) }
    }
    private fun handleWifiTrigger(bssid: String, lose: Boolean) {
        db.getTrigger(if (lose) TriggerType.loseWifi else TriggerType.foundWifi, bssid)?.forEach { handleTrigger(it) }
    }
    private fun handleGpsTrigger(triggerId: Long, leave: Boolean) {
        db.getTrigger(triggerId)?.let {
            if (leave && it.type == TriggerType.leaveGps) handleTrigger(it)
            else if (!leave && it.type == TriggerType.enterGps) handleTrigger(it)
        }
    }

    private val observedEntities = hashMapOf<String, String?>()
    private fun loadObserved() {
        synchronized(observedEntities) {
            setupNotificationChannel()
            observedEntities.clear()
            db.getObserved().forEach {
                observedEntities.set(it.entityId, db.getEntity(it.entityId)?.state)
            }
        }
    }
    private var widgetEntityIds: Set<String>? = null
    private fun loadWidgets() {
        widgetEntityIds = db.getAllWidgetEntityIds()
    }
    private var notificationEntityIds: Set<String>? = null
    private fun loadNotification() {
        notificationEntityIds = db.getAllNotificationEntityIds()
    }

    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val timeFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZZZZZ", Locale.CHINA)
    private fun handleObserved(entity: JsonEntity) {
        val oldState = observedEntities.get(entity.entityId)
        if (oldState == entity.state) return
        synchronized(observedEntities) {
            observedEntities.put(entity.entityId, entity.state)
        }
        val lastUpdated = try { timeFormatter.parse(entity.lastChanged) } catch (ex: Exception) { null } ?: Date()
        db.getObserved(entity.entityId, false)?.forEach {
            if (!it.timeIsIn(lastUpdated)) return@forEach
            when (it.condition) {
                ConditionType.e-> if (entity.state != it.state) return@forEach
                ConditionType.ne-> if (oldState != it.state || entity.state == it.state) return@forEach
                ConditionType.gt-> if (entity.state?.toDoubleOrNull() ?: .0 <= it.state?.toDoubleOrNull() ?: .0) return@forEach
                ConditionType.gte-> if (entity.state?.toDoubleOrNull()?.toLong() ?: 0 < it.state?.toDoubleOrNull()?.toLong() ?: 0) return@forEach
                ConditionType.lt-> if (entity.state?.toDoubleOrNull() ?: .0 >= it.state?.toDoubleOrNull() ?: .0) return@forEach
                ConditionType.lte-> if (entity.state?.toDoubleOrNull()?.toLong() ?: 0 > it.state?.toDoubleOrNull()?.toLong() ?: 0) return@forEach
                ConditionType.inc-> if (entity.state?.toDoubleOrNull() ?: .0 <= oldState?.toDoubleOrNull() ?: .0 || (!it.state.isNullOrBlank() && entity.state?.toDoubleOrNull() ?: .0 < it.state?.toDoubleOrNull() ?: .0)) return@forEach
                ConditionType.dec-> if (entity.state?.toDoubleOrNull() ?: .0 >= oldState?.toDoubleOrNull() ?: .0 || (!it.state.isNullOrBlank() && entity.state?.toDoubleOrNull() ?: .0 > it.state?.toDoubleOrNull() ?: .0)) return@forEach
            }
            if (entity.attributes?.ihassTotal != null && oldState == entity.attributes?.ihassTotal) {
                val state = entity.attributes?.ihassTotal
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val extra = (entity.attributes?.ihassTotalExtra?.toIntOrNull() ?: 0) * 1000
                api.getHistory(calendar.kdateTime("yyyy-MM-dd'T'HH:mm:ssZZZZZ"), entity.entityId, null)
                        .flatMap {
                            Observable.just(Gsons.gson.fromJson<ArrayList<ArrayList<Period>>>(it, object: TypeToken<ArrayList<ArrayList<Period>>>(){}.type))
                        }
                        .subscribeOn(Schedulers.computation())
                        .next {his->
                            if (his.size > 0 && his.get(0).size > 0) {
                                val raws = his.get(0).sortedBy { it.lastChanged }
                                var total = 0L
                                raws.forEachIndexed { index, period ->
                                    if (index > 0 && raws.get(index - 1).state == state) total += (period.lastChanged?.time ?: 0) - (raws.get(index - 1).lastChanged?.time ?: 0) + extra
                                }
                                raws.lastOrNull()?.let {
                                    if (it.state == state) total += System.currentTimeMillis() - (it.lastChanged?.time ?: 0)
                                }
                                total /= 1000
                                val hour = total / 3600
                                total %= 3600
                                val minute = total / 60
                                total %= 60
                                val totalName = "${entity.getFriendlyState(state)}总时长"
                                val totalValue = String.format("%02d:%02d:%02d", hour, minute, total)
                                showObservedNotify(entity, it, lastUpdated, "${totalName}为${totalValue}")
                            } else {
                                showObservedNotify(entity, it, lastUpdated)
                            }
                        }.error {t->
                            showObservedNotify(entity, it, lastUpdated)
                        }
            } else {
                showObservedNotify(entity, it, lastUpdated)
            }
        }
    }
    private fun showObservedNotify(entity: JsonEntity, it: Observed, lastUpdated: Date, total: String? = null) {
        val pendingIntent = PendingIntent.getActivity(this, (System.currentTimeMillis() / 1000).toInt() + it.id.toInt(), Intent(this, EmptyActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra("entityId", entity.entityId)
                .putExtra("event", "observedClicked"), PendingIntent.FLAG_CANCEL_CURRENT)
        val icon = if (it.image >= 0 && it.image < ObservedEditActivity.ImageResIds.size) ObservedEditActivity.ImageResIds.get(it.image).second else R.mipmap.ic_launcher
        val content = if (total != null) "${entity.friendlyName} 状态变更为 ${entity.friendlyStateRow}， 今天$total" else "${entity.friendlyName} 状态变更为 ${entity.friendlyStateRow}"
        val notify = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_EVENT + it.id)
                .setSmallIcon(icon)
                .setTicker(lastUpdated.kdateTime())
                .setContentTitle(it.name)
                .setContentText(content)
                .setLights(Color.RED, 500, 500)
                .setContentIntent(pendingIntent).build()
        notify.flags = notify.flags or Notification.FLAG_AUTO_CANCEL
        if (it.vibrate != AlarmVibrateType.quiet) notify.vibrate = it.vibrate.vibrate
        if (it.sound != AlarmSoundType.quiet) notify.sound = Uri.parse("android.resource://" + getPackageName() + "/" + it.sound.resId)
        if (it.insistent) notify.flags = notify.flags or Notification.FLAG_INSISTENT
        if (Utility.isJellyBeanMR1OrLater()) notify.`when` = lastUpdated.time
        notificationManager.notify(it.id.toInt() + 1000, notify)
    }
    private var broadcastReceiver: BroadcastReceiver? = null
    private val binder = DataSyncServiceStub()
    inner class DataSyncServiceStub: IDataSyncService.Stub() {
        private val callbackList = RemoteCallbackList<IDataSyncCallback>()

        fun onCreate() {
            if (cfg.getBoolean(HassConfig.Gps_Logger) || cfg.getBoolean(HassConfig.Probe_Gps)) {
                gpsDeviceId = cfg.getString(HassConfig.Gps_DeviceId)
                gpsPassword = cfg.getString(HassConfig.Gps_Password)
                gpsWebHookId = cfg.getString(HassConfig.Gps_WebHookId)
                requestLocation()
            }
            startWebSocket()
        }

        override fun registerCallback(cb: IDataSyncCallback) {
            callbackList.register(cb)
        }
        override fun unregisterCallback(cb: IDataSyncCallback) {
            callbackList.unregister(cb)
        }

        fun entityChanged(entity: String?, array: Boolean, clear: Boolean) {
            if (entity == null) return
            val distincted = mutableMapOf<String, String?>()
            db.saveEntity(entity, array, {entityId, state, raw ->
                distincted.set(entityId, state)
            }, clear)
            var updateWidget = false
            var updateNotification = false
            distincted.forEach { (key, value) ->
                if (!updateWidget && (widgetEntityIds?.contains(key) ?: false)) updateWidget = true
                if (!updateNotification && (notificationEntityIds?.contains(key) ?: false)) updateNotification = true
                if (!clear) onEntityChanged(key)
                if (observedEntities.contains(key) && observedEntities.get(key) != value) db.getEntity(key)?.let { handleObserved(it) }
            }
            if (clear) onEntityUpdated()
            if (updateWidget) updateWidget()
            if (updateNotification) setupNotification()
        }
        fun startWebSocket() {
            api.entityChanged = { entity, array-> entityChanged(entity, array, false) }
            ws.entityChanged = { entity, array-> entityChanged(entity, array, false) }
            ws.start()
        }
        fun pingWebSocket() {
            ws.hearbeat()
        }
        fun stopWebSocket() {
            ws.stop()
        }

        override fun updateEntity(index: Long) = updateEntity2(index)
        private var enterUpdating = 0L
        private var latestRefreshAt = 0L
        @Synchronized fun updateEntity2(index: Long?) {
            if (System.currentTimeMillis() - enterUpdating < 60_000) return onCallResult(index, null, null)
            if (System.currentTimeMillis() - latestRefreshAt < 60_000) return onCallResult(index, null, null)
            enterUpdating = System.currentTimeMillis()
            api.readStates()
                    .subscribeOn(Schedulers.computation())
                    .nextOnMain {
                        try {
                            val entities = JSONArray(it)
                            db.saveEntities(entities, { entityId, state, raw ->
                                if (observedEntities.contains(entityId) && observedEntities.get(entityId) != state) {
                                    try {
                                        db.gson.fromJson<JsonEntity>(raw, JsonEntity::class.java)
                                    } catch (_: Exception) {
                                        null
                                    }?.let {
                                        handleObserved(it)
                                    }
                                }
                            }, true)
                            updateWidget()
                            setupNotification()
                            onEntityUpdated()
                            latestRefreshAt = System.currentTimeMillis()
                            onCallResult(index, null, null)
                        } catch (ex: Throwable) {
                            onCallResult(index, null, ex.getMessage() ?: "未知错误")
                        }
                    }
                    .error {
                        enterUpdating = 0L
                        onCallResult(index, null, it.getMessage() ?: "未知错误")
                    }
        }

        override fun callService(index: Long, domain: String, service: String, serviceRequest: ServiceRequest?) {
            callService2(index, domain, service, serviceRequest)
        }
        internal fun callService2(index: Long?, domain: String, service: String, serviceRequest: ServiceRequest?) {
            ws.callService(domain, service, serviceRequest).subscribeOn(Schedulers.computation()).next {
                entityChanged(it, true, false)
                onCallResult(index, it, null)
            }.error {
                onCallResult(index, null, it.getMessage() ?: "未知错误")
            }
        }
        override fun getService(index: Long) {
            api.getServices().subscribeOn(Schedulers.computation()).next {
                onCallResult(index, it, null)
            }.error {
                onCallResult(index, null, it.getMessage() ?: "未知错误")
            }
        }
        override fun getHistory(index: Long, timestamp: String?, entityId: String?, endTime: String?) {
            api.getHistory(timestamp, entityId, endTime).subscribeOn(Schedulers.computation()).next {
                onCallResult(index, it, null)
            }.error {
                onCallResult(index, null, it.getMessage() ?: "未知错误")
            }
        }

        override fun getLocation(): Location? = location?.let { Location(it.latitude, it.longitude) }
        override fun hassChanged() {
            stopWebSocket()
            cfg.reload()
            checkConnectByLan(true)
            Api.api = null
            Websocket.ws = null
            startWebSocket()
        }
        override fun configChanged() {
            cfg.reload()
            cancelAlarm()
            if (cfg.getBoolean(HassConfig.Gps_Logger) || cfg.getBoolean(HassConfig.Probe_Gps)) {
                gpsDeviceId = cfg.getString(HassConfig.Gps_DeviceId)
                gpsPassword = cfg.getString(HassConfig.Gps_Password)
                gpsWebHookId = cfg.getString(HassConfig.Gps_WebHookId)
                requestLocation()
                setupAlarm()
            } else {
                gpsDeviceId = ""
                gpsPassword = ""
                gpsWebHookId = ""
                stopLocate()
            }
            if (cfg.getBoolean(HassConfig.Probe_BluetoothBle)) startBleScan()
            else stopBleScan()
            if (cfg.getBoolean(HassConfig.Probe_Wifi)) startWifiScan()
            doubleHomeKeyToVoice = cfg.getBoolean(HassConfig.Speech_DoubleHomeKey)
            tripleHomeKeyToLock = cfg.getBoolean(HassConfig.Speech_TripleHomeKey)
            setupVoiceWakeup()
            setupNotification()
        }
        override fun triggerChanged() {
            loadTrigger()
        }
        override fun observedChanged() {
            loadObserved()
        }
        override fun widgetChanged() {
            loadWidgets()
        }
        override fun notificationChanged() {
            loadNotification()
            setupNotification()
        }
        private val locationOptions by lazy {
            val option = LocationClientOption()
            option.locationMode = LocationClientOption.LocationMode.Battery_Saving
            option.setCoorType("GCJ02")
            option.setScanSpan(180_000)
            option.isOpenGps = false
            option.isLocationNotify = false
            option.setIgnoreKillProcess(false)
            option.SetIgnoreCacheException(false)
            option.setWifiCacheTimeOut(300_000)
            option.setEnableSimulateGps(false)
            option.setIsNeedAltitude(true)
            option
        }
        private var locationClient: LocationClient? = null
        private var location: BDLocation? = null
        override fun requestLocation() {
            try {
                val requestCode = locationClient?.requestLocation() ?: -1
                if (requestCode == 0 || requestCode == 6) return
                locationClient?.stop()
                locationClient = LocationClient(getApplicationContext())
                locationClient?.registerLocationListener(object : BDAbstractLocationListener() {
                    override fun onReceiveLocation(location: BDLocation?) {
                        location?.apply {
                            this@DataSyncServiceStub.location = this
                            try {
                                val count = callbackList.beginBroadcast()
                                val loc = Location(latitude, longitude)
                                try {
                                    for (i in 0 until count) callbackList.getBroadcastItem(i).onLocationChanged(loc)
                                } catch (ex: RemoteException) {
                                    ex.printStackTrace()
                                }
                                callbackList.finishBroadcast()
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                            uploadGps(latitude, longitude, altitude, addrStr, radius, "gps", false)
                            gpsTrigger(latitude, longitude)
                        }
                    }

                    override fun onLocDiagnosticMessage(p0: Int, p1: Int, p2: String?) {
                        logis(p2)
                    }
                })
                locationClient?.setLocOption(locationOptions)
                locationClient?.start()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
        fun uploadInfo() {
            uploadGps(location?.latitude ?: .0, location?.longitude ?: .0, location?.altitude ?: .0, location?.addrStr, location?.radius ?: 0F, "gps", true)
        }
        override fun nfcTrigger(uid: String) {
            if (!cfg.getBoolean(HassConfig.Probe_NfcCard)) return
            handleNfcTrigger(uid)
        }
        fun onEntityChanged(entityId: String) {
            try {
                val count = callbackList.beginBroadcast()
                try {
                    for (i in 0 until count) callbackList.getBroadcastItem(i).onEntityChanged(entityId)
                } catch (ex: RemoteException) {
                    ex.printStackTrace()
                }
                callbackList.finishBroadcast()
            } catch (_:Exception) {
            }
        }
        fun onConnectChanged(isLocal: Boolean) {
            try {
                val count = callbackList.beginBroadcast()
                try {
                    for (i in 0 until count) callbackList.getBroadcastItem(i).onConnectChanged(isLocal)
                } catch (ex: RemoteException) {
                    ex.printStackTrace()
                }
                callbackList.finishBroadcast()
            } catch (_:Exception) {
            }
        }
        fun onCallResult(index: Long?, result: String?, reason: String?) {
            if (index == null) return
            try {
                val count = callbackList.beginBroadcast()
                try {
                    for (i in 0 until count) callbackList.getBroadcastItem(i).onCallResult(index, result, reason)
                } catch (ex: RemoteException) {
                    ex.printStackTrace()
                }
                callbackList.finishBroadcast()
            } catch (_:Exception) {
            }
        }
        fun onEntityUpdated() {
            try {
                val count = callbackList.beginBroadcast()
                try {
                    for (i in 0 until count) callbackList.getBroadcastItem(i).onEntityUpdated()
                } catch (ex: RemoteException) {
                    ex.printStackTrace()
                }
                callbackList.finishBroadcast()
            } catch (_: Exception) {
            }
        }
        fun updateWidget() {
            Observable.create<Boolean> {
                db.getWidgets().forEach {
                    val entities = db.getWidgetEntity(it.widgetId)
                    when (it.widgetType) {
                        WidgetType.row -> RowWidgetProvider.updateEntityWidget(ctx, it.widgetId, entities)
                        else -> if (entities.size > 0) DetailWidgetProvider.updateEntityWidget(ctx, it.widgetId, entities.get(0))
                    }
                }
            }.subscribeOn(Schedulers.computation()).next {  }
        }
        fun stopLocate() {
            locationClient?.stop()
            locationClient = null
        }

        private var latestLatitude = .0
        private var latestLongitude = .0
        private var latestUploadGps = 0L
        private var gpsDeviceId = ""
        private var gpsPassword = ""
        private var gpsWebHookId = ""
        @Synchronized private fun uploadGps(latitude: Double, longitude: Double, altitude: Double, address: String?, radius: Float, coorType: String, force: Boolean) {
            if (gpsDeviceId.isBlank()) return
            if (Math.abs(latitude - latestLatitude) < 0.0002 && Math.abs(longitude - latestLongitude) < 0.0002 && System.currentTimeMillis() - latestUploadGps < 60_000) return
            latestLongitude = longitude
            latestLatitude = latitude
            latestUploadGps = System.currentTimeMillis()
            val batteryChanged = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val wgs = GpsUtil.GCJ2WGS(GpsUtil.LatLng(latitude, longitude))
            val app: String? = if (cfg.getBoolean(HassConfig.Gps_AppLogger)) currentApp() else null
            if (gpsWebHookId.isNotBlank()) {
                api.gpsLogger2(gpsWebHookId, wgs.latitude, wgs.longitude, altitude, gpsDeviceId, radius.toString(), coorType, batteryLevel(batteryChanged), app)
                        .error {  }
            } else {
                val wifi = (if (connectiveManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)?.isConnected ?: false) wifiManager.connectionInfo?.ssid?.trim('"') else null) ?: "未连接"
                val isInteractive = powerManager.isInteractive
                api.gpsLogger(wgs.latitude, wgs.longitude, altitude, address, gpsDeviceId, radius.toString(), coorType, batteryLevel(batteryChanged) * 100F, batteryTemperature(batteryChanged),
                        isCharging(batteryChanged), isInteractive, wifi, app, if (gpsPassword.isBlank()) null else gpsPassword)
                        .error { }
            }
        }

        private fun batteryTemperature(batteryChanged: Intent): Float = try { batteryChanged.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1).toFloat() / 10 } catch (_: Exception) { -1F }
        private fun batteryLevel(batteryChanged: Intent): Float = try { batteryChanged.getIntExtra(BatteryManager.EXTRA_LEVEL, -1).toFloat() / batteryChanged.getIntExtra(BatteryManager.EXTRA_SCALE, -1) } catch (_: Exception) { -1F }
        private fun isCharging(batteryChanged: Intent): Boolean = try { batteryChanged.getIntExtra(BatteryManager.EXTRA_STATUS, -1) } catch (_: Exception) { BatteryManager.BATTERY_STATUS_UNKNOWN }.let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
        private fun currentApp(): String? {
            val packageName: String? = try {
                val end = System.currentTimeMillis()
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val events = usageStatsManager.queryEvents((end - 60 * 1000), end)
                if (null == events) return@currentApp null
                val usageEvent = UsageEvents.Event()
                var lastMoveToFGEvent: UsageEvents.Event? = null
                while (events.hasNextEvent()) {
                    events.getNextEvent(usageEvent)
                    if (usageEvent.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        lastMoveToFGEvent = usageEvent
                    }
                }
                lastMoveToFGEvent?.getPackageName()
            } catch (ex: Exception) {
                ex.printStackTrace()
                null
            }
            return try {
                packageName?.let { packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, PackageManager.GET_META_DATA)).toString() }
            } catch (ex: Exception) {
                ex.printStackTrace()
                packageName
            }
        }
    }

    private var messageReceiver: MessageReceiver? = null
    override fun onCreate() {
        super.onCreate()
        if (!TtsEngine.get().initialEnv(this, true)) toastex("初始化TTS失败")
        checkConnectByLan(false)
        binder.onCreate()
        setupNotificationChannel()
        loadTrigger()
        loadObserved()
        loadWidgets()
        loadNotification()
        registerReceiver()
        registerBluetooth()
        registerWifi()
        registerScreen()
        setupAlarm()
        setupHomeKey()
        setupNotification()
        setupRxBus()
        setupVoiceWakeup()
        messageReceiver = MessageReceiver(this)
    }

    private fun isWiredHeadsetOn(): Boolean {
        try {
            if (isBluetoothMicOn()) return true
            else if (Build.VERSION.SDK_INT >= 23) return audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).find { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_LINE_ANALOG } != null
            else return audioManager.isWiredHeadsetOn
        } catch (ex: Exception) {
            return false
        }
    }
    private fun isBluetoothMicOn(): Boolean {
        try {
            return BluetoothAdapter.getDefaultAdapter().getProfileConnectionState(android.bluetooth.BluetoothProfile.HEADSET) == android.bluetooth.BluetoothProfile.STATE_CONNECTED
        } catch (ex: Exception) {
            return false
        }
    }
    private var enableWakeup: Boolean? = null
    private fun setupVoiceWakeup() {
        if (enableWakeup != null && enableWakeup!!) return startupWakeup()
        else if (enableWakeup != null && !enableWakeup!!) return shutdownWakeup()
        val isInteractive = powerManager.isInteractive
        if (cfg.getBoolean(HassConfig.Speech_HeadsetWakeup) && isWiredHeadsetOn()) {
            startupWakeup()
        } else if (isInteractive) {
            val mode = cfg.getInt(HassConfig.Speech_ScreenOnMode)
            if (mode == HassConfig.Wakeup_Always) return startupWakeup()
            if (mode == HassConfig.Wakeup_Forbid) return shutdownWakeup()
            val needCharging = cfg.getBoolean(HassConfig.Speech_ScreenOnCharging)
            if (needCharging) {
                val isCharging = try { registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)).getIntExtra(BatteryManager.EXTRA_STATUS, -1) } catch (_: Exception) { BatteryManager.BATTERY_STATUS_UNKNOWN }.let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
                if (!isCharging) return shutdownWakeup()
            }
            val needWifi = cfg.getString(HassConfig.Speech_ScreenOnWifi)
            if (needWifi.isNotBlank()) {
                val wifi = (if (connectiveManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)?.isConnected ?: false) wifiManager.connectionInfo?.bssid?.trim('"') else null) ?: "未连接"
                if (needWifi.contains(wifi)) return startupWakeup()
            }
            val needBle = cfg.getString(HassConfig.Speech_ScreenOnBluetooth)
            if (needBle.isNotBlank()) {
                val connected = BluetoothUtils.getConnected(ctx, bluetoothAdapter, 2).map { it.address }
                val addresses = needBle.split(' ').filter { it.isNotBlank() }
                connected.forEach { if (addresses.contains(it)) return@setupVoiceWakeup startupWakeup() }
            }
            shutdownWakeup()
        } else {
            val mode = cfg.getInt(HassConfig.Speech_ScreenOffMode)
            if (mode == HassConfig.Wakeup_Always) return startupWakeup()
            if (mode == HassConfig.Wakeup_Forbid) return shutdownWakeup()
            val needCharging = cfg.getBoolean(HassConfig.Speech_ScreenOffCharging)
            if (needCharging) {
                val isCharging = try { registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)).getIntExtra(BatteryManager.EXTRA_STATUS, -1) } catch (_: Exception) { BatteryManager.BATTERY_STATUS_UNKNOWN }.let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
                if (!isCharging) return shutdownWakeup()
            }
            val needWifi = cfg.getString(HassConfig.Speech_ScreenOffWifi)
            if (needWifi.isNotBlank()) {
                val wifi = (if (connectiveManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)?.isConnected ?: false) wifiManager.connectionInfo?.bssid?.trim('"') else null) ?: "未连接"
                if (needWifi.contains(wifi)) return startupWakeup()
            }
            val needBle = cfg.getString(HassConfig.Speech_ScreenOffBluetooth)
            if (needBle.isNotBlank()) {
                val connected = BluetoothUtils.getConnected(ctx, bluetoothAdapter, 2).map { it.address }
                val addresses = needBle.split(' ').filter { it.isNotBlank() }
                connected.forEach { if (addresses.contains(it)) return@setupVoiceWakeup startupWakeup() }
            }
            shutdownWakeup()
        }
    }
    private var myWakeup: MyWakeup? = null
    private var wakeupWorking = false
    set(value) {
        field = value
        setupNotification()
    }
    private val wakeLock by lazy {
        val lock = powerManager.newWakeLock(PowerManager.ON_AFTER_RELEASE or PowerManager.PARTIAL_WAKE_LOCK, "wakeup")
        lock.setReferenceCounted(false)
        lock
    }
    private var latestAudioDevice = false
    private @Synchronized fun startupWakeup() {
        try {
            if (wakeupWorking && latestAudioDevice == isWiredHeadsetOn()) return
            latestAudioDevice = isWiredHeadsetOn()
            if (!cfg.getBoolean(HassConfig.Speech_NoWakeupLock)) try { wakeLock.acquire() } catch (_: Throwable) {}
            try { myWakeup?.release() } catch (_: Throwable) {}
            myWakeup = MyWakeup(this, object: EventListener {
                private fun onSuccess(word: String?, result: WakeUpResult?) {
                    if (VoiceWindow.instant.isRecognizing()) return
                    powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "wakeup").acquire(10_000)
                    val noWait = word != "你好贝壳"
                    if (powerManager.isInteractive) {
                        if (!noWait) MediaPlayer.create(ctx, Wakeup_Feedback[(System.currentTimeMillis() % Wakeup_Feedback.size).toInt()]).start()
                        VoiceWindow.instant.show(noWait, null, false)
                    } else {
                        VoiceWindow.instant.show(noWait, null, true)
                    }
                }
                private fun onASrAudio(data: ByteArray?, offset: Int, length: Int) = Unit
                private fun onStop() {
                    wakeupWorking = false
                }
                private fun onError(errorCode: Int, errorMessge: String?, result: WakeUpResult?) {
                    wakeupWorking = false
                    toastex(if (errorMessge.isNullOrBlank()) result?.desc else errorMessge)
                }
                override fun onEvent(name: String?, params: String?, data: ByteArray?, offset: Int, length: Int) {
                    if (SpeechConstant.CALLBACK_EVENT_WAKEUP_SUCCESS == name) {
                        val result = WakeUpResult.parseJson(name, params)
                        if (result.hasError()) onError(result.errorCode, "", result)
                        else onSuccess(result.word, result)
                    } else if (SpeechConstant.CALLBACK_EVENT_WAKEUP_ERROR == name) {
                        val result = WakeUpResult.parseJson(name, params)
                        val errorCode = result.errorCode
                        if (result.hasError()) onError(errorCode, "", result)
                    } else if (SpeechConstant.CALLBACK_EVENT_WAKEUP_STOPED == name) {
                        onStop()
                    } else if (SpeechConstant.CALLBACK_EVENT_WAKEUP_AUDIO == name) {
                        onASrAudio(data, offset, length)
                    }
                }
            })
            val params = mapOf(SpeechConstant.WP_WORDS_FILE to "assets:///WakeUp.bin",
                    SpeechConstant.IN_FILE to "#com.baidu.aip.asrwakeup3.core.inputstream.MyMicrophoneInputStream.getInstance()")
            myWakeup?.start(params)
            wakeupWorking = true
        } catch (ex: Throwable) {
            ex.printStackTrace()
            Observable.timer(10, TimeUnit.SECONDS).next { startupWakeup() }
        }
    }
    private fun shutdownWakeup() {
        wakeupWorking = false
        try { wakeLock.release() } catch (_: Throwable) {}
        try { myWakeup?.release() } catch (_: Throwable) {}
    }

    private var disposable: CompositeDisposable? = null
    private fun setupRxBus() {
        disposable = RxBus2.getDefault().register(AllowWakeupChanged::class.java, {
            if (it.allow) startupWakeup()
            else shutdownWakeup()
            setupNotification()
        }, disposable)
    }
    private fun setupNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel(NOTIFICATION_CHANNEL_APP, "应用快捷操作", NotificationManager.IMPORTANCE_DEFAULT).let {
                notificationManager.createNotificationChannel(it)
            }
            NotificationChannel(NOTIFICATION_CHANNEL_TRIGGER, "情境触发通知", NotificationManager.IMPORTANCE_DEFAULT).let {
                notificationManager.createNotificationChannel(it)
            }
            NotificationChannelGroup(NOTIFICATION_CHANNEL_EVENT, "状态变更通知").let {
                notificationManager.createNotificationChannelGroup(it)
            }
            db.getObserved().forEach {
                NotificationChannel(NOTIFICATION_CHANNEL_EVENT + it.id, it.name, NotificationManager.IMPORTANCE_HIGH).let { channel ->
                    channel.enableVibration(true)
                    channel.enableLights(true)
                    channel.group = NOTIFICATION_CHANNEL_EVENT
                    if (it.vibrate != AlarmVibrateType.quiet) channel.vibrationPattern = it.vibrate.vibrate
                    if (it.sound != AlarmSoundType.quiet) channel.setSound(Uri.parse("android.resource://" + getPackageName() + "/" + it.sound.resId), null)
                    notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_EVENT + it.id)
                    notificationManager.createNotificationChannel(channel)
                }
            }
        }
    }

    private fun setupHomeKey() {
        doubleHomeKeyToVoice = cfg.getBoolean(HassConfig.Speech_DoubleHomeKey)
        tripleHomeKeyToLock = cfg.getBoolean(HassConfig.Speech_TripleHomeKey)
    }

    private val powerManager by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }
    private fun registerScreen() {
        val isOpen = powerManager.isInteractive()
        if (isOpen) handleScreenOnTrigger()
        else handleScreenOffTrigger()
    }

    private val alarmIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(applicationContext, 100, Intent("cn.com.thinkwatch.ihass2.Alarm"), PendingIntent.FLAG_UPDATE_CURRENT)
    }
    private fun setupAlarm() {
        alarmIntent.let { alarmManager.cancel(it) }
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 300_000, 300_000, alarmIntent)
    }
    private fun cancelAlarm() {
        alarmIntent.let { alarmManager.cancel(it) }
    }

    private data class WifiObserable(val bssid: String,
                                     var isActivited: Boolean? = null)
    private data class GpsObserable(val latitude: Double,
                                    val longitude: Double,
                                    val radius: Int,
                                    val triggerId: Long,
                                    var isActivited: Boolean? = null)
    private data class BleObserable(val address: String,
                                    var isActivited: Boolean? = null)
    private val wifiObserables = mutableListOf<WifiObserable>()
    private val bleObserables = mutableListOf<BleObserable>()
    private val gpsObserables = mutableListOf<GpsObserable>()
    private fun loadTrigger() {
        wifiObserables.clear()
        synchronized(bleObserables) { bleObserables.clear() }
        gpsObserables.clear()
        db.getTriggers().forEach { e->
            when(e.type) {
                TriggerType.foundWifi, TriggerType.loseWifi-> {
                    if (wifiObserables.find { it.bssid == e.params } == null) wifiObserables.add(WifiObserable(e.params))
                }
                TriggerType.enterGps, TriggerType.leaveGps-> {
                    val regex = Regex("([-+]?[0-9]*\\.?[0-9]+),([-+]?[0-9]*\\.?[0-9]+):([0-9]*)")
                    regex.find(e.params)?.let {
                        gpsObserables.add(GpsObserable(it.groupValues[1].toDoubleOrNull() ?: .0, it.groupValues[2].toDoubleOrNull() ?: .0, it.groupValues[3].toIntOrNull() ?: 100, e.id))
                    }
                }
                TriggerType.foundBluetooth, TriggerType.loseBluetooth-> {
                    synchronized(bleObserables) {
                        if (bleObserables.find { it.address == e.params } == null) bleObserables.add(BleObserable(e.params))
                    }
                }
                TriggerType.nfc-> {
                }
            }
        }
    }
    private fun bluetoothTrigger(results: List<String>) {
        if (!cfg.getBoolean(HassConfig.Probe_BluetoothBle)) return
        synchronized(bleObserables) {
            results.forEach { ble ->
                bleObserables.find { it.address == ble }?.let {
                    if (it.isActivited == null) {
                        it.isActivited = true
                    } else if (!it.isActivited!!) {
                        it.isActivited = true
                        handleBleTrigger(ble, false)
                    }
                }
            }
            bleObserables.forEach {
                if (it.isActivited == null) {
                    it.isActivited = false
                } else if (it.isActivited!! && !results.contains(it.address)) {
                    it.isActivited = false
                    handleBleTrigger(it.address, true)
                }
            }
        }
    }
    private fun wifiTrigger(results: List<android.net.wifi.ScanResult>) {
        if (!cfg.getBoolean(HassConfig.Probe_Wifi)) return
        wifiObserables.forEach {
            if (results.find {t-> t.BSSID == it.bssid } != null) {
                if (it.isActivited == null) {
                    it.isActivited = true
                } else if (!it.isActivited!!) {
                    it.isActivited = true
                    handleWifiTrigger(it.bssid, false)
                }
            } else {
                if (it.isActivited == null) {
                    it.isActivited = false
                } else if (it.isActivited!!) {
                    it.isActivited = false
                    handleWifiTrigger(it.bssid, true)
                }
            }
        }
    }
    private var latestGpsMeasured = 0L
    private fun gpsTrigger(latitude: Double, longitude: Double) {
        if (!cfg.getBoolean(HassConfig.Probe_Gps)) return
        if (System.currentTimeMillis() - latestGpsMeasured < 10_000) return
        latestGpsMeasured = System.currentTimeMillis()
        val distance = FloatArray(1)
        gpsObserables.forEach {
            android.location.Location.distanceBetween(latitude, longitude, it.latitude, it.longitude, distance)
            if (distance[0] < it.radius) {
                if (it.isActivited == null) {
                    it.isActivited = true
                } else if (!it.isActivited!!) {
                    it.isActivited = true
                    handleGpsTrigger(it.triggerId, false)
                }
            } else {
                if (it.isActivited == null) {
                    it.isActivited = false
                } else if (it.isActivited!!) {
                    it.isActivited = false
                    handleGpsTrigger(it.triggerId, true)
                }
            }
        }
    }

    private val wifiManager by lazy {  applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    private val connectiveManager by lazy { applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private fun registerWifi() {
        if (wifiManager.isWifiEnabled) logws("Wifi is closed.")
        if (cfg.getBoolean(HassConfig.Probe_Wifi)) wifiManager.startScan()
    }
    private fun registerBluetooth() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) return loges("Not found ble blue tooth")
        BleManager.getInstance().init(getApplication())
        BleManager.getInstance()
                .enableLog(true)
                .setReConnectCount(1, 5000)
                .setConnectOverTime(20000)
                .setOperateTimeout(5000)
        BleManager.getInstance().initScanRule(BleScanRuleConfig.Builder()
                .setServiceUuids(null)
                .setDeviceMac(null)
                .setAutoConnect(false)
                .setScanTimeOut(10000)
                .build())
        if (cfg.getBoolean(HassConfig.Probe_BluetoothBle)) startBleScan()
    }
    private var latestScanAt = 0L
    private @Synchronized fun startBleScan(force: Boolean = false) {
        if (!bluetoothAdapter.isEnabled()) return toastex("蓝牙未打开，无法进行蓝牙场景扫描！")
        if (!force && System.currentTimeMillis() - latestScanAt < 180_000) return
        latestScanAt = System.currentTimeMillis()
        BleManager.getInstance().scan(object: BleScanCallback() {
            override fun onScanStarted(success: Boolean) = Unit
            override fun onScanning(bleDevice: BleDevice?) = Unit
            override fun onScanFinished(scanResultList: MutableList<BleDevice>?) {
                val addresses = mutableListOf<String>()
                try { BluetoothUtils.getConnected(ctx, bluetoothAdapter)
                            .timeout(2, TimeUnit.SECONDS).forEach { addresses.addAll(it.map { it.address }) }
                } catch (_: Exception) {  }
                scanResultList?.forEach { addresses.add(it.device.address) }
                bluetoothTrigger(addresses)
            }
        })
    }
    private fun stopBleScan() {
        try { BleManager.getInstance().cancelScan() } catch (_: Exception) {}
    }
    private val bluetoothManager by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }
    private var latestScanWifi = 0L
    private fun startWifiScan() {
        if (System.currentTimeMillis() - latestScanWifi < 30_000) return
        latestScanWifi = System.currentTimeMillis()
        wifiManager.startScan()
    }
    private fun registerReceiver() {
        broadcastReceiver?.let { unregisterReceiver(it) }
        broadcastReceiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                db.async {
                    when (intent?.action) {
                        "android.intent.action.SCREEN_OFF"-> {
                            VoiceWindow.dismiss()
                            setupVoiceWakeup()
                            handleScreenOffTrigger()
                            binder.uploadInfo()
                            if (cfg.getBoolean(HassConfig.Gps_Logger)) stopBleScan()
                            if (!cfg.getBoolean(HassConfig.Connect_ScreenOff)) binder.stopWebSocket()
                        }
                        "android.intent.action.SCREEN_ON"-> {
                            setupVoiceWakeup()
                            binder.startWebSocket()
                            if (cfg.getBoolean(HassConfig.Probe_Wifi)) wifiManager.startScan()
                            if (cfg.getBoolean(HassConfig.Probe_BluetoothBle)) startBleScan(true)
                            if (cfg.getBoolean(HassConfig.Gps_Logger) || cfg.getBoolean(HassConfig.Probe_Gps)) {
                                setupAlarm()
                                binder.requestLocation()
                            }
                            handleScreenOnTrigger()
                            binder.updateEntity2(null)
                            wifiConnected = connectiveManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected
                            setupNotification()
                        }
                        WifiManager.NETWORK_STATE_CHANGED_ACTION-> {
                            setupVoiceWakeup()
                            intent.getParcelableExtra<NetworkInfo?>(WifiManager.EXTRA_NETWORK_INFO)?.let { wifiConnected = it.isConnected }
                        }
                        WifiManager.SCAN_RESULTS_AVAILABLE_ACTION-> {
                            wifiManager.scanResults?.let { wifiTrigger(it) }
                        }
                        BluetoothAdapter.ACTION_STATE_CHANGED,
                        BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED-> {
                            setupVoiceWakeup()
                            if (cfg.getBoolean(HassConfig.Probe_BluetoothBle)) startBleScan(true)
                        }
                        BluetoothDevice.ACTION_ACL_CONNECTED,
                        BluetoothDevice.ACTION_ACL_DISCONNECTED,
                        Intent.ACTION_POWER_CONNECTED,
                        Intent.ACTION_POWER_DISCONNECTED-> {
                            setupVoiceWakeup()
                        }
                        Intent.ACTION_HEADSET_PLUG-> {
                            setupVoiceWakeup()
                        }
                    }
                    true
                }.subscribeOn(Schedulers.computation()).next {
                }
            }
        }
        val filter = IntentFilter()
        filter.priority = IntentFilter.SYSTEM_HIGH_PRIORITY - 9
        filter.addAction("android.intent.action.SCREEN_OFF")
        filter.addAction("android.intent.action.SCREEN_ON")
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        filter.addAction(Intent.ACTION_POWER_CONNECTED)
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        filter.addAction(Intent.ACTION_HEADSET_PLUG)
        registerReceiver(broadcastReceiver, filter)

        registerReceiver(object: BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                db.async {
                    if (cfg.getBoolean(HassConfig.Gps_Logger) || cfg.getBoolean(HassConfig.Probe_Gps)) {
                        binder.requestLocation()
                    }
                    if (cfg.getBoolean(HassConfig.Probe_BluetoothBle)) {
                        startBleScan()
                    }
                }.subscribeOn(Schedulers.computation()).next {
                }
            }
        }, IntentFilter("cn.com.thinkwatch.ihass2.Alarm"))

        registerReceiver(object: BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                val reason = p1?.getStringExtra(SYSTEM_DIALOG_REASON_KEY)
                if (reason == SYSTEM_DIALOG_REASON_HOME_KEY) handleHomeKey()
            }
        }, IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))

        registerReceiver(object: BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                if (!VoiceWindow.instant.isRecognizing()) VoiceWindow.instant.show()
                collapseStatusBar()
            }
        }, IntentFilter("cn.com.thinkwatch.ihass2.Voice"))

        registerReceiver(object: BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                if (enableWakeup != null && enableWakeup!!) enableWakeup = false
                else if (enableWakeup != null && !enableWakeup!!) enableWakeup = null
                else enableWakeup = true
                setupVoiceWakeup()
                setupNotification()
            }
        }, IntentFilter("cn.com.thinkwatch.ihass2.Wakeup"))
    }
    override fun onDestroy() {
        super.onDestroy()
        try { myWakeup?.release() } catch (_: Exception) {}
        disposable?.dispose()
        broadcastReceiver?.let { unregisterReceiver(it) }
        binder.stopWebSocket()
        val localIntent = Intent()
        localIntent.setClass(this, DataSyncServiceStub()::class.java)
        this.startService(localIntent)
    }

    private fun setupNotification() {
        val entities = db.readNotification()
        if (entities.isEmpty()) return
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_APP)
        val parentView = RemoteViews(packageName, R.layout.notification_row)
        parentView.removeAllViews(R.id.contentView)
        if (!cfg.getBoolean(HassConfig.Speech_Notification)) {
            parentView.setViewVisibility(R.id.voice, View.GONE)
            parentView.setViewVisibility(R.id.divider, View.GONE)
        } else {
            parentView.setViewVisibility(R.id.voice, View.VISIBLE)
            parentView.setViewVisibility(R.id.divider, View.VISIBLE)
            parentView.setOnClickPendingIntent(R.id.voice, PendingIntent.getBroadcast(ctx, 100, Intent("cn.com.thinkwatch.ihass2.Voice"), 0))
        }
        if (!cfg.getBoolean(HassConfig.Speech_ShowWakeup)) {
            parentView.setViewVisibility(R.id.wakeup, View.GONE)
            parentView.setViewVisibility(R.id.divider2, View.GONE)
        } else {
            val icon = if (enableWakeup != null && enableWakeup!!) R.drawable.icon_wakeup
                else if (enableWakeup != null && !enableWakeup!!) R.drawable.icon_wakeup_disabled
                else if (wakeupWorking) R.drawable.icon_wakeup_if_enable
                else R.drawable.icon_wakeup_if_disabled
            parentView.setViewVisibility(R.id.wakeup, View.VISIBLE)
            parentView.setViewVisibility(R.id.divider2, View.VISIBLE)
            parentView.setImageViewResource(R.id.wakeup, icon)
            parentView.setOnClickPendingIntent(R.id.wakeup, PendingIntent.getBroadcast(ctx, 101, Intent("cn.com.thinkwatch.ihass2.Wakeup"), 0))
        }
        entities.forEachIndexed { index, entity->
            if (index >= RowWidgetProvider.actions.size) return@forEachIndexed
            val remoteView = RemoteViews(packageName, R.layout.listitem_notification)
            val iconColor = if (entity.isActivated) ContextCompat.getColor(ctx, R.color.xiaomiPrimaryTextSelected) else 0xffaaaaaa.toInt()
            val iconText = if (entity.isSensor && (entity.attributes?.unitOfMeasurement != null || entity.isDigitalState)) entity.state else if (entity.showIcon.isNullOrBlank()) entity.iconState else entity.showIcon
            remoteView.setOnClickPendingIntent(R.id.itemView, PendingIntent.getActivity(ctx, 200 + index, Intent(ctx, EmptyActivity::class.java)
                    .setAction(RowWidgetProvider.actions.get(index))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra("entityId", entity.entityId)
                    .putExtra("event", "widgetClicked"), PendingIntent.FLAG_CANCEL_CURRENT))
            remoteView.setTextViewText(R.id.name, if (entity.showName.isNullOrBlank()) entity.friendlyName else entity.showName)
            remoteView.setImageViewBitmap(R.id.icon, MDIFont.get().drawIcon(ctx, iconText, iconColor, ctx.dip(if (iconText?.startsWith("mdi:") ?: false) 24 else 14), ctx.dip(24)))
            remoteView.setTextViewTextSize(R.id.name, TypedValue.COMPLEX_UNIT_SP, 10F)
            remoteView.setTextColor(R.id.name, iconColor)
            parentView.addView(R.id.contentView, remoteView)
        }
        builder.setSmallIcon(R.drawable.shell8)
                .setDefaults(0)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContent(parentView)
        startForeground(-1, builder.build())
    }

    private fun saveLatestApp() {
        latestUsageEvent = try {
                val end = System.currentTimeMillis()
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val events = usageStatsManager.queryEvents((end - 3000), end)
                if (null != events) {
                    val usageEvent = UsageEvents.Event()
                    var lastMoveToFGEvent: UsageEvents.Event? = null
                    while (events.hasNextEvent()) {
                        events.getNextEvent(usageEvent)
                        if (usageEvent.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                            lastMoveToFGEvent = usageEvent
                        }
                    }
                    lastMoveToFGEvent
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
    }
    private fun restoreLatestApp() {
        latestUsageEvent?.let {
            try {
                val intent = Intent()
                intent.setClassName(it.packageName, it.className)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
                pendingIntent.send()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
        latestUsageEvent = null
    }

    private var doubleHomeKeyToVoice = false
    private var tripleHomeKeyToLock = false
    private var latestPressedHomeKey = 0L
    private var countPressedHomeKey = 0
    private val pressedHomeHandler = Handler()
    private val pressedHomeTask = Runnable { handleHomeKey(countPressedHomeKey) }
    private var latestUsageEvent: UsageEvents.Event? = null
    private fun handleHomeKey(clickedCount: Int) {
        try {
            latestPressedHomeKey = 0
            if (clickedCount == 2 && doubleHomeKeyToVoice) {
                if (!VoiceWindow.instant.isRecognizing()) VoiceWindow.instant.show(false, latestUsageEvent)
                latestUsageEvent = null
            } else if (clickedCount >= 2 && tripleHomeKeyToLock) {
                restoreLatestApp()
                (getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).lockNow()
            } else if (clickedCount > 10) {
                val intent = Intent(this, VoiceActivity::class.java)
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
                pendingIntent.send()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
    private fun handleHomeKey() {
        if (System.currentTimeMillis() - latestPressedHomeKey > 500) countPressedHomeKey = 0
        countPressedHomeKey++
        latestPressedHomeKey = System.currentTimeMillis()
        pressedHomeHandler.removeCallbacks(pressedHomeTask)
        if (countPressedHomeKey > 2) handleHomeKey(countPressedHomeKey)
        else if (countPressedHomeKey > 1 && tripleHomeKeyToLock && doubleHomeKeyToVoice) pressedHomeHandler.postDelayed(pressedHomeTask, 500)
        else if (countPressedHomeKey > 1) handleHomeKey(countPressedHomeKey)
        else {
            saveLatestApp()
            VoiceWindow.dismiss()
        }
    }

    fun checkConnectByLan(reset: Boolean) {
        val byLan = if (cfg.getString(HassConfig.Hass_LocalUrl).isNullOrBlank() || cfg.getString(HassConfig.Hass_LocalBssid).isNullOrBlank()) false
        else (wifiManager.getConnectionInfo()?.bssid?.let { cfg.get(HassConfig.Hass_LocalBssid)?.contains(it) } ?: false)
        if (app.isConnectByLan != byLan) {
            app.isConnectByLan = byLan
            if (reset) Observable.timer(1, TimeUnit.SECONDS).next {
                Api.api = null
                Websocket.ws = null
                binder.startWebSocket()
                binder.onConnectChanged(byLan)
            }
        }
    }
    private fun collapseStatusBar() {
        try {
            val statusBarManager = getSystemService("statusbar")
            val collapse: Method = if (Build.VERSION.SDK_INT <= 16) statusBarManager.javaClass.getMethod("collapse")
            else statusBarManager.javaClass.getMethod("collapsePanels")
            collapse.invoke(statusBarManager);
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
    private var wifiConnected = false
        set(value) {
            if (value != field) checkConnectByLan(true)
            field = value
        }

    companion object {
        private val SYSTEM_DIALOG_REASON_KEY = "reason"
        private val SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps"
        private val SYSTEM_DIALOG_REASON_HOME_KEY = "homekey"
        private val SYSTEM_DIALOG_REASON_LOCK = "lock"
        private val SYSTEM_DIALOG_REASON_ASSIST = "assist"
        private val NOTIFICATION_CHANNEL_APP = "notifyApp"
        private val NOTIFICATION_CHANNEL_EVENT= "notifyEvent"
        private val NOTIFICATION_CHANNEL_TRIGGER = "notifyTrigger"

        private val Wakeup_Feedback = arrayOf(R.raw.itshere, R.raw.wozai)
    }
}