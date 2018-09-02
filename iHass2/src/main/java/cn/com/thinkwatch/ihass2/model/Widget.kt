package cn.com.thinkwatch.ihass2.model

import cn.com.thinkwatch.ihass2.enums.WidgetType
import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table

@Table(name = "HASS_WIDGETS")
data class Widget (@Column(name = "WIDGET_ID", isId = true, autoGen = false) var widgetId: Int = 0,
                   @Column(name = "WIDGET_TYPE") var widgetType: WidgetType = WidgetType.detail,
                   @Column(name = "BACK_COLOR") var backColor: Int = 0x00000000,
                   @Column(name = "NORMAL_COLOR") var normalColor: Int = 0xff656565.toInt(),
                   @Column(name = "ACTIVE_COLOR") var activeColor: Int = 0xff0288D1.toInt(),
                   @Column(name = "TEXT_SIZE") var textSize: Int = 12,
                   @Column(name = "IMAGE_SIZE") var imageSize: Int = 30)