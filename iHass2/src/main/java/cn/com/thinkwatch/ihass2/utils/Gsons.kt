package cn.com.thinkwatch.ihass2.utils

import cn.com.thinkwatch.ihass2.model.automation.*
import cn.com.thinkwatch.ihass2.model.datatype.KeyValues
import cn.com.thinkwatch.ihass2.model.service.Field
import cn.com.thinkwatch.ihass2.model.service.Fields
import cn.com.thinkwatch.ihass2.model.service.Service
import cn.com.thinkwatch.ihass2.model.service.Services
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.yunsean.dynkotlins.extensions.ktime
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.*

object Gsons {
    val gson: Gson by lazy {
        GsonBuilder()
                .enableComplexMapKeySerialization()
                .setVersion(1.0)
                .registerTypeAdapter(Date::class.java, object : JsonDeserializer<Date> {
                    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Date? {
                        return try {
                            val value = json.toString().trim('"')
                            if (value.length == 25) dfShort.parse(value)
                            else dfLong.parse(value)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                            null
                        }
                    }
                })
                .registerTypeAdapter(Date::class.java, object : JsonSerializer<Date> {
                    override fun serialize(src: Date?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
                        if (src == null) return JsonPrimitive("")
                        else return JsonPrimitive(dfLong.format(src))
                    }
                })
                .registerTypeAdapter(Services::class.java, object : JsonDeserializer<Services> {
                    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Services {
                        val services = mutableMapOf<String, Service>()
                        json?.let {
                            val jsonObject = it.asJsonObject
                            for (entry in jsonObject.entrySet()) {
                                val service = context?.deserialize<Service>(entry.value, Service::class.java)
                                if (service != null) services.put(entry.key, service)
                            }
                        }
                        return Services(services)
                    }
                })
                .registerTypeAdapter(Fields::class.java, object : JsonDeserializer<Fields> {
                    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Fields {
                        val fields = mutableMapOf<String, Field>()
                        json?.let {
                            val jsonObject = it.asJsonObject
                            for (entry in jsonObject.entrySet()) {
                                val field = context?.deserialize<Field>(entry.value, Field::class.java)
                                if (field != null) fields.put(entry.key, field)
                            }
                        }
                        return Fields(fields)
                    }
                })
                .registerTypeAdapter(KeyValues::class.java, object : JsonDeserializer<KeyValues> {
                    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): KeyValues? {
                        try {
                            val value = json?.toString()?.trim('"')
                            if (value == null) return null
                            val result = KeyValues()
                            value.split(',').forEach {
                                it.trim().let {
                                    if (!it.isBlank()) {
                                        val parts = it.split('!')
                                        if (parts.size == 1) result.put(parts[0], parts[0])
                                        else if (parts.size == 2) result.put(parts[0], parts[1])
                                    }
                                }
                            }
                            return result
                        } catch (_: Exception) {
                            return null
                        }
                    }
                })
                .registerTypeAdapter(KeyValues::class.java, object : JsonSerializer<KeyValues> {
                    override fun serialize(src: KeyValues?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement? {
                        if (src == null || src.size < 1) return null
                        val result = StringBuffer()
                        src.forEach { (key, value) ->
                            result.append("${key}!${value},")
                        }
                        return JsonPrimitive(result.toString())
                    }
                })
                .registerTypeAdapter(Automation::class.java, object : JsonDeserializer<Automation> {
                    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Automation? {
                        return json?.let {
                            val jsonObject = it.asJsonObject
                            val id = if (jsonObject.has("id")) jsonObject.get("id").asString else ""
                            val alias = if (jsonObject.has("alias")) jsonObject.get("alias").asString else ""
                            val trigger : List<Trigger> = (if (!jsonObject.has("trigger")) null else jsonObject.get("trigger").let { if (it.isJsonArray) context?.deserialize<List<Trigger>>(it, object: TypeToken<List<Trigger>>(){}.type) else context?.deserialize<Trigger>(it, Trigger::class.java)?.let { listOf(it) } }) ?: listOf()
                            val condition : List<Condition> = (if (!jsonObject.has("condition")) null else jsonObject.get("condition").let { if (it.isJsonArray) context?.deserialize<List<Condition>>(it, object: TypeToken<List<Condition>>(){}.type) else context?.deserialize<Condition>(it, Condition::class.java)?.let { listOf(it) } }) ?: listOf()
                            val action : List<Action> = (if (!jsonObject.has("action")) null else jsonObject.get("action").let { if (it.isJsonArray) context?.deserialize<List<Action>>(it, object: TypeToken<List<Action>>(){}.type) else context?.deserialize<Action>(it, Action::class.java)?.let { listOf(it) } }) ?: listOf()
                            Automation(id, alias, trigger, condition, action)
                        }
                    }
                })
                .registerTypeAdapter(Trigger::class.java, object : JsonDeserializer<Trigger> {
                    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Trigger? {
                        json?.let {
                            val jsonObject = it.asJsonObject
                            val platform = jsonObject.get("platform").asString
                            return when (platform) {
                                "event"-> context?.deserialize(json, EventTrigger::class.java)
                                "homeassistant"-> context?.deserialize(json, HomeAssistantTrigger::class.java)
                                "mqtt"-> context?.deserialize(json, MqttTrigger::class.java)
                                "numeric_state"-> context?.deserialize(json, NumericStateTrigger::class.java)
                                "state"-> context?.deserialize(json, StateTrigger::class.java)
                                "sun"-> context?.deserialize(json, SunTrigger::class.java)
                                "template"-> context?.deserialize(json, TemplateTrigger::class.java)
                                "time"-> context?.deserialize(json, TimeTrigger::class.java)
                                "time_pattern"-> context?.deserialize(json, TimePatternTrigger::class.java)
                                "webhook"-> context?.deserialize(json, WebHookTrigger::class.java)
                                "zone"-> context?.deserialize(json, ZoneTrigger::class.java)
                                else-> context?.deserialize(json, UnknownTrigger::class.java)
                            }
                        }
                        return null
                    }
                })
                .registerTypeAdapter(Condition::class.java, object : JsonDeserializer<Condition> {
                    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Condition? {
                        json?.let {
                            val jsonObject = it.asJsonObject
                            val condition = jsonObject.get("condition").asString
                            return when (condition) {
                                "and"-> context?.deserialize(json, AndCondition::class.java)
                                "or"-> context?.deserialize(json, OrCondition::class.java)
                                "numeric_state"-> context?.deserialize(json, NumericStateCondition::class.java)
                                "state"-> context?.deserialize(json, StateCondition::class.java)
                                "sun"-> context?.deserialize(json, SunCondition::class.java)
                                "template"-> context?.deserialize(json, TemplateCondition::class.java)
                                "time"-> context?.deserialize(json, TimeCondition::class.java)
                                "zone"-> context?.deserialize(json, ZoneCondition::class.java)
                                else-> context?.deserialize(json, UnknownCondition::class.java)
                            }
                        }
                        return null
                    }
                })
                .registerTypeAdapter(Action::class.java, object : JsonDeserializer<Action> {
                    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Action? {
                        json?.let {
                            val jsonObject = it.asJsonObject
                            if (jsonObject.has("service")) return context?.deserialize(json, ServiceAction::class.java)
                            else if (jsonObject.has("condition")) return ConditionAction(context?.deserialize(json, Condition::class.java))
                            else if (jsonObject.has("delay")) return context?.deserialize(json, DelayAction::class.java)
                            else if (jsonObject.has("wait_template")) return context?.deserialize(json, WaitAction::class.java)
                            else if (jsonObject.has("event")) return context?.deserialize(json, FireEventAction::class.java)
                            else return context?.deserialize(json, UnknownAction::class.java)
                        }
                        return null
                    }
                })
                .registerTypeAdapter(DelayAction::class.java, object : JsonDeserializer<DelayAction> {
                    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): DelayAction? {
                        return json?.let {
                            val delay = it.asJsonObject.get("delay")
                            if (delay.isJsonObject) {
                                DelayAction(delayValue = context?.deserialize(delay, DelayTime::class.java))
                            } else {
                                DelayAction(delay = delay.asString)
                            }
                        }
                    }
                })
                .registerTypeAdapter(UnknownTrigger::class.java, object : JsonDeserializer<UnknownTrigger> {
                    private fun parseJsonObject(json: JsonObject?): MutableMap<String, Any?>? {
                        if (json == null) return null
                        var map = mutableMapOf<String, Any?>()
                        for (entry in json.entrySet()) {
                            val child = entry.value
                            if (child == null) continue
                            else if (child.isJsonObject) map.put(entry.key, parseJsonObject(child.asJsonObject))
                            else if (child.isJsonPrimitive) map.put(entry.key, child.asJsonPrimitive)
                        }
                        return map
                    }
                    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): UnknownTrigger? {
                        if (json == null || !json.isJsonObject) return null
                        return UnknownTrigger(parseJsonObject(json.asJsonObject))
                    }
                })
                .registerTypeAdapter(UnknownTrigger::class.java, object : JsonSerializer<UnknownTrigger> {
                    private fun writeJsonObject(src: Map<String, Any?>?) : JsonObject? {
                        if (src == null) return null
                        val json = JsonObject()
                        for (entry in src.entries) {
                            if (entry.value == null) continue
                            else if (entry.value is Map<*, *>) json.add(entry.key, writeJsonObject(entry.value as Map<String, Any?>))
                        }
                        return json
                    }
                    override fun serialize(src: UnknownTrigger?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement? {
                        if (src == null || src.fields == null) return null
                        return writeJsonObject(src.fields)
                    }
                })
                .create()
    }
    private val dfLong = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZZZZZ", Locale.ENGLISH)
    private val dfShort = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.ENGLISH)
}