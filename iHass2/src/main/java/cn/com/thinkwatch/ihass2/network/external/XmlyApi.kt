package cn.com.thinkwatch.ihass2.network.external

import android.content.Context
import android.support.v4.app.Fragment
import cn.com.thinkwatch.ihass2.network.BaseApi
import cn.com.thinkwatch.ihass2.dto.xmly.*
import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.Query

interface XmlyApi {
    @GET("/live-web/v1/getProvinceList")
    fun getProvinceList(): Observable<XmlyResult2<List<Province>>>

    @GET("/live-web/v5/homepage")
    fun getCategoryList(): Observable<XmlyResult<Homepage>>

    @GET("/live-web/v2/radio/national")
    fun getNationalChannel(@Query("pageNum") pageNum: Int, @Query("pageSize") pageSize: Int = 20): Observable<XmlyResult<ChannelResult>>

    @GET("/live-web/v2/radio/province")
    fun getProvinceChannel(@Query("provinceCode") provinceCode: Int, @Query("pageNum") pageNum: Int, @Query("pageSize") pageSize: Int = 20): Observable<XmlyResult<ChannelResult>>

    @GET("/live-web/v2/radio/category")
    fun getCategoryChannel(@Query("categoryId") categoryId: Int, @Query("pageNum") pageNum: Int, @Query("pageSize") pageSize: Int = 20): Observable<XmlyResult<ChannelResult>>

    @GET("/live-web/v2/radio/network")
    fun getNetworkChannel(@Query("pageNum") pageNum: Int, @Query("pageSize") pageSize: Int = 20): Observable<XmlyResult<ChannelResult>>

    companion object {
        var jsonApi: XmlyApi? = null
        get() {
            if (field == null) field = BaseApi.jsonApi("http://live.ximalaya.com/", XmlyApi::class.java)
            return field
        }
    }
}

inline val Context.xmlyApi: XmlyApi
    get() = XmlyApi.jsonApi!!
inline val Fragment.xmlyApi: XmlyApi
    get() = XmlyApi.jsonApi!!
