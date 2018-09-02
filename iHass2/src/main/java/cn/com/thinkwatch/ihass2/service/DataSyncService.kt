package cn.com.thinkwatch.ihass2.service

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.RemoteException
import cn.com.thinkwatch.ihass2.aidl.IDataSyncCallback
import cn.com.thinkwatch.ihass2.aidl.IDataSyncService
import cn.com.thinkwatch.ihass2.api.BaseApi
import cn.com.thinkwatch.ihass2.api.RestApi
import cn.com.thinkwatch.ihass2.api.hassApi
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.bus.LocationChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.dto.StateChanged
import cn.com.thinkwatch.ihass2.enums.WidgetType
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.Location
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import cn.com.thinkwatch.ihass2.widget.DetailWidgetProvider
import cn.com.thinkwatch.ihass2.widget.RowWidgetProvider
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.dylan.common.data.GpsUtil
import com.dylan.common.rx.RxBus2
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.yunsean.dynkotlins.extensions.loges
import com.yunsean.dynkotlins.extensions.logis
import com.yunsean.dynkotlins.extensions.logws
import com.yunsean.dynkotlins.extensions.next
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.jetbrains.anko.ctx
import org.json.JSONObject
import java.util.*

class DataSyncService : Service() {

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY_COMPATIBILITY
    }

    data class WebSocketDto(val id: Int?,
                            val type: String,
                            val domain: String? = null,
                            val service: String? = null,
                            val event_type: String? = null,
                            val service_data: ServiceRequest? = null,
                            val api_password: String? = null)

    private var broadcastReceiver: BroadcastReceiver? = null
    private val binder = DataSyncServiceStub()
    inner class DataSyncServiceStub: IDataSyncService.Stub() {
        private val callbackList = RemoteCallbackList<IDataSyncCallback>()
        private var nextNum = 100
        private var webSocket: WebSocket? = null
        private var haPassword: String? = null
        private var haBaseUrl: String? = null
        private val allEntities = HashMap<String, JsonEntity>()
        private var connected = false
        private var latestSend: Long = System.currentTimeMillis()

        @JvmOverloads
        fun startWebSocket() {
            try {
                stopWebSocket()
                val hostUrl = app.haHostUrl
                if (hostUrl.isBlank()) return logws("Hass server did not configured.")
                haPassword = app.haPassword
                haBaseUrl = hostUrl.replace("http://", "ws://").replace("https://", "wss://") + "/api/websocket"
                connected = false
                logis("connect to websocket at ${haBaseUrl}")
                val request = Request.Builder().url(haBaseUrl!!).build()
                val listener = EchoWebSocketListener()
                val client = BaseApi.getWebSocketOkHttpClientInstance()
                webSocket = client.newWebSocket(request, listener)
            } catch (ex: Exception) {
                ex.printStackTrace()
                stopWebSocket()
            }
        }
        fun stopWebSocket() {
            try {
                webSocket?.cancel()
                webSocket = null
            } catch (_: Exception) {
            }
        }
        fun sendCommand(dto: WebSocketDto): Boolean {
            val json = gson.toJson(dto)
            val result = webSocket?.send(json) ?: false
            logis("${json}: ${result}")
            if (!result) startWebSocket()
            return result
        }
        val gson = GsonBuilder().create()
        fun processResponse(s: String?) {
            try {
                logis("received websocket message: ${s}")
                val response = JSONObject(s)
                val type = response.getString("type")
                when (type) {
                    "auth_required" -> {
                        sendCommand(WebSocketDto(null, "auth", api_password = haPassword))
                    }
                    "auth_ok" -> {
                        connected = true
                        sendCommand(WebSocketDto(++nextNum, "subscribe_events",  event_type = "state_changed"))
                        sendCommand(WebSocketDto(++nextNum, "get_states"))
                    }
                    "event" -> {
                        val event = Gson().fromJson<StateChanged>(s, StateChanged::class.java)
                        if (event != null) {
                            val newEntity = event.event?.data?.newState
                            val entityId = newEntity?.entityId
                            if (newEntity != null && entityId != null) {
                                db.saveEntity(newEntity)
                                allEntities.put(entityId, newEntity)
                                onEntityChanged(newEntity)
                                db.getWidgets(entityId).forEach {
                                    val entities = db.getWidgetEntity(it.widgetId)
                                    when (it.widgetType) {
                                        WidgetType.row-> RowWidgetProvider.updateEntityWidget(ctx, it.widgetId, entities)
                                        else-> if (entities.size > 0) DetailWidgetProvider.updateEntityWidget(ctx, it.widgetId, entities.get(0))
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private inner class EchoWebSocketListener : WebSocketListener() {
            private val NORMAL_CLOSURE_STATUS = 1000
            override fun onOpen(webSocket: WebSocket?, response: Response?) {
                latestSend = System.currentTimeMillis()
            }
            override fun onMessage(webSocket: WebSocket?, text: String?) {
                processResponse(text)
                latestSend = System.currentTimeMillis()
            }
            override fun onMessage(webSocket: WebSocket?, bytes: ByteString) {
                latestSend = System.currentTimeMillis()
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String?) {
                webSocket.close(NORMAL_CLOSURE_STATUS, null)
                stopWebSocket()
            }
            override fun onFailure(webSocket: WebSocket?, t: Throwable, response: Response?) {
                t.printStackTrace()
                stopWebSocket()
            }
        }

        override fun callService(domain: String, service: String, serviceRequest: ServiceRequest?): Boolean {
            try {
                return sendCommand(WebSocketDto(++nextNum, "call_service", domain = domain, service = service, service_data = serviceRequest))
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
        override fun registerCallback(cb: IDataSyncCallback) {
            callbackList.register(cb)
        }
        override fun unregisterCallback(cb: IDataSyncCallback) {
            callbackList.unregister(cb)
        }
        override fun isRunning(): Boolean {
            val running = webSocket != null && connected && (System.currentTimeMillis() - latestSend < 300_000)
            if (!running) startWebSocket()
            return running
        }
        override fun getLocation(): Location? {
            return location?.let { Location(it.latitude, it.longitude) }
        }

        override fun hassChanged() {
            cfg.reload()
            RestApi.jsonApi = null
            startWebSocket()
        }
        override fun configChanged() {
            cfg.reload()
            if (cfg.getBoolean(HassConfig.Gps_Logger)) {
                gpsDeviceId = cfg.getString(HassConfig.Gps_DeviceId)
                gpsPassword = cfg.getString(HassConfig.Gps_Password)
                requestLocation()
            } else {
                gpsDeviceId = ""
                gpsPassword = ""
                stopLocate()
            }
        }
        private var locationClient: LocationClient? = null
        private var location: BDLocation? = null
        override fun requestLocation() {
            try {
                logis("requestLocation()")
                val requestCode = locationClient?.requestLocation() ?: -1
                if (requestCode == 0 || requestCode == 6) return
                locationClient?.stop()
                locationClient = LocationClient(getApplicationContext())
                locationClient?.registerLocationListener(object : BDAbstractLocationListener() {
                    override fun onReceiveLocation(location: BDLocation?) {
                        location?.apply {
                            this@DataSyncServiceStub.location = this
                            RxBus2.getDefault().post(LocationChanged(latitude, longitude, radius, locationDescribe ?: ""))
                            uploadGps(latitude, longitude, radius, locationDescribe ?: "")
                            logis("located: gps=${latitude}, ${longitude}, radius=${radius}, type=${locationDescribe}")
                        }
                    }

                    override fun onLocDiagnosticMessage(p0: Int, p1: Int, p2: String?) {
                        logis(p2)
                    }
                })
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
                locationClient?.setLocOption(option)
                locationClient?.start()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        private fun onEntityChanged(entity: JsonEntity) {
            val count = callbackList.beginBroadcast()
            try {
                for (i in 0 until count) {
                    callbackList.getBroadcastItem(i).onEntityChanged(entity)
                }
            } catch (ex: RemoteException) {
                ex.printStackTrace()
            } finally {
            }
            callbackList.finishBroadcast()
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
        private fun uploadGps(latitude: Double, longitude: Double, radius: Float, coorType: String) {
            if (Math.abs(latitude - latestLatitude) < 0.00001 && Math.abs(longitude - latestLongitude) < 0.00001 && System.currentTimeMillis() - latestUploadGps < 60_000) return
            latestLongitude = longitude
            latestLatitude = latitude
            latestUploadGps = System.currentTimeMillis()
            if (gpsDeviceId.isBlank()) return
            val wgs = GpsUtil.GCJ2WGS(GpsUtil.LatLng(latitude, longitude))
            hassApi.gpsLogger(app.haPassword, wgs.latitude, wgs.longitude, gpsDeviceId, radius.toString(), coorType, gpsPassword)
                    .next {
                        logis("dylan", "update location failed")
                    }
        }
    }



    override fun onCreate() {
        super.onCreate()
        registerReceiver()
        registerBluetooth()
    }
    private lateinit var bluetoothScanCallback: ScanCallback
    private fun registerBluetooth() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
            return loges("Not found ble blue tooth")
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        val bluetoothAdapter = bluetoothManager?.getAdapter()
        if (bluetoothAdapter == null)
            return loges("Obtain blue tooth adapter failed")
        if (!bluetoothAdapter.isEnabled)
            return loges("Blue tooth is closed")
        bluetoothScanCallback = object: ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                logis("ble=${result?.device?.address}")
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                logis("ble count: ${results?.size ?: -1}")
                results?.forEach {
                    logis("bles=${it.device.address}->${it.device.name}")
                }
            }
            override fun onScanFailed(errorCode: Int) {
                loges("ble failed with ${errorCode}")
                super.onScanFailed(errorCode)
            }
        }
        bluetoothAdapter.bluetoothLeScanner.startScan(null, ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setReportDelay(60_000)
                .build(), bluetoothScanCallback)
    }
    private fun registerReceiver() {
        broadcastReceiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                val sb = StringBuilder()
                sb.append("action=${intent?.action}")
                intent?.extras?.apply {
                    keySet().forEach {
                        sb.append("  ${it}=${get(it)}")
                    }
                }
                logis(sb.toString())
                when (intent?.action) {
                    "android.intent.action.SCREEN_OFF"-> {
                        logis("TerminateConnectionReceiver")
                        binder.stopWebSocket()
                        binder.stopLocate()
                    }
                    "android.intent.action.SCREEN_ON",
                    "android.intent.action.USER_PRESENT"-> {
                        logis("ScreenOnReceiver")
                        context.app.refreshState()
                        binder.startWebSocket()
                        binder.requestLocation()
                    }
                    "android.net.conn.CONNECTIVITY_CHANGE",
                    "android.net.wifi.WIFI_STATE_CHANGED",
                    "android.net.wifi.STATE_CHANGE"-> {
                        logis("NetworkConnectChangedReceiver")
                        binder.requestLocation()
                    }
                    BluetoothAdapter.ACTION_STATE_CHANGED-> {
                    }
                    BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED-> {

                    }
                }
            }
        }
        val filter = IntentFilter()
        filter.addAction("android.intent.action.SCREEN_OFF")
        filter.addAction("android.intent.action.SCREEN_ON")
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE")
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(broadcastReceiver, filter)
        binder.requestLocation()
    }
    override fun onDestroy() {
        super.onDestroy()
        broadcastReceiver?.let { unregisterReceiver(it) }
        binder.stopWebSocket()
        loges("DataSyncService destoried")
        val localIntent = Intent()
        localIntent.setClass(this, DataSyncServiceStub()::class.java)
        this.startService(localIntent)
    }
}