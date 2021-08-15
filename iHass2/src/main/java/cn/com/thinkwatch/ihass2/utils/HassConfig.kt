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

        const val Connect_ScreenOff = "connect.screenOff"

        const val Hass_HostUrl = "hass.HostUrl"
        const val Hass_Password = "hass.Password"
        const val Hass_Token = "hass.Token"
        const val Hass_LocalUrl = "hass.LocalUrl"
        const val Hass_LocalBssid = "hass.LocalBssid"

        const val Ui_PullRefresh = "ui.pullRefresh"
        const val Ui_HomePanels = "ui.panelShowEditor"
        const val Ui_FontScale = "ui.fontScale"
        const val Ui_PageDelay = "ui.pageDelay"

        const val Gps_Logger = "gps.logger"
        const val Gps_DeviceName = "gps.deviceName"
        const val Gps_DeviceId = "gps.deviceId"
        const val Gps_Password = "gps.password"
        const val Gps_AppLogger = "gps.appLogger"
        const val Gps_WebHookId = "gps.webHookId"

        const val Probe_NfcCard = "probe.nfcCard"
        const val Probe_BluetoothBle = "probe.bluetoothBle"
        const val Probe_Wifi = "probe.wifi"
        const val Probe_Gps = "probe.gps"

        const val Speech_DoubleHomeKey = "speech.doubleHomeKey"
        const val Speech_TripleHomeKey = "speech.tripleHomeKey"
        const val Speech_Notification = "speech.notification"
        const val Speech_ShowWakeup = "speech.showWakeup"

        const val Speech_ScreenOnMode = "speech.screenOnMode"
        const val Speech_ScreenOnWifi = "speech.screenOnWifi"
        const val Speech_ScreenOnBluetooth = "speech.screenOnBluetooth"
        const val Speech_ScreenOnCharging = "speech.screenOnCharging"

        const val Speech_ScreenOffMode = "speech.screenOffMode"
        const val Speech_ScreenOffWifi = "speech.screenOffWifi"
        const val Speech_ScreenOffBluetooth = "speech.screenOffBluetooth"
        const val Speech_ScreenOffCharging = "speech.screenOffCharging"

        const val Speech_FromBluetooth = "speech.recordFromBluetooth"
        const val Speech_HeadsetWakeup = "speech.headsetWakeup"
        const val Speech_NoWakeupLock = "speech.noWakeupLock"
        const val Speech_VoiceOpenApp = "speech.voiceOpenApp"
        const val Speech_VoiceContact = "speech.voiceContact"

        const val Aux_ProtectEye = "aux.protectEye"
        const val Aux_ProtectEye_Color = "aux.protectEyeColor"

        const val Wakeup_Forbid = 0
        const val Wakeup_Condition = 1
        const val Wakeup_Always = -1

        const val Album_Used = "album.used"
        const val Album_UserStub = "album.userStub"
        const val Album_Folders = "album.folders"
        const val Album_AutoUpload = "album.autoUpload"
        const val Album_UploadWifi = "album.uploadWifi"
        const val Album_ScanInterval = "album.scanInterval"
        const val Album_Override = "album.override"
        const val Album_EarlyTime = "album.earlyTime"
        const val Album_LastScan = "album.lastScan"

        const val UploadMode_None = 0
        const val UploadMode_SomeWifi = 1
        const val UploadMode_AllWifi = 2
        const val UploadMode_Always = 3
    }
}

inline val Context.cfg: HassConfig
    get() = HassConfig.INSTANCE
inline val Fragment.cfg: HassConfig
    get() = HassConfig.INSTANCE
