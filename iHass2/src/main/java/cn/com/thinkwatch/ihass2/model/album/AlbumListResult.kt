package cn.com.thinkwatch.ihass2.model.album

data class AlbumListResult(val folders: List<AlbumRemoteItem>? = null,
                           val files: List<AlbumRemoteItem>? = null)