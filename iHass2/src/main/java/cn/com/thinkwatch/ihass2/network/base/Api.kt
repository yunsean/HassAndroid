package cn.com.thinkwatch.ihass2.network.base

import android.content.Context
import android.support.v4.app.Fragment
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.network.http.HttpApi
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import io.reactivex.Observable


interface Api {
    var entityChanged: ((entity: String?, array: Boolean)-> Unit)?
    fun getStates(): Observable<ArrayList<JsonEntity>>
    fun getServices(): Observable<String>
    fun getRawStates(): String?
    fun readStates(): Observable<String>
    fun callService(domain: String?, service: String?, json: String?): Observable<String>
    fun callService(domain: String?, service: String?, json: ServiceRequest?): Observable<String>
    fun getHistory(timestamp: String?, entityId: String?, endTime: String? = null): Observable<String>
    fun gpsLogger(password: String?, token: String?, latitude: Double, longitude: Double, altitude: Double, address: String?, device: String, accuracy: String, provider: String, battery: Float, batteryTemperature: Float, isCharging: Boolean, isInteractive: Boolean, wifi: String?, currentApp: String?, gpsPassword: String?): Observable<String>
    fun gpsLogger2(webHookId: String?, latitude: Double, longitude: Double, altitude: Double, device: String, accuracy: String, provider: String, battery: Float, currentApp: String?): Observable<String>
    fun dispose()

    companion object {
        var api: Api? = null
            get() {
                if (field == null) {
                    field = HassApplication.application.let {
                        if (it.isConnectByLan) HttpApi(it.cfg.getString(HassConfig.Hass_LocalUrl, ""))
                        else HttpApi(it.cfg.getString(HassConfig.Hass_HostUrl, ""))
                    }
                }
                return field
            }
            set(value) {
                field?.dispose()
                field = value
            }
        val instance: Api
            get() = api!!
    }
}

inline val Context.api: Api
    get() = Api.instance
inline val Fragment.api: Api
    get() = Api.instance