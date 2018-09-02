package cn.com.thinkwatch.ihass2.dto.qtfm

import com.google.gson.annotations.SerializedName

data class QtfmResult<T>(@SerializedName("Success") val success: String,
                         @SerializedName("Data") val data: T?)