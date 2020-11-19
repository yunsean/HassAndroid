package cn.com.thinkwatch.ihass2.model

import com.google.gson.annotations.Expose
import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table

@Table(name = "HASS_NOTIFICATIONS")
data class Notification (@Column(name = "ENTITY_ID") @Expose var entityId: String = "",
                         @Column(name = "DISPLAY_ORDER") @Expose var order: Int = 0,
                         @Column(name = "SHOW_ICON") @Expose var showIcon: String? = null,
                         @Column(name = "SHOW_NAME") @Expose var showName: String? = null,
                         @Column(name = "ID", isId = true, autoGen = true) var id: Long = 0)