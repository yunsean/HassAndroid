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
    @Synchronized fun get(key: String, volatiled: Boolean = false): String? {
        var value: String? = null
        if (!volatiled) value = allConfigs.get(key)
        if (value != null) return value
        value = db.getConfig(key)
        value?.let { allConfigs.set(key, it) }
        return value
    }
    fun optString(key: String, volatiled: Boolean = false): String? = get(key, volatiled)
    fun getString(key: String, fallback: String = "", volatiled: Boolean = false): String = get(key, volatiled) ?: fallback
    fun optInt(key: String, volatiled: Boolean = false): Int? = get(key, volatiled)?.toIntOrNull()
    fun getInt(key: String, fallback: Int = 0, volatiled: Boolean = false): Int = get(key, volatiled)?.toIntOrNull() ?: fallback
    fun optBoolean(key: String, volatiled: Boolean = false): Boolean? = get(key, volatiled)?.toBoolean()
    fun getBoolean(key: String, fallback: Boolean = false, volatiled: Boolean = false): Boolean = get(key, volatiled)?.toBoolean() ?: fallback

    val haHostUrl: String
        get() = get(Hass_HostUrl) ?: ""
    val haPassword: String
        get() = get(Hass_Password) ?: ""

    private object Holder {
        val INSTANCE = HassConfig()
    }
    companion object {
        val INSTANCE: HassConfig by lazy { Holder.INSTANCE }

        val Hass_HostUrl = "hass.HostUrl"
        val Hass_Password = "hass.Password"
        val Ui_PullRefresh = "ui.pullRefresh"
        val Ui_HomePanels = "ui.homePanels"
        val Gps_Logger = "gps.logger"
        val Gps_DeviceName = "gps.deviceName"
        val Gps_DeviceId = "gps.deviceId"
        val Gps_Password = "gps.password"
    }
}

inline val Context.cfg: HassConfig
    get() = HassConfig.INSTANCE
inline val Fragment.cfg: HassConfig
    get() = HassConfig.INSTANCE
