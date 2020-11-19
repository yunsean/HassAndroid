package cn.com.thinkwatch.ihass2.enums

import cn.com.thinkwatch.ihass2.R

enum class AlarmSoundType (val desc: String,
                           val resId: Int) {
    quiet("静默", 0),
    airAlarm("防空警报", R.raw.airalarm),
    alarm("警车", R.raw.alarm),
    doorbell("门铃", R.raw.doorbell),
    exception("告警提示", R.raw.exception),
    airLeak("漏气", R.raw.airleak),
    waterLeak("漏水", R.raw.waterleak),
    waterPop("水泡", R.raw.waterpop),
    ding("叮", R.raw.ding),
    windBell("风铃", R.raw.windbell)
}