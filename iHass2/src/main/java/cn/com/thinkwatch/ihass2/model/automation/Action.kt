package cn.com.thinkwatch.ihass2.model.automation

import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.ui.automation.action.ActionDelayActivity
import cn.com.thinkwatch.ihass2.ui.automation.action.ActionWaitActivity
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

//https://www.home-assistant.io/docs/automation/action/
enum class ActionType(var desc: String) {
    service("调用服务"),
    condition("条件判断"),
    delay("延时"),
    wait("等待"),
    event("事件触发"),
    unknown("不支持")
}
open class Action(@Transient var action: ActionType = ActionType.service) {
    open fun desc() = "未知操作"
    open fun icon() = "mdi:arrow-right-circle"
}

data class ServiceAction(
        var service: String = "",
        var data: Map<String, String>? = null,
        @SerializedName("data_template") var dataTemplate: Map<String, String>? = null
) : Action(ActionType.service) {
    @Transient private var entity: JsonEntity? = null
    override fun desc(): String {
        data?.get("entity_id")?.let {
            if (entity == null) entity = HassApplication.application.db.getEntity(it)
        }
        return "${entity?.friendlyName ?: ""}执行${service}"
    }
    override fun icon(): String {
        data?.get("entity_id")?.let {
            if (entity == null) entity = HassApplication.application.db.getEntity(it)
        }
        return entity?.iconState ?: "mdi:gesture-tap"
    }
}

data class ConditionAction(
        var condition: Condition? = null
) : Action(ActionType.condition) {
    override fun desc(): String = condition?.desc()?.let { "如果$it" } ?: "条件判断"
    override fun icon() = "mdi:code-tags"
}

data class DelayTime(
        var days: String? = null,
        var hours: String? = null,
        var minutes: String? = null,
        var seconds: String? = null,
        var milliseconds: String? = null)
data class DelayAction(
        var delay: String? = null,
        var delayValue: DelayTime? = null
) : Action(ActionType.delay) {
    override fun desc(): String = ActionDelayActivity.desc(this)
    override fun icon(): String = "mdi:clock-outline"
}

data class WaitAction(
        @SerializedName("wait_template") var waitTemplate: String = "",
        var timeout: String? = null,
        @SerializedName("continue_on_timeout") var continueOnTimeout: Boolean = true
) : Action(ActionType.wait) {
    override fun desc(): String = ActionWaitActivity.desc(this)
    override fun icon(): String = "mdi:timer-sand"
}

data class FireEventAction(
        var event: String = "",
        @SerializedName("event_data") var eventData: Map<String, String>? = null,
        @SerializedName("event_data_template") var eventDataTemplate: Map<String, String>? = null
) : Action(ActionType.service) {
    override fun desc(): String = "发送${event}事件"
    override fun icon(): String = "mdi:undo-variant"
}

data class UnknownAction(
        var fields: MutableMap<String, Any?>? = mutableMapOf()
): Action(ActionType.unknown) {
    override fun desc(): String = "暂不支持的操作"
    override fun icon(): String = "mdi:alert-decagram"
}
