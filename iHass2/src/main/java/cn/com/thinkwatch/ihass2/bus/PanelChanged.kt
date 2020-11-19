package cn.com.thinkwatch.ihass2.bus

data class PanelChanged(val panelId: Long,
                        val isAdd: Boolean = false)