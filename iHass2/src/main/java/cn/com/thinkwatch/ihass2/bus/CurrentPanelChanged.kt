package cn.com.thinkwatch.ihass2.bus

data class CurrentPanelChanged(val panelId: Long,
                               val name: String)