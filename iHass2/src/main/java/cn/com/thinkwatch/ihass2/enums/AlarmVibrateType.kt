package cn.com.thinkwatch.ihass2.enums

enum class AlarmVibrateType (val desc: String,
                             val vibrate: LongArray) {
    quiet("无", longArrayOf()),
    weak("弱", longArrayOf(0, 200, 200, 200)),
    middle("中", longArrayOf(0, 500, 500, 500, 500, 500)),
    strong("强", longArrayOf(0, 1000, 1000, 1000, 1000, 1000, 1000)),
    superstrong("超强", longArrayOf(0, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000))
}