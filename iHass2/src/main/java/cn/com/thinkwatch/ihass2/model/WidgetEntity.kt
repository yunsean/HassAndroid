package cn.com.thinkwatch.ihass2.model

import com.google.gson.annotations.Expose
import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table

@Table(name = "HASS_WIDGET_ENTITIES")
data class WidgetEntity (@Expose @Column(name = "WIDGET_ID") var widgetId: Int = 0,
                         @Expose @Column(name = "ENTITY_ID") var entityId: String = "",
                         @Expose @Column(name = "DISPLAY_ORDER") var order: Int = 0,
                         @Expose @Column(name = "SHOW_NAME") var showName: String? = null,
                         @Expose @Column(name = "SHOW_ICON") var showIcon: String? = null,
                         @Expose @Column(name = "ID", isId = true, autoGen = true) var id: Long = 0L)