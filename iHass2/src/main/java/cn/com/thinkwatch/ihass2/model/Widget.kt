package cn.com.thinkwatch.ihass2.model

import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table

@Table(name = "HASS_WIDGETS")
data class Widget (@Column(name = "ENTITY_ID") var entityId: String = "",
                   @Column(name = "FRIENDLY_STATE") var friendlyState: String = "",
                   @Column(name = "FRIENDLY_NAME") var friendlyName: String = "",
                   @Column(name = "LAST_UPDATED") var lastUpdated: Int = 1,
                   @Column(name = "WIDGET_ID", isId = true, autoGen = true) var id: Long = 0)