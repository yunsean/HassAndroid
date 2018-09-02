package cn.com.thinkwatch.ihass2.dto

import android.os.Parcel
import android.os.Parcelable
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
                          @SerializedName("fan_mode") var fanMode: String? = null,
                          @SerializedName("operation_mode") var operationMode: String? = null,
                          @SerializedName("swing_mode") var swingMode: String? = null,
                          @SerializedName("time") var time: String? = null,
                          @SerializedName("volume") var volume: Int? = null,
                          @SerializedName("message") var message: String? = null,
                          @SerializedName("pan") var pan: String? = null,
                          @SerializedName("tilt") var tilt: String? = null,
                          @SerializedName("url") var url: String? = null,
                          @SerializedName("program_id") val programId: Int? = null,
                          @SerializedName("ringtone_id") val ringtoneId: Int? = null,
                          @SerializedName("zoom") var zoom: String? = null) : Parcelable {
    constructor(source: Parcel) : this(
            source.readString(),
            source.readString(),
            source.readString(),
            source.readString(),
            source.readString(),
            source.readString(),
            source.createIntArray()?.toTypedArray(),
            source.readSerializable() as BigDecimal?,
            source.readValue(Int::class.java.classLoader) as Int?,
            source.readValue(Int::class.java.classLoader) as Int?,
            source.readSerializable() as BigDecimal?,
            source.readValue(Boolean::class.java.classLoader) as Boolean?,
            source.readString(),
            source.readString(),
            source.readString(),
            source.readString(),
            source.readString(),
            source.readString(),
            source.readString(),
            source.readValue(Int::class.java.classLoader) as Int?,
            source.readString(),
            source.readString(),
            source.readString(),
            source.readString(),
            source.readValue(Int::class.java.classLoader) as Int?,
            source.readValue(Int::class.java.classLoader) as Int?,
            source.readString()
    )

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeString(domain)
        writeString(service)
        writeString(entityId)
        writeString(option)
        writeString(value)
        writeString(code)
        writeIntArray(rgbColor?.toIntArray())
        writeSerializable(volumeLevel)
        writeValue(brightness)
        writeValue(colorTemp)
        writeSerializable(temperature)
        writeValue(isVolumeMuted)
        writeString(date)
        writeString(speed)
        writeString(fanSpeed)
        writeString(fanMode)
        writeString(operationMode)
        writeString(swingMode)
        writeString(time)
        writeValue(volume)
        writeString(message)
        writeString(pan)
        writeString(tilt)
        writeString(url)
        writeValue(programId)
        writeValue(ringtoneId)
        writeString(zoom)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ServiceRequest> = object : Parcelable.Creator<ServiceRequest> {
            override fun createFromParcel(source: Parcel): ServiceRequest = ServiceRequest(source)
            override fun newArray(size: Int): Array<ServiceRequest?> = arrayOfNulls(size)
        }
    }
}
