package cn.com.thinkwatch.ihass2.model.album

data class AlbumResult(val result: String? = null,
                       val md5: String? = null,
                       val exist: Boolean? = null,
                       val mtime: Long? = null,
                       val size: Long? = null,
                       val message: String? = null)