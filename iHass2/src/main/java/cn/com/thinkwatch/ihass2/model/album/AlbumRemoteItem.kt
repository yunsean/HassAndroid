package cn.com.thinkwatch.ihass2.model.album

data class AlbumRemoteItem(override val name: String = "",
                           override val size: Long = 0L,
                           override val mtime: Long = 0L,
                           override var checked: Boolean = false,
                           var type: AlbumMediaType = AlbumMediaType.other) : AlbumItem {
    override fun url(userStub: String?, haUrl: String?, pathPrefix: String?): String {
        if (pathPrefix == null || haUrl == null || userStub == null) return ""
        val path = if (pathPrefix.endsWith("/")) pathPrefix + name else "$pathPrefix/$name"
        return "$haUrl/api/alumb/preview?user=${userStub}&path=${path}"
    }
}