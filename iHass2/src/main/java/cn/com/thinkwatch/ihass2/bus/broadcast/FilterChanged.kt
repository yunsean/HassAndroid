package cn.com.thinkwatch.ihass2.bus.broadcast

data class XmlyFilterChange(val mode: FilterMode,
                            val value: Int) {
    companion object {
        enum class FilterMode{province, category}
    }
}