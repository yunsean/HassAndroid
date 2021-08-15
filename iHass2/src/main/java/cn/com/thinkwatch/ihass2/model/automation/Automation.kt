package cn.com.thinkwatch.ihass2.model.automation

class Automation(var id: String = "",
                 var alias: String = "",
                 val trigger: List<Trigger> = listOf(),
                 val condition: List<Condition> = listOf(),
                 val action: List<Action> = listOf(),
                 var mode: String = "single",
                 var max: Int = 10)