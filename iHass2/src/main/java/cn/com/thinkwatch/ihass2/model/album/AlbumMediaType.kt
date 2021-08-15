package cn.com.thinkwatch.ihass2.model.album

enum class AlbumMediaType(val text: String, val color: Int) {
    image("\uf2e9", 0xff487AB5.toInt()),
    movie("\uf7dd", 0xffFF9800.toInt()),
    other("\uf219", 0xff8BC34A.toInt()),
    folder("\uf256", 0xff3F51B5.toInt())
}