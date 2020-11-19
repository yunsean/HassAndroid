package cn.com.thinkwatch.ihass2.model.automation

import android.annotation.SuppressLint
import android.os.Parcelable
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.ui.automation.trigger.TriggerNumbericActivity
import cn.com.thinkwatch.ihass2.ui.automation.trigger.TriggerPatternActivity
import cn.com.thinkwatch.ihass2.ui.automation.trigger.TriggerStateActivity
import cn.com.thinkwatch.ihass2.ui.automation.trigger.TriggerTimeActivity
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import kotlinx.android.parcel.RawValue
import java.math.BigDecimal

//https://www.home-assistant.io/docs/automation/trigger/
enum class TriggerPlatform(var desc: String) {
    event("事件"),
    homeassistant("系统"),
    mqtt("MQTT"),
    numeric_state("数字状态"),
    state("状态"),
    sun("日出"),
    template("模板"),
    time("时间"),
    time_pattern("时间模式"),
    webhook("WebHook"),
    zone("区域"),
    unknown("不支持的")
}
open class Trigger(
        var platform: TriggerPlatform = TriggerPlatform.event) {
    open fun desc() = "未知触发类型"
    open fun icon() = "mdi:timer"
}

data class EventTrigger(
        @SerializedName("event_type") var eventType: String = "",
        @SerializedName("event_data") var eventData: Map<String, String>? = null
): Trigger(TriggerPlatform.event) {
    override fun desc(): String = "收到${eventType}事件"
    override fun icon() = "mdi:bell-ring-outline"
}

enum class HomeAssistantEvent {start, shutdown}
data class HomeAssistantTrigger(
        var event: HomeAssistantEvent = HomeAssistantEvent.start
): Trigger(TriggerPlatform.homeassistant) {
    override fun desc(): String = if (event == HomeAssistantEvent.start) "Home assistant启动" else "Home assistant关闭"
    override fun icon(): String = if (event == HomeAssistantEvent.start) "mdi:play-circle" else "mdi:stop-circle"
}

data class MqttTrigger(
        var topic: String = "",
        var payload: String? = null,
        var encoding: String? = null
): Trigger(TriggerPlatform.mqtt) {
    override fun desc(): String = "${topic}=${payload}"
    override fun icon(): String = "mdi:quality-medium"
}

data class NumericStateTrigger(
        @SerializedName("entity_id") var entityId: String = "",
        @SerializedName("value_template") var valueTemplate: String? = null,
        var above: BigDecimal? = null,
        var below: BigDecimal? = null,
        @SerializedName("for") var lasted: String? = null): Trigger(TriggerPlatform.numeric_state) {
    override fun desc(): String = TriggerNumbericActivity.desc(this)
    override fun icon(): String = HassApplication.application.db.getEntity(entityId)?.iconState ?: "mdi:counter"
}

data class StateTrigger(
        @SerializedName("entity_id") var entityId: String = "",
        var from: String? = null,
        var to: String? = null,
        @SerializedName("for") var lasted: String? = null): Trigger(TriggerPlatform.state) {
    override fun desc(): String = TriggerStateActivity.desc(this)
    override fun icon(): String {
        val ids = entityId.split(",")
        if (ids.size > 0) return HassApplication.application.db.getEntity(ids.get(0))?.iconState ?: "mdi:airplay"
        else return "mdi:airplay"
    }
}

enum class SunEvent(val desc: String) {
    sunset("日落"),
    sunrise("日出")
}
data class SunTrigger(
        var event: SunEvent = SunEvent.sunrise,
        var offset: String? = null): Trigger(TriggerPlatform.sun) {
    override fun desc(): String = if (event == SunEvent.sunrise) "日出" else "日落"
    override fun icon(): String = if (event == SunEvent.sunrise) "mdi:weather-sunset-up" else "mdi:weather-sunset-down"
}

data class TemplateTrigger(
        @SerializedName("value_template") var valueTemplate: String = "",
        @SerializedName("for") var lasted: String? = null): Trigger(TriggerPlatform.template) {
    override fun desc(): String = "模板触发"
    override fun icon(): String = "mdi:code-braces"
}

data class TimeTrigger(
        var at: String? = null,
        var hours: String? = null,
        var minutes: String? = null,
        var seconds: String? = null): Trigger(TriggerPlatform.time) {
    override fun desc(): String = TriggerTimeActivity.desc(this) ?: "不支持的时间模板"
    override fun icon(): String = "mdi:timer"
}

data class TimePatternTrigger(
        var hours: String? = null,
        var minutes: String? = null,
        var seconds: String? = null): Trigger(TriggerPlatform.time_pattern) {
    override fun desc(): String = TriggerPatternActivity.desc(this) ?: "不支持的时间模板"
    override fun icon(): String = "mdi:timer-10"
}

data class WebHookTrigger(
        @SerializedName("webhook_id") var webhookId: String = ""): Trigger(TriggerPlatform.webhook) {
    override fun desc(): String = "WebHook触发"
    override fun icon(): String = "mdi:google-physical-web"
}

enum class ZoneEvent { enter, leave }
data class ZoneTrigger(
        @SerializedName("entity_id") var entityId: String = "",
        var zone: String = "",
        var event: ZoneEvent = ZoneEvent.enter): Trigger(TriggerPlatform.zone) {
    @Transient private var entity: JsonEntity? = null
    @Transient private var zoneEntity: JsonEntity? = null
    override fun desc(): String {
        if (entity == null) entity = HassApplication.application.db.getEntity(entityId)
        if (zoneEntity == null) zoneEntity = HassApplication.application.db.getEntity(zone)
        val result = StringBuilder(entity?.friendlyName ?: entityId)
        result.append(if (event == ZoneEvent.enter) "进入" else "离开")
        result.append(zoneEntity?.friendlyName ?: zone)
        return result.toString()
    }
    override fun icon(): String {
        if (entity == null) entity = HassApplication.application.db.getEntity(entityId)
        return entity?.iconState ?: "mdi:home-group"
    }
}

data class UnknownTrigger(
        val dummy: String? = null): Trigger(TriggerPlatform.unknown) {
    constructor(fields: MutableMap<String, Any?>?) : this() {
        this.fields = fields
    }
    @IgnoredOnParcel var fields: MutableMap<String, Any?>? = mutableMapOf()
    override fun desc(): String = "暂不支持的触发器"
    override fun icon(): String = "mdi:alert-decagram"
}