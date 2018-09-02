package cn.com.thinkwatch.ihass2.dto.qtfm

import com.google.gson.annotations.SerializedName

data class NowPlaying(@SerializedName("id") val id: Int = 0,
                      @SerializedName("title") val title: String = "",
                      @SerializedName("broadcasters") val broadcasters: List<String> = listOf(),
                      @SerializedName("start_time") val startTime: String = "",
                      @SerializedName("duration") val duration: Int = 0)