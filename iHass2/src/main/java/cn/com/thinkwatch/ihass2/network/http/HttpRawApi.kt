package cn.com.thinkwatch.ihass2.network.http

import io.reactivex.Observable
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

interface HttpRawApi {
    @GET("/api/states")
    fun syncRawStates(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?): Call<String>

    @GET("/api/states")
    fun getRawStates(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?): Observable<String>

    @GET("/api/services")
    fun getServices(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?): Observable<String>

    @POST("/api/services/{domain}/{service}")
    fun callService(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?, @Path("domain") domain: String?, @Path("service") service: String?, @Body json: String?): Observable<String>

    @GET("/api/history/period/{timestamp}")
    fun getHistory(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?, @Path("timestamp") timestamp: String?, @Query("filter_entity_id") entityId: String?, @Query("end_time") endTime: String?): Observable<String>

    @POST("/api/broadcast/voice")
    fun sendVoice(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?, @Body body: RequestBody, @Query("volume") volume: Int? = null): Observable<String>

    @Streaming
    @GET("/api/alumb/download")
    fun albumDownload(@Header("x-ha-access") password: String?,
                      @Header("Authorization") token: String?,
                      @Query("user") user: String,
                      @Query("path") path: String): Observable<Response<ResponseBody>>

}

