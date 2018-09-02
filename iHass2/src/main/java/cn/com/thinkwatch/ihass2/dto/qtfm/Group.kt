package cn.com.thinkwatch.ihass2.dto.qtfm

import com.google.gson.annotations.SerializedName

data class Group(@SerializedName("id") val id: Int = 0,
                 @SerializedName("title") val name: String = "")