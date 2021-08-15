package cn.com.thinkwatch.ihass2.model.album

import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table
import java.util.*

@Table(name = "HASS_ALBUM_DOWNLOAD")
data class AlbumDownloadItem (@Column(name = "NAME") override var name: String = "",
                              @Column(name = "PATH") var path: String = "",
                              @Column(name = "USER") var userStub: String = "",
                              @Column(name = "SIZE") override var size: Long = 0L,
                              @Column(name = "FAILED") var failed: Boolean = false,
                              @Column(name = "REASON") var reason: String? = null,
                              @Column(name = "TIME") var addTime: Date = Date(),
                              @Column(name = "LOCALPATH") var localPath: String = "",
                              @Column(name = "ID", isId = true, autoGen = true) var id: Long = 0L,
                              @Transient override var checked: Boolean = false,
                              @Transient var downloading: Boolean = false): AlbumItem {
    override val mtime: Long? = null
    override fun url(userStub: String?, haUrl: String?, pathPrefix: String?): String {
        if (pathPrefix == null || haUrl == null || userStub == null) return ""
        return "$haUrl/api/alumb/preview?user=${userStub}&path=${path}"
    }
}