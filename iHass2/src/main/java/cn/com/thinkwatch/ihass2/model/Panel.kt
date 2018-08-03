package cn.com.thinkwatch.ihass2.model

import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table

@Table(name = "HASS_PANELS")
data class Panel (@Column(name = "PANEL_NAME") var name: String = "",
                  @Column(name = "PANEL_ORDER") var order: Int = 0,
                  @Column(name = "PANEL_ID", isId = true, autoGen = true) var id: Long = 0)