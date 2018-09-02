package cn.com.thinkwatch.ihass2.model

import com.google.gson.annotations.Expose
import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table

@Table(name = "HASS_WIDGET_ENTITIES")
data class WidgetEntity (@Column(name = "WIDGET_ID") var widgetId: Int = 0,
                         @Column(name = "ENTITY_ID") var entityId: String = "",
                         @Column(name = "DISPLAY_ORDER") @Expose var order: Int = 0,
                         @Column(name = "SHOW_NAME") @Expose var showName: String? = null,
                         @Column(name = "SHOW_ICON") @Expose var showIcon: String? = null,
                         @Column(name = "ID", isId = true, autoGen = true) var id: Long = 0L)