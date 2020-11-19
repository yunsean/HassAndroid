package cn.com.thinkwatch.ihass2.model.automation

import android.annotation.SuppressLint
import android.os.Parcelable
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.ui.automation.condition.ConditionNumbericActivity
import cn.com.thinkwatch.ihass2.ui.automation.condition.ConditionStateActivity
import cn.com.thinkwatch.ihass2.ui.automation.condition.ConditionSunActivity
import cn.com.thinkwatch.ihass2.ui.automation.condition.ConditionTimeActivity
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import kotlinx.android.parcel.RawValue
import java.util.*

//https://www.home-assistant.io/docs/automation/condition/
enum class ConditionType(var desc: String) {
    and("AND"),
    or("OR"),
    numeric_state("数字状态"),
    state("状态"),
    sun("日出"),
    template("模板"),
    time("时间"),
    zone("区域"),
    unknown("不支持")
}
open class Condition(var condition: ConditionType = ConditionType.and) {
    open fun desc() = "未知条件"
    open fun icon() = "mdi:code-tags"
}

data class AndCondition(
        val conditions: List<Condition> = listOf()
) : Condition(ConditionType.and) {
    override fun desc(): String = "同时满足${conditions.size}个条件"
    override fun icon(): String = "mdi:gate-and"
}

data class OrCondition(
        val conditions: List<Condition> = listOf()
) : Condition(ConditionType.or) {
    override fun desc(): String = "满足${conditions.size}个条件中任意一个"
    override fun icon(): String = "mdi:gate-or"
}

data class NumericStateCondition(
        @SerializedName("entity_id") var entityId: String = "",
        var above: BigDecimal? = null,
        var below: BigDecimal? = null,
        @SerializedName("value_template") var valueTemplate: String? = null
) : Condition(ConditionType.numeric_state) {
    override fun desc(): String = ConditionNumbericActivity.desc(this)
    override fun icon(): String = HassApplication.application.db.getEntity(entityId)?.iconState ?: "mdi:counter"
}

data class TimeOffset(
        var hours: Int? = null,
        var minutes: Int? = null,
        var seconds: Int? = null) {
    override fun toString(): String = String.format("%02d:%02d:%02d", hours, minutes, hours)
    fun toDate(): Calendar {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hours ?: 0)
        calendar.set(Calendar.MINUTE, minutes ?: 0)
        calendar.set(Calendar.SECOND, seconds ?: 0)
        return calendar
    }
    fun setDate(date: Calendar) {
        hours = date.get(Calendar.HOUR_OF_DAY)
        minutes = date.get(Calendar.MINUTE)
        seconds = date.get(Calendar.SECOND)
    }
}
data class StateCondition(
        @SerializedName("entity_id") var entityId: String = "",
        var state: String = "",
        @SerializedName("for") var lasted: TimeOffset? = null
) : Condition(ConditionType.state) {
    @Transient private var entity: JsonEntity? = null
    override fun desc(): String = ConditionStateActivity.desc(this)
    override fun icon(): String {
        if (entity == null) entity = HassApplication.application.db.getEntity(entityId)
        return entity?.iconState ?: "mdi:airplay"
    }
}

data class SunCondition(
        var after: SunEvent? = null,
        var before: SunEvent? = null,
        @SerializedName("after_offset") var afterOffset: String? = null,
        @SerializedName("before_offset") var beforeOffset: String? = null
) : Condition(ConditionType.sun) {
    override fun desc(): String = ConditionSunActivity.desc(this)
    override fun icon(): String = "mdi:weather-sunny"
}

data class TemplateCondition(
        @SerializedName("value_template") var valueTemplate: String = ""
) : Condition(ConditionType.template) {
    override fun desc(): String = "模板条件"
    override fun icon(): String = "mdi:code-braces"
}

enum class Weekday(val desc: String) {
    mon("周一"),
    tue("周二"),
    wed("周三"),
    thu("周四"),
    fri("周五"),
    sat("周六"),
    sun("周日")
}
data class TimeCondition(
        var after: String? = null,
        var before: String? = null,
        var weekday: List<Weekday>? = null
) : Condition(ConditionType.time) {
    override fun desc(): String = ConditionTimeActivity.desc(this)
    override fun icon(): String = "mdi:av-timer"
}

data class ZoneCondition(
        @SerializedName("entity_id") var entityId: String = "",
        var zone: String = ""
) : Condition(ConditionType.zone) {
    @Transient private var entity: JsonEntity? = null
    @Transient private var zoneEntity: JsonEntity? = null
    override fun desc(): String {
        if (entity == null) entity = HassApplication.application.db.getEntity(entityId)
        if (zoneEntity == null) zoneEntity = HassApplication.application.db.getEntity(zone)
        return "${entity?.friendlyName ?: entityId}位于${zoneEntity?.friendlyName ?: zone}"
    }
    override fun icon(): String {
        if (entity == null) entity = HassApplication.application.db.getEntity(entityId)
        return entity?.iconState ?: "mdi:home-group"
    }
}

data class UnknownCondition(
        var fields: MutableMap<String, Any?>? = mutableMapOf()
): Condition(ConditionType.unknown) {
    override fun desc(): String = "暂不支持的条件"
    override fun icon(): String = "mdi:alert-decagram"
}
