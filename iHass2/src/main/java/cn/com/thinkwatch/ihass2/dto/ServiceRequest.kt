package cn.com.thinkwatch.ihass2.dto

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class ServiceRequest(@Transient var domain: String? = "homeassistant",
                          @Transient var service: String?,
                          @SerializedName("entity_id") var entityId: String?,
                          @SerializedName("option") var option: String? = null,
                          @SerializedName("value") var value: String? = null,
                          @SerializedName("code") var code: String? = null,
                          @SerializedName("rgb_color") var rgbColor: Array<Int>? = null,
                          @SerializedName("volume_level") var volumeLevel: BigDecimal? = null,
                          @SerializedName("brightness") var brightness: Int? = null,
                          @SerializedName("color_temp") var colorTemp: Int? = null,
                          @SerializedName("temperature") var temperature: BigDecimal? = null,
                          @SerializedName("is_volume_muted") var isVolumeMuted: Boolean? = null,
                          @SerializedName("date") var date: String? = null,
                          @SerializedName("speed") var speed: String? = null,
                          @SerializedName("fan_speed") var fanSpeed: String? = null,
                          @SerializedName("operation_mode") var operationMode: String? = null,
                          @SerializedName("swing_mode") var swingMode: String? = null,
                          @SerializedName("time") var time: String? = null,
                          @SerializedName("message") var message: String? = null,
                          @SerializedName("pan") var pan: String? = null,
                          @SerializedName("tilt") var tilt: String? = null,
                          @SerializedName("zoom") var zoom: String? = null)
