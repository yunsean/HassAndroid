package cn.com.thinkwatch.ihass2.network.external

import android.content.Context
import android.support.v4.app.Fragment
import cn.com.thinkwatch.ihass2.dto.qtfm.Categories
import cn.com.thinkwatch.ihass2.dto.qtfm.Channel
import cn.com.thinkwatch.ihass2.dto.qtfm.QtfmResult
import cn.com.thinkwatch.ihass2.network.BaseApi
import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface QtfmApi {
    @GET("/categories")
    fun getCategories(): Observable<QtfmResult<Categories>>

    @GET("/categories/{channelId}/channels")
    fun getChannels(@Path("channelId")channelId: Int, @Query("page") page: Int, @Query("pagesize") pagesize: Int = 20): Observable<QtfmResult<List<Channel>>>

    @GET("/categories/409/channels")
    fun getNationalChannels(@Query("page") page: Int, @Query("pagesize") pagesize: Int = 20): Observable<QtfmResult<List<Channel>>>

    @GET("/categories/407/channels")
    fun getNetworkChannels(@Query("page") page: Int, @Query("pagesize") pagesize: Int = 20): Observable<QtfmResult<List<Channel>>>

    companion object {
        var jsonApi: QtfmApi? = null
        get() {
            if (field == null) field = BaseApi.jsonApi("https://rapi.qingting.fm/", QtfmApi::class.java)
            return field
        }
    }
}

inline val Context.qtfmApi: QtfmApi
    get() = QtfmApi.jsonApi!!
inline val Fragment.qtfmApi: QtfmApi
    get() = QtfmApi.jsonApi!!
