package cn.com.thinkwatch.ihass2.network.external

import android.content.Context
import android.support.v4.app.Fragment
import cn.com.thinkwatch.ihass2.dto.neteasy.SearchMvResult
import cn.com.thinkwatch.ihass2.dto.neteasy.SearchSongResult
import cn.com.thinkwatch.ihass2.dto.neteasy.UrlResult
import cn.com.thinkwatch.ihass2.network.BaseApi
import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface NeteasyMusicApi {
    @GET("/search")
    fun songs(@Query("keywords") keyword: String, @Query("offset") offset: Int, @Query("type") type: Int = 1): Observable<SearchSongResult>

    @GET("/search")
    fun mvs(@Query("keywords") keyword: String, @Query("offset") offset: Int, @Query("type") type: Int = 1004): Observable<SearchMvResult>

    @GET("/{type}/url")
    fun getUrl(@Path("type") type: String = "song", @Query("id") id: String, @Query("br") bitrate: Int = 192000): Observable<UrlResult>

    companion object {
        var jsonApi: NeteasyMusicApi? = null
        get() {
            if (field == null) field = BaseApi.jsonApi("http://neteasy.yunsean.com/", NeteasyMusicApi::class.java)
            return field
        }
    }
}

inline val Context.neteasyMusicApi: NeteasyMusicApi
    get() = NeteasyMusicApi.jsonApi!!
inline val Fragment.neteasyMusicApi: NeteasyMusicApi
    get() = NeteasyMusicApi.jsonApi!!
