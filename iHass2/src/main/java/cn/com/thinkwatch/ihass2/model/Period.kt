package cn.com.thinkwatch.ihass2.model

import com.google.gson.annotations.SerializedName
import java.util.*

data class Period (@SerializedName("entity_id") var entityId: String = "",
                   @SerializedName("state") var state: String? = null,
                   @SerializedName("last_updated") var lastUpdated: Date? = null,
                   @SerializedName("last_changed") var lastChanged: Date? = null)