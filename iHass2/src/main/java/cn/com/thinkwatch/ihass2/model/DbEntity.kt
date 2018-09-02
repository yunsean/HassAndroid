package cn.com.thinkwatch.ihass2.model

import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table

@Table(name = "HASS_ENTITIES")
data class DbEntity (@Column(name = "ENTITY_ID", isId = true, autoGen = false) var entityId: String = "",
                     @Column(name = "RAW_JSON") var rawJson: String = "",
                     @Column(name = "VERSION") var version: Long = 0)