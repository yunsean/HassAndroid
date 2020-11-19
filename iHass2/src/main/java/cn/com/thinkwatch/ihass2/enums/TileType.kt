package cn.com.thinkwatch.ihass2.enums

enum class TileType (override val code: Int) : BaseEnum {
    inherit(-1),
    tile(0),
    list(1),
    circle(2),
    square(3),
    list2(4)
}