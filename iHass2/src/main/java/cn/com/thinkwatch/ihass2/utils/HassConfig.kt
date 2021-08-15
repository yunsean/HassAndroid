package cn.com.thinkwatch.ihass2.utils

import android.content.Context
import android.support.v4.app.Fragment
import cn.com.thinkwatch.ihass2.db.LocalStorage

class HassConfig {

    private var allConfigs = mutableMapOf<String, String>()
    private val db = LocalStorage.instance
    init { reload() }

    @Synchronized fun reload() {
        allConfigs = db.allConfigs().associateBy({it.key}, {it.value}).toMutableMap()
    }
    @Synchronized fun set(key: String, value: Any?) {
        if (key.isBlank()) return
        val str = value?.toString() ?: ""
        db.setConfig(key, str)
        allConfigs.set(key, str)
    }
    @Synchronized fun get(key: String): String? {
        var value: String? = allConfigs.get(key)
        if (value != null) return value
        value = db.getConfig(key)
        value?.let { allConfigs.set(key, it) }
        return value
    }

    fun optString(key: String): String? = get(key)
    fun getString(key: String, fallback: String = ""): String = get(key) ?: fallback
    fun optInt(key: String): Int? = get(key)?.toIntOrNull()
    fun getInt(key: String, fallback: Int = 0): Int = get(key)?.toIntOrNull() ?: fallback
    fun optLong(key: String): Long? = get(key)?.toLongOrNull()
    fun getLong(key: String, fallback: Long = 0): Long = get(key)?.toLongOrNull() ?: fallback
    fun optBoolean(key: String): Boolean? = get(key)?.toBoolean()
    fun getBoolean(key: String, fallback: Boolean = false): Boolean = get(key)?.toBoolean() ?: fallback

    fun isInited() : Boolean {
        if (!get(Hass_HostUrl).isNullOrBlank()) return true
        return false
    }

    val haHostUrl: String
        get() = get(Hass_HostUrl) ?: ""
    val haPassword: String
        get() = get(Hass_Password) ?: ""
    val haToken: String
        get() = get(Hass_Token) ?: ""

    private object Holder {
        val INSTANCE = HassConfig()
    }
    companion object {
        val INSTANCE: HassConfig by lazy { Holder.INSTANCE }

        val Connect_ScreenOff = "connect.screenOff"

        val Hass_HostUrl = "hass.HostUrl"
        val Hass_Password = "hass.Password"
        val Hass_Token = "hass.Token"
        val Hass_LocalUrl = "hass.LocalUrl"
        val Hass_LocalBssid = "hass.LocalBssid"

        val Ui_PullRefresh = "ui.pullRefresh"
        val Ui_HomePanels = "ui.homePanels"
        val Ui_WebFrist = "ui.webFirst"
        val Ui_ShowTopbar = "ui.showTopbar"
        val Ui_ShowSidebar = "ui.showSidebar"
        val Ui_FontScale = "ui.fontScale"

        val Gps_Logger = "gps.logger"
        val Gps_DeviceName = "gps.deviceName"
        val Gps_DeviceId = "gps.deviceId"
        val Gps_Password = "gps.password"
        val Gps_AppLogger = "gps.appLogger"
        val Gps_WebHookId = "gps.webHookId"

        val Probe_NfcCard = "probe.nfcCard"
        val Probe_BluetoothBle = "probe.bluetoothBle"
        val Probe_Wifi = "probe.wifi"
        val Probe_Gps = "probe.gps"

        val Speech_DoubleHomeKey = "speech.doubleHomeKey"
        val Speech_TripleHomeKey = "speech.tripleHomeKey"
        val Speech_Notification = "speech.notification"
        val Speech_ShowWakeup = "speech.showWakeup"

        val Speech_ScreenOnMode = "speech.screenOnMode"
        val Speech_ScreenOnWifi = "speech.screenOnWifi"
        val Speech_ScreenOnBluetooth = "speech.screenOnBluetooth"
        val Speech_ScreenOnCharging = "speech.screenOnCharging"

        val Speech_ScreenOffMode = "speech.screenOffMode"
        val Speech_ScreenOffWifi = "speech.screenOffWifi"
        val Speech_ScreenOffBluetooth = "speech.screenOffBluetooth"
        val Speech_ScreenOffCharging = "speech.screenOffCharging"

        val Speech_FromBluetooth = "speech.recordFromBluetooth"
        val Speech_HeadsetWakeup = "speech.headsetWakeup"
        val Speech_NoWakeupLock = "speech.noWakeupLock"
        val Speech_VoiceOpenApp = "speech.voiceOpenApp"
        val Speech_VoiceContact = "speech.voiceContact"

        val Aux_ProtectEye = "aux.protectEye"
        val Aux_ProtectEye_Color = "aux.protectEyeColor"

        val Wakeup_Forbid = 0
        val Wakeup_Condition = 1
        val Wakeup_Always = -1

        val Album_Used = "album.used"
        val Album_UserStub = "album.userStub"
        val Album_Folders = "album.folders"
        val Album_AutoUpload = "album.autoUpload"
        val Album_UploadWifi = "album.uploadWifi"
        val Album_ScanInterval = "album.scanInterval"
        val Album_Override = "album.override"
        val Album_EarlyTime = "album.earlyTime"
        val Album_LastScan = "album.lastScan"

        val UploadMode_None = 0
        val UploadMode_SomeWifi = 1
        val UploadMode_AllWifi = 2
        val UploadMode_Always = 3
    }
}

inline val Context.cfg: HassConfig
    get() = HassConfig.INSTANCE
inline val Fragment.cfg: HassConfig
    get() = HassConfig.INSTANCE
