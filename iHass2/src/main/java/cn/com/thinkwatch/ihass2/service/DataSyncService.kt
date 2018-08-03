package cn.com.thinkwatch.ihass2.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import cn.com.thinkwatch.ihass2.api.BaseApi
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.bus.EntityChanged
import cn.com.thinkwatch.ihass2.bus.HassConfiged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.dto.StateChanged
import cn.com.thinkwatch.ihass2.model.JsonEntity
import com.dylan.common.rx.RxBus2
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.yunsean.dynkotlins.extensions.logis
import io.reactivex.disposables.Disposable
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.*



class DataSyncService : Service() {
    private val localBinder = LocalBinder()
    private var nextNum = 100
    private var webSocket: WebSocket? = null
    private var haPassword: String? = null
    private var haBaseUrl: String? = null
    private val allEntities = HashMap<String, JsonEntity>()
    private var connected = false
    private var latestSend: Long = System.currentTimeMillis()
    private var screenOffReceiver: ScreenOffReceiver? = null
    private var screenOnReceiver: ScreenOnReceiver? = null

    val isWebSocketRunning: Boolean
        get() {
            val running = webSocket != null && connected && (System.currentTimeMillis() - latestSend < 300_000)
            if (!running) startWebSocket()
            return running
        }
    inner class LocalBinder : Binder() {
        val service: DataSyncService
            get() = this@DataSyncService
    }
    override fun onBind(intent: Intent): IBinder? {
        return localBinder
    }
    override fun onUnbind(intent: Intent): Boolean {
        stopWebSocket()
        return super.onUnbind(intent)
    }

    @JvmOverloads
    fun startWebSocket() {
        try {
            stopWebSocket()
            val hostUrl = app.haHostUrl
            if (hostUrl.isBlank()) return
            haPassword = app.haPassword
            haBaseUrl = hostUrl.replace("http://", "ws://").replace("https://", "wss://") + "/api/websocket"
            connected = false
            if (webSocket == null) {
                val request = Request.Builder().url(haBaseUrl!!).build()
                val listener = EchoWebSocketListener()
                val client = BaseApi.getWebSocketOkHttpClientInstance()
                webSocket = client.newWebSocket(request, listener)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            stopWebSocket()
        }
    }
    fun stopWebSocket() {
        webSocket?.cancel()
        webSocket = null
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
    private var disposable: Disposable? = null
    override fun onCreate() {
        super.onCreate()
        mInstance = this
        screenOffReceiver = ScreenOffReceiver()
        screenOffReceiver?.let {
            val filter = IntentFilter()
            filter.addAction("android.intent.action.SCREEN_OFF")
            registerReceiver(it, filter)
        }
        screenOnReceiver = ScreenOnReceiver()
        screenOnReceiver?.let {
            val filter = IntentFilter()
            filter.addAction("android.intent.action.SCREEN_ON")
            filter.addAction("android.intent.action.USER_PRESENT")
            registerReceiver(it, filter)
        }
        disposable = RxBus2.getDefault().register(HassConfiged::class.java) {
            startWebSocket()
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        disposable?.dispose()
        webSocket?.cancel()
        screenOffReceiver?.let { unregisterReceiver(it) }
        screenOnReceiver?.let { unregisterReceiver(it) }
    }
    fun sendCommand(dto: WebSocketDto): Boolean {
        val json = gson.toJson(dto)
        val result = webSocket?.send(json) ?: false
        logis("${json}: ${result}")
        if (!result) startWebSocket()
        return result
    }
    data class WebSocketDto(val id: Int?,
                            val type: String,
                            val domain: String? = null,
                            val service: String? = null,
                            val event_type: String? = null,
                            val service_data: ServiceRequest? = null,
                            val api_password: String? = null)
    val gson = GsonBuilder().create()
    fun callService(domain: String?, service: String?, serviceRequest: ServiceRequest): Boolean {
        try {
            return sendCommand(WebSocketDto(++nextNum, "call_service", domain = domain, service = service, service_data = serviceRequest))
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
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
                            RxBus2.getDefault().post(EntityChanged(newEntity))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    class TerminateConnectionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mInstance?.stopWebSocket()
        }
    }
    class ScreenOffReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mInstance?.stopWebSocket()
        }
    }
    class ScreenOnReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mInstance?.startWebSocket()
        }
    }
    companion object {
        private var mInstance: DataSyncService? = null
    }
}