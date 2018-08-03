package cn.com.thinkwatch.ihass2.enums

enum class ItemType (override val code: Int) : BaseEnum {
    blank(0),
    divider(1),
    entity(2)
}