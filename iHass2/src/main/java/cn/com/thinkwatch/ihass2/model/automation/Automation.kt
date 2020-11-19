package cn.com.thinkwatch.ihass2.model.automation

class Automation(val id: String = "",
                 val alias: String = "",
                 val trigger: List<Trigger> = listOf(),
                 val condition: List<Condition> = listOf(),
                 val action: List<Action> = listOf())