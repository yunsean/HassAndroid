package cn.com.thinkwatch.ihass2.network.http

import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.network.BaseApi
import cn.com.thinkwatch.ihass2.network.base.Api
import cn.com.thinkwatch.ihass2.utils.Gsons
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import io.reactivex.Observable

class HttpApi(private val haHost: String): Api {
    private val app = HassApplication.application
    private val haPassword: String? by lazy { app.cfg.optString(HassConfig.Hass_Password) }
    private val haToken: String? by lazy { app.cfg.optString(HassConfig.Hass_Token) }
    private val restApi: HttpRestApi by lazy { BaseApi.jsonApi(haHost, HttpRestApi::class.java) }
    private val rawApi: HttpRawApi by lazy { BaseApi.rawApi(haHost, HttpRawApi::class.java) }

    override var entityChanged: ((entity: String?, array: Boolean)-> Unit)? = null
    override fun getStates(): Observable<ArrayList<JsonEntity>> = restApi.getStates(haPassword, haToken)
    override fun getRawStates(): String? = rawApi.syncRawStates(haPassword, haToken).execute().body()
    override fun readStates(): Observable<String> = rawApi.getRawStates(haPassword, haToken)
    override fun callService(domain: String?, service: String?, json: String?): Observable<String> = rawApi.callService(haPassword, haToken, domain, service, json)
    override fun getServices(): Observable<String> = rawApi.getServices(haPassword, haToken)
    override fun callService(domain: String?, service: String?, json: ServiceRequest?): Observable<String> = rawApi.callService(haPassword, haToken, domain, service, Gsons.gson.toJson(json))
    override fun getHistory(timestamp: String?, entityId: String?, endTime: String?): Observable<String> = rawApi.getHistory(haPassword, haToken, timestamp, entityId, endTime)
    override fun gpsLogger(password: String?, token: String?, latitude: Double, longitude: Double, altitude: Double, address: String?, device: String, accuracy: String, provider: String, battery: Float, batteryTemperature: Float, isCharging: Boolean, isInteractive: Boolean, wifi: String?, currentApp: String?, gpsPassword: String?): Observable<String> = restApi.gpsLogger(password, token, latitude, longitude, altitude, address, device, accuracy, provider, battery, batteryTemperature, isCharging, isInteractive, wifi, currentApp, gpsPassword)
    override fun gpsLogger2(webHookId: String?, latitude: Double, longitude: Double, altitude: Double, device: String, accuracy: String, provider: String, battery: Float, currentApp: String?): Observable<String> = restApi.gpsLogger2(webHookId, latitude, longitude, altitude, device, accuracy, provider, battery, currentApp)

    override fun dispose() { }
}
