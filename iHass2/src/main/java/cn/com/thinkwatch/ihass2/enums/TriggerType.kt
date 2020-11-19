package cn.com.thinkwatch.ihass2.enums

import cn.com.thinkwatch.ihass2.R

enum class TriggerType (override val code: Int, val iconResId: Int, val desc: String) : BaseEnum {
    nfc(0, R.drawable.ic_trigger_nfc, "NFC刷卡"),
    foundBluetooth(1, R.drawable.ic_trigger_bluetooth_found, "蓝牙区域进入"),
    loseBluetooth(2, R.drawable.ic_trigger_bluetooth_lose, "蓝牙区域离开"),
    enterGps(3, R.drawable.ic_trigger_gps_enter, "GPS区域进入"),
    leaveGps(4, R.drawable.ic_trigger_gps_leave, "GPS区域离开"),
    foundWifi(5, R.drawable.ic_trigger_wifi_found, "WIFI区域进入"),
    loseWifi(6, R.drawable.ic_trigger_wifi_lose, "WIFI区域离开"),
    screenOn(7, R.drawable.ic_lock_outline_black_24dp, "手机解锁"),
    screenOff(8, R.drawable.ic_lock_open_black_24dp, "手机锁屏")
}