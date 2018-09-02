package cn.com.thinkwatch.ihass2.model.broadcast

import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table


@Table(name = "XMLY_FAVORITES")
data class Favorite(@Column(name = "ID", isId = true, autoGen = false) var id: Int = 0,
                    @Column(name = "ENTITY_ID") var entityId: String = "",
                    @Column(name = "NAME") var name: String = "",
                    @Column(name = "COVER_SMALL") var coverSmall: String = "",
                    @Column(name = "PLAY_URL") var playUrl: String = "",
                    @Column(name = "PLAY_COUNT") var playCount: Int = 0)