package cn.com.thinkwatch.ihass2.model

import cn.com.thinkwatch.ihass2.enums.ItemType
import cn.com.thinkwatch.ihass2.enums.TileType
import com.dylan.common.data.GpsUtil
import com.google.gson.annotations.SerializedName
import java.util.*

data class JsonEntity (@SerializedName("entity_id") var entityId: String = "",
                       @SerializedName("state") var state: String? = null,
                       @SerializedName("last_updated") var lastUpdated: String? = null,
                       @SerializedName("last_changed") var lastChanged: String? = null,
                       @SerializedName("attributes") var attributes: Attribute? = null,
                       var displayOrder: Int = 0,
                       var showIcon: String? = null,
                       var showName: String? = null,
                       var itemType: ItemType = ItemType.entity,
                       var tileType: TileType = TileType.inherit,
                       var columnCount: Int = 1,
                       var id: Long = 0) {
    val friendlyName: String
        get() = attributes?.friendlyName ?: ""
    val domain: String
        get() = if (entityId.contains("\\.".toRegex())) entityId.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0] else ""

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
    val groupName: String?
        get() = if (hasMdiIcon) friendlyStateRow else (if (isSensor && attributes?.unitOfMeasurement != null) (if (hasMdiIcon) state + " " else "") + attributes?.unitOfMeasurement else friendlyDomainName) ?: state
    val friendlyDomainName: String?
        get() = if (isInputSelect) "Input Select"
        else if (isInputSlider) "Input Number"
        else if (isInputDateTime) "Input DateTime"
        else if (isInputText) "Input Text"
        else if (isInputBoolean) "Input Boolean"
        else if (isMediaPlayer) "Media Player"
        else if (isBinarySensor) if (state == "off") "离线" else "在线"
        else if (isSun) if (state == "above_horizon") "Above Horizon" else "Below Horizon"
        else if (isDeviceTracker) if (state == "home") "在家" else "外出"
        else if (isAlarmControlPanel) state
        else if (isPersistentNotification) "Notification"
        else if (domain.length > 1) domain.substring(0, 1).toUpperCase() + domain.substring(1)
        else null
    val iconState: String?
        get() = if (hasMdiIcon && !isInputBoolean && !isInputSelect && !isInputSlider) MDIFont.getIcon(attributes?.icon)
        else if (isAlarmControlPanel) when (state) {
            "armed_away" -> MDIFont.getIcon("mdi:pine-tree")
            "disarmed" -> MDIFont.getIcon("mdi:bell-outline")
            "armed_home" -> MDIFont.getIcon("mdi:home")
            "pending" -> MDIFont.getIcon("mdi:alarm")
            else -> MDIFont.getIcon("mdi:alarm")
        }
        else if (isScene) MDIFont.getIcon("mdi:format-paint")
        else if (isFan) MDIFont.getIcon("mdi:fan")
        else if (isCover) MDIFont.getIcon("mdi:window-closed")
        else if (isGroup) MDIFont.getIcon("mdi:google-circles-communities")
        else if (isLight) MDIFont.getIcon(if (state?.toUpperCase() == "ON") "mdi:lightbulb" else "mdi:lightbulb-outline")
        else if (isSun) MDIFont.getIcon(if (state?.toUpperCase() == "ABOVE_HORIZON") "mdi:white-balance-sunny" else "mdi:brightness-3")
        else if (isSwitch) MDIFont.getIcon(if (state?.toUpperCase() == "ON") "mdi:toggle-switch" else "mdi:toggle-switch-off")
        else if (isScript) MDIFont.getIcon("mdi:code-braces")
        else if (isCamera) MDIFont.getIcon("mdi:camera")
        else if (isMediaPlayer) MDIFont.getIcon("mdi:cast")
        else if (isDeviceTracker) MDIFont.getIcon("mdi:face")
        else if (isAutomation) MDIFont.getIcon("playlist-play")
        else if (isBinarySensor) if (hasMdiIcon) MDIFont.getIcon(attributes?.icon) else MDIFont.getIcon("numeric-1-box-outline")
        else if (isInputBoolean && hasMdiIcon) MDIFont.getIcon(attributes?.icon)
        else friendlyState
    val mdiIcon: String
        get() = if (hasMdiIcon) MDIFont.getIcon(attributes?.icon)
        else if (isAlarmControlPanel) when (state) {
            "armed_away" -> MDIFont.getIcon("mdi:pine-tree")
            "disarmed" -> MDIFont.getIcon("mdi:bell-outline")
            "armed_home" -> MDIFont.getIcon("mdi:home")
            "pending" -> MDIFont.getIcon("mdi:alarm")
            else -> MDIFont.getIcon("mdi:alarm")
        }
        else if (isScene) MDIFont.getIcon("mdi:format-paint")
        else if (isFan) MDIFont.getIcon("mdi:fan")
        else if (isCover) MDIFont.getIcon("mdi:window-closed")
        else if (isGroup) MDIFont.getIcon("mdi:google-circles-communities")
        else if (isLight) MDIFont.getIcon(if (state?.toUpperCase() == "ON") "mdi:lightbulb" else "mdi:lightbulb-outline")
        else if (isSun) MDIFont.getIcon(if (state?.toUpperCase() == "ABOVE_HORIZON") "mdi:white-balance-sunny" else "mdi:brightness-3")
        else if (isSwitch) MDIFont.getIcon(if (state?.toUpperCase() == "ON") "mdi:toggle-switch" else "mdi:toggle-switch-off")
        else if (isScript) MDIFont.getIcon("mdi:code-braces")
        else if (isCamera) MDIFont.getIcon("mdi:camera")
        else if (isMediaPlayer) MDIFont.getIcon("mdi:cast")
        else if (isAutomation) MDIFont.getIcon("playlist-play")
        else if (isInputText) MDIFont.getIcon("textbox")
        else if (isInputDateTime) MDIFont.getIcon("calendar-clock")
        else if (isAnySensors) MDIFont.getIcon("eye")
        else if ("homeassistant" == domain) MDIFont.getIcon("home")
        else MDIFont.getIcon("mdi:information-outline")
    val deviceClassState: String?
        get() = when (attributes?.deviceClass) {
            "cold" -> if (!isCurrentStateActive) "Off" else "Cold"
            "connectivity" -> if (!isCurrentStateActive) "No Connection" else "Connection Present"
            "gas" -> if (!isCurrentStateActive) "Off" else "Gas Detected"
            "heat" -> if (!isCurrentStateActive) "Off" else "Hot"
            "light" -> if (!isCurrentStateActive) "Off" else "On"
            "moisture" -> if (!isCurrentStateActive) "Off" else "Wet"
            "motion" -> if (!isCurrentStateActive) "Clear" else "Detected"
            "moving" -> if (!isCurrentStateActive) "Stopped" else "Moving"
            "occupancy" -> if (!isCurrentStateActive) "Not Occupied" else "Occupied"
            "opening" -> if (!isCurrentStateActive) "Closed" else "Open"
            "plug" -> if (!isCurrentStateActive) "Off" else "On"
            "power" -> if (!isCurrentStateActive) "Off" else "On"
            "safety" -> if (!isCurrentStateActive) "Unsafe" else "Safe"
            "smoke" -> if (!isCurrentStateActive) "Off" else "Smoke Detected"
            "sound" -> if (!isCurrentStateActive) "No Sound" else "Sound Detected"
            "vibration" -> if (!isCurrentStateActive) "No Vibration" else "Vibration Detected"
            else -> null
        }
    val friendlyState: String?
        get() = if (isAlarmControlPanel) when (state) {
            "armed_away" -> "Armed Away"
            "disarmed" -> "Disarmed"
            "armed_home" -> "Armed Home"
            "pending" -> "Pending"
            else -> "Pending"
        }
        else if (isSwitch || isLight || isAutomation || isScript || isInputBoolean || isMediaPlayer || isGroup) state?.toUpperCase()
        else if (isBinarySensor) if (state == "off") "离线" else "在线"
        else if (isSun) if (state == "above_horizon") "日出" else "日落"
        else if (isDeviceTracker) if (state == "home") "在家" else "外出"
        else if (deviceClassState != null) deviceClassState
        else state
    val isStateful: Boolean
        get() = attributes?.isStateful ?: true
    val isCurrentStateActive: Boolean
        get() = state?.toUpperCase() == "ON"
    val friendlyStateRow: String
        get() = if (isAnySensors && attributes?.unitOfMeasurement != null) String.format(Locale.ENGLISH, "%s %s", state, attributes?.unitOfMeasurement) else friendlyState ?: ""
    val isActivated: Boolean
        get() = if (isMediaPlayer) state?.toUpperCase() != "OFF" else if (isSun) false else if (isDeviceTracker) state?.toUpperCase() == "HOME" else state?.toUpperCase() == "ON"
    val nextState: String
        get() = "turn_" + if (isCurrentStateActive) "off" else "on"
    val isToggleable: Boolean
        get() = isSwitch || isLight || isAutomation || isScript || isInputBoolean || isGroup || isFan || isVacuum
    val isCircle: Boolean
        get() = isSensor || isSun || isAnySensors || isDeviceTracker || isAlarmControlPanel
    val location: GpsUtil.LatLng?
        get() = if ((isDeviceTracker || isZone) && attributes?.latitude != null && attributes?.longitude != null) GpsUtil.LatLng(attributes!!.latitude!!.toDouble(), attributes!!.longitude!!.toDouble()) else null
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
}