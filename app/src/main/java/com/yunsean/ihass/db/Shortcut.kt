package com.yunsean.ihass.db

import cn.com.thinkwatch.ihass2.enums.ItemType
import com.google.gson.annotations.Expose
import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table

@Table(name = "HASS_SHORTCUTS")
data class Shortcut (@Column(name = "ENTITY_ID") @Expose var entityId: String = "",
                     @Column(name = "DISPLAY_ORDER") @Expose var order: Int = 0,
                     @Column(name = "SHOW_NAME") @Expose var showName: String? = null,
                     @Column(name = "SHOW_ICON") @Expose var showIcon: String? = null,
                     @Column(name = "ITEM_TYPE") @Expose var itemType: ItemType = ItemType.entity,
                     @Column(name = "ID", isId = true, autoGen = true) var id: Long = 0)