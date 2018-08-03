package cn.com.thinkwatch.ihass2.model

import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table

@Table(name = "HASS_ENTITIES")
data class DbEntity (@Column(name = "ENTITY_ID") var entityId: String = "",
                     @Column(name = "RAW_JSON") var rawJson: String = "",
                     @Column(name = "ID", isId = true, autoGen = true) var id: Long = 0)