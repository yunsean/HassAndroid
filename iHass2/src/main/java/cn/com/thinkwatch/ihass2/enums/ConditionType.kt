package cn.com.thinkwatch.ihass2.enums

enum class ConditionType (val desc: String, val tips: String) {
    any("变化", ""),
    e("等于", "参考状态字符串"),
    ne("不等于", "参考状态字符串"),
    gt("大于", "参考状态浮点数"),
    gte("大于等于", "参考状态整数"),
    lt("小于", "参考状态浮点数"),
    lte("小于等于", "参考状态整数"),
    inc("增加/上升", "最小通知浮点数阈值（可选）"),
    dec("下降/降低", "最大通知浮点数阈值（可选）")
}