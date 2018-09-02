package cn.com.thinkwatch.ihass2.dto.qtfm

import com.google.gson.annotations.SerializedName

data class Channel(@SerializedName("content_id") val id: Int = 0,
                   @SerializedName("content_type") val type: String = "",
                   @SerializedName("cover") val cover: String = "",
                   @SerializedName("title") val title: String = "",
                   @SerializedName("description") val description: String = "",
                   @SerializedName("nowplaying") val nowplaying: NowPlaying = NowPlaying(),
                   @SerializedName("audience_count") val playCount: Int = 0)