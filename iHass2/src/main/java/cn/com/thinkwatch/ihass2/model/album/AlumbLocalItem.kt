package cn.com.thinkwatch.ihass2.model.album

import cn.com.thinkwatch.ihass2.enums.AlbumSyncStatus
import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table
import java.util.*

@Table(name = "HASS_ALBUM_ITEM")
data class AlbumLocalItem (@Column(name = "NAME") override var name: String = "",
                           @Column(name = "PATH") var path: String = "",
                           @Column(name = "SIZE") override var size: Long = 0L,
                           @Column(name = "MTIME") override var mtime: Long = 0L,
                           @Column(name = "STATUS") var status: AlbumSyncStatus = AlbumSyncStatus.waiting,
                           @Column(name = "TIME") var time: Date = Date(),
                           @Column(name = "REASON") var reason: String = "",
                           @Column(name = "ID", isId = true, autoGen = true) var id: Long = 0L,
                           @Column(name = "OVERRIDE") var override: Boolean = false,
                           @Column(name = "RETRIED") var retried: Int = 0,
                           @Transient override var checked: Boolean = false,
                           @Transient var md5: String? = null,
                           @Transient var type: AlbumMediaType = AlbumMediaType.other): AlbumItem {
    override fun url(userStub: String?, haUrl: String?, pathPrefix: String?): String {
        return path
    }
}