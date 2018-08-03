package cn.com.thinkwatch.ihass2.api

import android.content.Context
import android.support.v4.app.Fragment
import cn.com.thinkwatch.ihass2.HassApplication
import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.Header

interface RawApi {
    @GET("/api/states")
    fun rawStates(@Header("x-ha-access") password: String): Observable<String>

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
