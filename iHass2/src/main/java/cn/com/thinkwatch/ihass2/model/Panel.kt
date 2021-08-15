package cn.com.thinkwatch.ihass2.model

import com.google.gson.annotations.Expose
import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table

@Table(name = "HASS_PANELS")
data class Panel (@Column(name = "PANEL_NAME") var name: String = "",
                  @Column(name = "PANEL_ORDER") var order: Int = 0,
                  @Column(name = "PANEL_SPELL") var spell: String = "",
                  @Column(name = "PANEL_SIMILAR") var similar: String = "",
                  @Column(name = "PANEL_ICON") @Expose var icon: String? = null,
                  @Column(name = "BACK_IMAGE") @Expose var backImage: String? = null,
                  @Column(name = "TILE_ALPHA") @Expose var tileAlpha: Float? = null,
                  @Column(name = "STUBBORN_CLASS") @Expose var stubbornClass: String? = null,
                  @Column(name = "PANEL_ID", isId = true, autoGen = true) var id: Long = 0)