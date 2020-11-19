package cn.com.thinkwatch.ihass2.network.http

import cn.com.thinkwatch.ihass2.dto.AutomationResponse
import cn.com.thinkwatch.ihass2.dto.ConfigEntityResult
import cn.com.thinkwatch.ihass2.dto.EntityConfigResult
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.Period
import cn.com.thinkwatch.ihass2.model.automation.Automation
import cn.com.thinkwatch.ihass2.model.script.Script
import cn.com.thinkwatch.ihass2.model.service.Domain
import io.reactivex.Observable
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*
import java.util.*

interface HttpRestApi {
    @GET("/api/states")
    fun getStates(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?): Observable<ArrayList<JsonEntity>>

    @GET("/api/services")
    fun getServices(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?): Observable<ArrayList<Domain>>

    @POST("/api/services/{domain}/{service}")
    fun callService(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?, @Path("domain") domain: String?, @Path("service") service: String?, @Body json: ServiceRequest?): Observable<String>

    @POST("/api/services/{domain}/{service}")
    fun syncCallService(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?, @Path("domain") domain: String?, @Path("service") service: String?, @Body json: ServiceRequest?): Call<ArrayList<JsonEntity>>

    @POST("/api/states/{entityId}")
    fun setState(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?, @Path("entityId") entityId: String?, @Body json: JsonEntity): Observable<JsonEntity?>

    @GET("/api/history/period/{timestamp}")
    fun getHistory(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?, @Path("timestamp") timestamp: String?, @Query("filter_entity_id") entityId: String?, @Query("end_time") endTime: String?): Observable<ArrayList<ArrayList<Period>>>

    @GET("/api/gpslogger")
    fun gpsLogger(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?,
                  @Query("latitude") latitude: Double, @Query("longitude") longitude: Double,
                  @Query("altitude") altitude: Double, @Query("address") address: String?,
                  @Query("device") device: String, @Query("accuracy") accuracy: String,
                  @Query("provider") provider: String, @Query("battery") battery: Float,
                  @Query("batteryTemperature") batteryTemperature: Float,
                  @Query("charging") isCharging: Boolean, @Query("interactive") isInteractive: Boolean,
                  @Query("wifi") wifi: String?, @Query("app") currentApp: String?,
                  @Query("api_password") gpsPassword: String?): Observable<String>

    @FormUrlEncoded
    @Headers("Content-Type: application/x-www-form-urlencoded;charset=UTF-8")
    @POST("/api/webhook/{webHookId}")
    fun gpsLogger2(@Path("webHookId") webHookId: String?,
                   @Field("latitude") latitude: Double, @Field("longitude") longitude: Double,
                   @Field("altitude") altitude: Double, @Field("device") device: String,
                   @Field("accuracy") accuracy: String, @Field("provider") provider: String,
                   @Field("battery") battery: Float, @Field("activity") currentApp: String?): Observable<String>

    @GET("/api/broadcast/files")
    fun getBroadcastFiles(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?, @Query("entity_id") entityId: String?): Observable<ArrayList<String>>

    @GET("/api/config/automation/config/{id}")
    fun getAutomation(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?, @Path("id") id: String?): Observable<Automation>

    @POST("/api/config/automation/config/{id}")
    fun setAutomation(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?, @Path("id") id: String?, @Body body: RequestBody): Observable<AutomationResponse>

    @GET("/api/config/script/config/{id}")
    fun getScript(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?, @Path("id") id: String?): Observable<Script>

    @POST("/api/config/script/config/{id}")
    fun setScript(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?, @Path("id") id: String?, @Body body: RequestBody): Observable<AutomationResponse>

    @POST("/api/template")
    fun testTemplate(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?, @Body body: RequestBody): Observable<String>

    @POST("/api/config/customize/config/{entityId}")
    fun setConfigEntity(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?, @Path("entityId") entityId: String?, @Body body: RequestBody): Observable<ConfigEntityResult>

    @GET("/api/config/customize/config/{entityId}")
    fun getConfigEntity(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?, @Path("entityId") entityId: String?): Observable<EntityConfigResult>
}
