package cn.com.thinkwatch.ihass2.api

import android.content.Context
import android.support.v4.app.Fragment
import cn.com.thinkwatch.ihass2.HassApplication
import io.reactivex.Observable
import retrofit2.Call
import retrofit2.http.*

interface RawApi {
    @GET("/api/states")
    fun syncRawStates(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?): Call<String>

    @POST("/api/services/{domain}/{service}")
    fun callService(@Header("x-ha-access") password: String?, @Header("Authorization") token: String?, @Path("domain") domain: String?, @Path("service") service: String?, @Body json: String?): Observable<String>

    companion object {
        var rawApi: RawApi? = null
        get() {
            if (field == null) field = BaseApi.rawApi(HassApplication.application.haHostUrl)
            return field
        }
    }
}

inline val Context.hassRawApi: RawApi
    get() = RawApi.rawApi!!
inline val Fragment.hassRawApi: RawApi
    get() = RawApi.rawApi!!
