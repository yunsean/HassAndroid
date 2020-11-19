package cn.com.thinkwatch.ihass2.network.http

import android.content.Context
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.network.BaseApi
import cn.com.thinkwatch.ihass2.network.base.Api
import cn.com.thinkwatch.ihass2.network.base.Websocket
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import com.google.gson.GsonBuilder
import com.yunsean.dynkotlins.extensions.logis
import com.yunsean.dynkotlins.extensions.next
import io.reactivex.Observable
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HttpWebSocket(private val hostUrl: String): Websocket {

    private var haPassword: String? = null
    private var haToken: String? = null
    private var haBaseUrl: String? = null
    private var webSocket: WebSocket? = null
    private var connected = false
    private var latestSend: Long = System.currentTimeMillis()
    private val gson = GsonBuilder().create()
    private var nextNum = 100

    override var entityChanged: ((entity: String?, array: Boolean)-> Unit)? = null
    override fun start() {
        try {
            stop()
            val context: Context = HassApplication.application
            haToken = context.cfg.optString(HassConfig.Hass_Token)?.replace("Bearer ", "")
            if (haToken.isNullOrBlank()) haToken = null
            haPassword = if (haToken.isNullOrBlank()) context.cfg.optString(HassConfig.Hass_Password) else null
            haBaseUrl = hostUrl.replace("http://", "ws://").replace("https://", "wss://") + "/api/websocket"
            connected = false
            val request = Request.Builder().url(haBaseUrl!!).build()
            val listener = EchoWebSocketListener()
            val client = BaseApi.getWebSocketOkHttpClientInstance()
            webSocket = client.newWebSocket(request, listener)
        } catch (ex: Exception) {
            ex.printStackTrace()
            stop()
        }
    }
    override fun hearbeat() {
        if (System.currentTimeMillis() -  latestSend > 15_000) {
            sendCommand(WebSocketDto(++nextNum, "ping"))
        }
    }
    override fun stop() {
        webSocket?.let {
            webSocket = null
            try { it.cancel() } catch (ex: Exception) {  }
        }
    }
    override fun callService(domain: String, service: String, request: ServiceRequest?): Observable<String> {
        if (if (isRunning()) try { sendCommand(WebSocketDto(++nextNum, "call_service", domain = domain, service = service, service_data = request)) } catch (e: Exception) { false } else false) {
            return Observable.create<String> { it.onNext("[]") }
        } else {
            return Api.instance.callService(request?.domain, request?.service, request)
        }
    }
    override fun dispose() = stop()

    private fun isRunning(): Boolean {
        val running = webSocket != null && connected && (System.currentTimeMillis() - latestSend < 300_000)
        if (!running) start()
        return running
    }
    private fun sendCommand(dto: WebSocketDto): Boolean {
        val json = gson.toJson(dto)
        logis(json)
        val result = webSocket?.send(json) ?: false
        if (!result) start()
        return result
    }
    private fun processResponse(s: String?) {
        try {
            val response = JSONObject(s)
            val type = response.getString("type")
            when (type) {
                "auth_required" -> {
                    sendCommand(WebSocketDto(null, "auth", api_password = haPassword, access_token = haToken))
                }
                "auth_ok" -> {
                    connected = true
                    sendCommand(WebSocketDto(++nextNum, "subscribe_events", event_type = "state_changed"))
                }
                "event" -> {
                    JSONObject(s).optJSONObject("event")?.optJSONObject("data")?.optJSONObject("new_state")?.let {
                        entityChanged?.invoke(it.toString(), false)
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
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
            connected = false
            webSocket.close(NORMAL_CLOSURE_STATUS, null)
            stop()
        }
        override fun onClosed(webSocket: WebSocket?, code: Int, reason: String?) {
            connected = false
            super.onClosed(webSocket, code, reason)
        }
        override fun onFailure(webSocket: WebSocket?, t: Throwable, response: Response?) {
            connected = false
            if (this@HttpWebSocket.webSocket == webSocket) Observable.timer(3, TimeUnit.SECONDS).next { start() }
        }
    }

    private data class WebSocketDto(val id: Int?,
                            val type: String,
                            val domain: String? = null,
                            val service: String? = null,
                            val event_type: String? = null,
                            val service_data: ServiceRequest? = null,
                            val api_password: String? = null,
                            val access_token: String? = null)
}