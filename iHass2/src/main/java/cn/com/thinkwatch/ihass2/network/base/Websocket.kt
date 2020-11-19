package cn.com.thinkwatch.ihass2.network.base

import android.content.Context
import android.support.v4.app.Fragment
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.network.http.HttpWebSocket
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import io.reactivex.Observable

interface Websocket {
    var entityChanged: ((entity: String?, array: Boolean)-> Unit)?
    fun start()
    fun hearbeat()
    fun stop()
    fun callService(domain: String, service: String, request: ServiceRequest?): Observable<String>
    fun dispose()

    companion object {
        var ws: Websocket? = null
            get() {
                if (field == null) {
                    field = HassApplication.application.let {
                        if (it.isConnectByLan) HttpWebSocket(it.cfg.getString(HassConfig.Hass_LocalUrl, ""))
                        else HttpWebSocket(it.cfg.getString(HassConfig.Hass_HostUrl, ""))
                    }
                }
                return field
            }
            set(value) {
                field?.dispose()
                field = value
            }
        val instance: Websocket
            get() = ws!!
    }
}

inline val Context.ws: Websocket
    get() = Websocket.instance
inline val Fragment.ws: Websocket
    get() = Websocket.instance