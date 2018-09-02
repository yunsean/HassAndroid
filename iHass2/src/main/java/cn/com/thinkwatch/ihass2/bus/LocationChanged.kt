package cn.com.thinkwatch.ihass2.bus

data class LocationChanged(val latitude: Double,
                           val longitude: Double,
                           val radius: Float,
                           val coorType: String)