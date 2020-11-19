package cn.com.thinkwatch.ihass2.api

import android.content.Context
import android.support.v4.app.Fragment
import cn.com.thinkwatch.ihass2.api.mqtt.MqttApi
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.Period
import cn.com.thinkwatch.ihass2.model.service.Domain
import io.reactivex.Observable
import retrofit2.http.Body
import java.util.*

class RestApi {
    fun getServices(password: String?, token: String?): Observable<ArrayList<Domain>> {
        //return HttpRestApi.instance.getServices(password, token)
        return MqttApi.instance.getServices()
    }
    fun callService(password: String?, token: String?, domain: String?, service: String?, @Body json: ServiceRequest): Observable<ArrayList<JsonEntity>> {
        //return HttpRestApi.instance.callService(password, token, domain, service, json)
        return MqttApi.instance.callService(domain, service, json)
    }
    fun syncCallService(password: String?, token: String?, domain: String?, service: String?, @Body json: ServiceRequest?): ArrayList<JsonEntity>? {
        //return HttpRestApi.instance.syncCallService(password, token, domain, service, json)
        return MqttApi.instance.syncCallService(domain, service, json)
    }
    fun setState(password: String?, token: String?, entityId: String?, @Body json: JsonEntity): Observable<JsonEntity?> {
        //return HttpRestApi.instance.setState(password, token, entityId, json)
        return MqttApi.instance.setState(entityId, json)
    }
    fun getHistory(password: String?, token: String?, timestamp: String?, entityId: String? = null, endTime: String? = null): Observable<ArrayList<ArrayList<Period>>> {
        //return HttpRestApi.instance.getHistory(password, token, timestamp, entityId, endTime)
        return MqttApi.instance.getHistory(timestamp, entityId, endTime)
    }
    fun gpsLogger(password: String?, token: String?, latitude: Double, longitude: Double, device: String, accuracy: String, provider: String, gpsPassword: String? = null): Observable<JsonEntity?> {
        //return HttpRestApi.instance.gpsLogger(password, token, latitude, longitude, device, accuracy, provider, gpsPassword)
        return MqttApi.instance.gpsLogger(latitude, longitude, device, accuracy, provider, gpsPassword)
    }

    private object Holder {
        val INSTANCE = RestApi()
    }
    companion object {
        val instance: RestApi by lazy { Holder.INSTANCE }
    }
}

inline val Context.hassApi: RestApi
    get() = RestApi.instance
inline val Fragment.hassApi: RestApi
    get() = RestApi.instance
