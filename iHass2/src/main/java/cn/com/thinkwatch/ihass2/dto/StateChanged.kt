package cn.com.thinkwatch.ihass2.dto

import cn.com.thinkwatch.ihass2.model.JsonEntity
import com.google.gson.annotations.SerializedName

class StateChanged(@SerializedName("id") var id: Int? = null,
                   @SerializedName("type") var type: String? = null,
                   @SerializedName("event") var event: Event? = null) {
    data class Event(@SerializedName("data") var data: EventData? = null,
                     @SerializedName("event_type") var type: String? = null)
    data class EventData(@SerializedName("entity_id") var entityId: String? = null,
                         @SerializedName("new_state") var newState: JsonEntity? = null)
}