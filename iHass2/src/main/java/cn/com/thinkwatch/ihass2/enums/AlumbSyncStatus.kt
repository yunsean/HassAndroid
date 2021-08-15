package cn.com.thinkwatch.ihass2.enums

enum class AlbumSyncStatus (override val code: Int, val desc: String) : BaseEnum {
    waiting(0, "排队"),
    succeed(1, "完成"),
    failed(2, "失败"),
    working(3, "传输中"),
    download(4, "已下载")
}