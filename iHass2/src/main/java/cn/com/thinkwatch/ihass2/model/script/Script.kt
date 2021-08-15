package cn.com.thinkwatch.ihass2.model.script

import cn.com.thinkwatch.ihass2.model.automation.Action

class Script(val alias: String = "",
             val sequence: List<Action> = listOf(),
             var mode: String = "single",
             var max: Int = 10)