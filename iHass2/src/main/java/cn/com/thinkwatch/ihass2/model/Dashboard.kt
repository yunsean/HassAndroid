package cn.com.thinkwatch.ihass2.model

import cn.com.thinkwatch.ihass2.enums.ItemType
import cn.com.thinkwatch.ihass2.enums.TileType
import com.google.gson.annotations.Expose
import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table

@Table(name = "HASS_DASHBOARDS")
data class Dashboard (@Column(name = "PANEL_ID") var panelId: Long = 0,
                      @Column(name = "ENTITY_ID") @Expose var entityId: String = "",
                      @Column(name = "DISPLAY_ORDER") @Expose var order: Int = 0,
                      @Column(name = "SHOW_NAME") @Expose var showName: String? = null,
                      @Column(name = "SHOW_ICON") @Expose var showIcon: String? = null,
                      @Column(name = "COLUMN_COUNT") @Expose var columnCount: Int = 1,
                      @Column(name = "ITEM_TYPE") @Expose var itemType: ItemType = ItemType.entity,
                      @Column(name = "TILE_TYPE") @Expose var tileType: TileType = TileType.inherit,
                      @Column(name = "ID", isId = true, autoGen = true) var id: Long = 0)