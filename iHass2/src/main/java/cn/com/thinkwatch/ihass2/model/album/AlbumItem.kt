package cn.com.thinkwatch.ihass2.model.album

interface AlbumItem {
    val name: String
    val size: Long
    val mtime: Long?
    var checked: Boolean
    fun url(userStub: String?, haUrl: String?, pathPrefix: String?): String
}