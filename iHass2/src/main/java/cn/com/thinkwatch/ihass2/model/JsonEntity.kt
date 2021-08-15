package cn.com.thinkwatch.ihass2.model

import android.os.Parcel
import android.os.Parcelable
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.db.LocalStorage
import cn.com.thinkwatch.ihass2.enums.ItemType
import cn.com.thinkwatch.ihass2.enums.TileType
import cn.com.thinkwatch.ihass2.utils.Gsons
import com.dylan.common.data.GpsUtil
import com.google.gson.annotations.SerializedName
import java.util.*
import java.util.regex.Pattern

data class JsonEntity(@SerializedName("entity_id") var entityId: String = "",
                      @SerializedName("state") var state: String? = null,
                      @SerializedName("last_updated") var lastUpdated: String? = null,
                      @SerializedName("last_changed") var lastChanged: String? = null,
                      @SerializedName("attributes") var attributes: Attribute? = null,
                      var displayOrder: Int = 0,
                      var showIcon: String? = null,
                      var showName: String? = null,
                      var itemType: ItemType = ItemType.entity,
                      var tileType: TileType = TileType.inherit,
                      var columnCount: Int = 1) : Parcelable {
    val friendlyName: String
        get() = attributes?.friendlyName ?: ""

    val domain: String
        get() = if (entityId.contains("\\.".toRegex())) entityId.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0] else ""

    val icon: String?
        get() = if (showIcon.isNullOrBlank()) friendlyStateRow else showIcon

    val domainRanking: Int
        get() = if (isSun) 0 else if (isDeviceTracker) 1 else if (isSensor) 2 else if (isAnySensors) 3 else domain[0].toInt()

    val isHidden: Boolean
        get() = attributes?.hidden ?: false

    val isSupported: Boolean
        get() = friendlyState != null && entityId != "" && friendlyName != ""

    val isDisplayTile: Boolean
        get() = attributes?.unitOfMeasurement != null

    val isSwitch: Boolean
        get() = entityId.startsWith("switch.")

    val isLight: Boolean
        get() = entityId.startsWith("light.")

    val isFan: Boolean
        get() = entityId.startsWith("fan.")

    val isCover: Boolean
        get() = entityId.startsWith("cover.")

    val isLock: Boolean
        get() = entityId.startsWith("lock.")

    val isVacuum: Boolean
        get() = entityId.startsWith("vacuum.")

    val isMediaPlayer: Boolean
        get() = entityId.startsWith("media_player.")

    val isDeviceTracker: Boolean
        get() = entityId.startsWith("device_tracker.")

    val isZone: Boolean
        get() = entityId.startsWith("zone.")

    val isSun: Boolean
        get() = entityId.startsWith("sun.")

    val isSensor: Boolean
        get() = entityId.startsWith("sensor.")

    val isClimate: Boolean
        get() = entityId.startsWith("climate.")

    val isCamera: Boolean
        get() = entityId.startsWith("camera.")

    val isAnySensors: Boolean
        get() = entityId.contains("sensor.")

    val isGroup: Boolean
        get() = entityId.startsWith("group.")

    val isAutomation: Boolean
        get() = entityId.startsWith("automation.")

    val isScript: Boolean
        get() = entityId.startsWith("script.")

    val isInputSelect: Boolean
        get() = entityId.startsWith("input_select.")

    val isInputSlider: Boolean
        get() = entityId.startsWith("input_slider.") || entityId.startsWith("input_number.")

    val isAlarmControlPanel: Boolean
        get() = entityId.startsWith("alarm_control_panel.")

    val isScene: Boolean
        get() = entityId.startsWith("scene.")

    val isInputBoolean: Boolean
        get() = entityId.startsWith("input_boolean.")

    val isInputText: Boolean
        get() = entityId.startsWith("input_text.")

    val isInputDateTime: Boolean
        get() = entityId.startsWith("input_datetime.")

    val isPersistentNotification: Boolean
        get() = entityId.startsWith("persistent_notification.")

    val isBinarySensor: Boolean
        get() = entityId.startsWith("binary_sensor.")

    val isMiioGateway: Boolean
        get() = entityId.startsWith("miio_acpartner.")

    val isAnyBroadcast: Boolean
        get() = entityId.startsWith("broadcast.")
    val isBroadcastRadio: Boolean
        get() = (entityId == "broadcast.qtfm") || (entityId == "broadcast.xmly")
    val isBroadcastVoice: Boolean
        get() = entityId == "broadcast.voice"
    val isBroadcastMusic: Boolean
        get() = isAnyBroadcast && !isBroadcastRadio && !isBroadcastVoice

    val groupName: String?
        get() = if (hasMdiIcon) friendlyStateRow else (if (isSensor && attributes?.unitOfMeasurement != null) (if (hasMdiIcon || !showIcon.isNullOrBlank()) state + " " else "") + attributes?.unitOfMeasurement else friendlyDomainName) ?: state

    val friendlyDomainName: String?
        get() = if (isInputSelect) "Input Select"
        else if (attributes?.ihassState?.containsKey(state) ?: false) attributes?.ihassState?.get(state)
        else if (isInputSlider) "Input Number"
        else if (isInputDateTime) "Input DateTime"
        else if (isInputText) "Input Text"
        else if (isInputBoolean) "Input Boolean"
        else if (isMediaPlayer) "Media Player"
        else if (isCover) if (state == "open") "打开" else if (state == "closed") "关闭" else if (state == "opening") "打开中" else if (state == "closing") "关闭中" else state
        else if (isVacuum) if (state == "docked") "停靠" else if (state == "cleaning") "清扫" else if (state == "idle") "暂停" else if (state == "returning") "回充" else if (state == "unavailable") "离线" else if (state == "off") "关机" else if (state == "unknown") "未知" else state
        else if (isBinarySensor) state?.toUpperCase()
        else if (isSun) if (state == "above_horizon") "日出" else "日落"
        else if (isDeviceTracker) if (state == "home") "在家" else if (state == "not_home") "外出" else state
        else if (isAlarmControlPanel) state
        else if (isPersistentNotification) "Notification"
        else if (isMiioGateway) HassApplication.application.xmlyChannels.get(attributes?.channel?.toInt() ?: 0)?.name ?: "radio"
        else if (isBroadcastRadio && isActivated) LocalStorage.instance.getXmlyCached(attributes?.url?: "")?.name ?: "未知电台"
        else if (isBroadcastVoice && isActivated) "播放中"
        else if (isBroadcastMusic && isActivated) attributes?.url?.let {
            var index = it.lastIndexOfAny(charArrayOf('/', '\\'))
            var name = it
            if (index >= 0) name = name.substring(index + 1)
            index = name.lastIndexOf('.')
            if (index > 0) name = name.substring(0, index)
            name
        } ?: "未知音频"
        else if (isAnyBroadcast) "停止"
        else if (domain.length > 1) domain.substring(0, 1).toUpperCase() + domain.substring(1)
        else null

    val iconState: String?
        get() = getIconState(state)
    fun getIconState(state: String?): String? {
        return if (hasMdiIcon && !isInputSelect && !isInputSlider) attributes?.icon
        else if (isAlarmControlPanel) when (state) {
            "armed_away" -> "mdi:pine-tree"
            "disarmed" -> "mdi:bell-outline"
            "armed_home" -> "mdi:home"
            "pending" -> "mdi:alarm"
            else -> "mdi:alarm"
        }
        else if (attributes?.ihassIcon?.containsKey(state) ?: false) attributes?.ihassIcon?.get(state) ?: "mdi:information-outline"
        else if (isScene) "mdi:format-paint"
        else if (isFan) "mdi:fan"
        else if (isCover) "mdi:window-closed"
        else if (isGroup) "mdi:google-circles-communities"
        else if (isLight) if (state?.toUpperCase() == "ON") "mdi:lightbulb" else "mdi:lightbulb-outline"
        else if (isSun) if (state?.toUpperCase() == "ABOVE_HORIZON") "mdi:white-balance-sunny" else "mdi:brightness-3"
        else if (isSwitch) if (state?.toUpperCase() == "ON") "mdi:toggle-switch" else "mdi:toggle-switch-off"
        else if (isScript) "mdi:code-braces"
        else if (isCamera) "mdi:camera"
        else if (isMediaPlayer) "mdi:cast"
        else if (isDeviceTracker) "mdi:face"
        else if (isAutomation) "mdi:playlist-play"
        else if (isBinarySensor) if (hasMdiIcon) attributes?.icon else "mdi:numeric-1-box-outline"
        else if (isInputBoolean && hasMdiIcon) attributes?.icon
        else if (isMiioGateway && state == "on") "mdi:play"
        else if (isMiioGateway) "mdi:stop"
        else if (isAnyBroadcast && state == "on") "mdi:play"
        else if (isAnyBroadcast) "mdi:stop"
        else friendlyState
    }

    val mdiIcon: String
        get() = if (hasMdiIcon) attributes?.icon ?: ""
        else if (isAlarmControlPanel) when (state) {
            "armed_away" -> "mdi:pine-tree"
            "disarmed" -> "mdi:bell-outline"
            "armed_home" -> "mdi:home"
            "pending" -> "mdi:alarm"
            else -> "mdi:alarm"
        }
        else if (isScene) "mdi:format-paint"
        else if (isFan) "mdi:fan"
        else if (isCover) "mdi:window-closed"
        else if (isGroup) "mdi:google-circles-communities"
        else if (isLight) if (state?.toUpperCase() == "ON") "mdi:lightbulb" else "mdi:lightbulb-outline"
        else if (isSun) if (state?.toUpperCase() == "ABOVE_HORIZON") "mdi:white-balance-sunny" else "mdi:brightness-3"
        else if (isSwitch) if (state?.toUpperCase() == "ON") "mdi:toggle-switch" else "mdi:toggle-switch-off"
        else if (isScript) "mdi:code-braces"
        else if (isCamera) "mdi:camera"
        else if (isMediaPlayer) "mdi:cast"
        else if (isAutomation) "mdi:playlist-play"
        else if (isInputText) "mdi:textbox"
        else if (isInputDateTime) "mdi:calendar-clock"
        else if (isAnySensors) "mdi:eye"
        else if ("homeassistant" == domain) "mdi:home"
        else "mdi:information-outline"

    val deviceClassState: String?
        get() = getDeviceClassState(isCurrentStateActive)
    fun getDeviceClassState(isActive: Boolean): String? {
        return when (attributes?.deviceClass) {
            "cold" -> if (!isActive) "Off" else "Cold"
            "connectivity" -> if (!isActive) "离线" else "在线"
            "gas" -> if (!isActive) "Off" else "Gas"
            "heat" -> if (!isActive) "Off" else "Hot"
            "light" -> if (!isActive) "Off" else "On"
            "moisture" -> if (!isActive) "Off" else "Wet"
            "motion" -> if (!isActive) "Clear" else "Detected"
            "moving" -> if (!isActive) "Stopped" else "Moving"
            "occupancy" -> if (!isActive) "None" else "Occupied"
            "opening" -> if (!isActive) "关闭" else "打开"
            "plug" -> if (!isActive) "Off" else "On"
            "power" -> if (!isActive) "Off" else "On"
            "safety" -> if (!isActive) "Unsafe" else "Safe"
            "smoke" -> if (!isActive) "Off" else "Smoking"
            "sound" -> if (!isActive) "Mute" else "Speaking"
            "vibration" -> if (!isActive) "Quiet" else "Vibrate"
            else -> null
        }
    }

    val friendlyState: String?
        get() = getFriendlyState(state)
    fun getFriendlyState(state: String?): String? {
        return if (attributes?.ihassState?.containsKey(state) ?: false) attributes?.ihassState?.get(state)
        else if (isAlarmControlPanel) when (state) {
            "armed_away" -> "Armed Away"
            "disarmed" -> "Disarmed"
            "armed_home" -> "Armed Home"
            "pending" -> "Pending"
            else -> "Pending"
        }
        else if (isCover) if (state == "open") "打开" else if (state == "closed") "关闭" else if (state == "opening") "打开中" else if (state == "closing") "关闭中" else if (state == "unknown") "未知" else state?.toUpperCase()
        else if (isLock) if (state == "locked") "上锁" else if (state == "unlocked") "开锁" else if (state == "unknown") "未知" else state?.toUpperCase()
        else if (isSwitch || isFan || isLight || isAutomation || isScript || isInputBoolean || isMediaPlayer || isGroup) state?.toUpperCase()
        else if (isVacuum) if (state == "docked") "停靠" else if (state == "cleaning") "清扫" else if (state == "idle") "暂停" else if (state == "returning") "回充" else if (state == "unavailable") "离线" else if (state == "off") "关机" else if (state == "unknown") "未知" else state?.toUpperCase()
        else if (isSun) if (state == "above_horizon") "日出" else "日落"
        else if (isDeviceTracker) if (state == "home") "在家" else if (state == "not_home") "外出" else state?.toUpperCase()
        else getDeviceClassState(state?.toUpperCase() == "ON")?.let { it } ?: state?.toUpperCase()
    }

    val isStateful: Boolean
        get() = attributes?.isStateful ?: true

    val isCurrentStateActive: Boolean
        get() = state?.toUpperCase() == "ON"

    val friendlyStateRow: String
        get() = if (isAnySensors && attributes?.unitOfMeasurement != null && friendlyState?.toDoubleOrNull() != null) String.format(Locale.ENGLISH, "%s %s", friendlyState, attributes?.unitOfMeasurement) else friendlyState ?: ""

    val isActivated: Boolean
        get() = if (isClimate) state?.toUpperCase() != "OFF" else if (isSun) false else if (isVacuum) state == "cleaning" else if (isDeviceTracker) state?.toUpperCase() == "HOME" else if (isMediaPlayer) state?.toUpperCase() != "OFF" else state?.toUpperCase().let { it == "ON" || it == "MOTION_DETECTED" || it == "OPEN" || it == "LOCKED" }

    val nextState: String
        get() = "turn_" + if (isCurrentStateActive) "off" else "on"

    val isToggleable: Boolean
        get() = (isSwitch && isStateful) || isLight || isAutomation || isScript || isInputBoolean || isGroup || isFan || isVacuum || isCover || isLock

    val isCircle: Boolean
        get() = isSensor || isSun || isAnySensors || isDeviceTracker || isAlarmControlPanel

    val location: GpsUtil.LatLng?
        get() = if ((isDeviceTracker || isZone) && attributes?.latitude != null && attributes?.longitude != null) GpsUtil.LatLng(attributes!!.latitude!!.toDouble(), attributes!!.longitude!!.toDouble()) else null

    val isDigitalState
        get() = state?.let { digitalPattern.matcher(it).matches() } ?: false

    val hasIndicator: Boolean
        get() = isToggleable && !isGroup

    val hasMdiIcon: Boolean
        get() = attributes?.icon?.startsWith("mdi:") ?: false

    val hasStateIcon: Boolean
        get() = if (isLight || isSwitch || isScript || isAutomation || isCamera || isMediaPlayer || isGroup || isDeviceTracker || isSun || isFan || isCover) true
        else if (isBinarySensor || isAlarmControlPanel || isScene) true
        else if (isInputBoolean && hasMdiIcon) hasMdiIcon
        else if (hasMdiIcon && !isAnySensors && !isInputBoolean && !isInputSelect && !isInputSlider) true
        else if (isSensor && hasMdiIcon) true
        else false

    constructor(source: Parcel) : this(
            source.readString(),
            source.readString(),
            source.readString(),
            source.readString(),
            source.readString()?.let { Gsons.gson.fromJson(it, Attribute::class.java) },
            source.readInt(),
            source.readString(),
            source.readString(),
            ItemType.values()[source.readInt()],
            TileType.values()[source.readInt()],
            source.readInt()
    )

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeString(entityId)
        writeString(state)
        writeString(lastUpdated)
        writeString(lastChanged)
        writeString(attributes?.let { Gsons.gson.toJson(it) })
        writeInt(displayOrder)
        writeString(showIcon)
        writeString(showName)
        writeInt(itemType.ordinal)
        writeInt(tileType.ordinal)
        writeInt(columnCount)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<JsonEntity> = object : Parcelable.Creator<JsonEntity> {
            override fun createFromParcel(source: Parcel): JsonEntity = JsonEntity(source)
            override fun newArray(size: Int): Array<JsonEntity?> = arrayOfNulls(size)
        }
        private val digitalPattern by lazy { Pattern.compile("^[-\\+]?[.\\d]*$") }
    }
}