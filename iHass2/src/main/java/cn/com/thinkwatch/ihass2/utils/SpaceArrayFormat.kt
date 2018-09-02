package cn.com.thinkwatch.ihass2.utils

import cn.com.thinkwatch.ihass2.R.id.value
import cn.com.thinkwatch.ihass2.model.AttributeRender
import com.yunsean.dynkotlins.extensions.actualType

class SpaceArrayFormat: AttributeRender {
    override fun render(rawValue: Any?): String {
        if (rawValue is List<*> && rawValue.size > 0 && rawValue.javaClass.actualType(0) == Long::class.java) {
            val value = (rawValue as List<Long>).get(0)
            if (value > 1_000_000_000_000 * 10) return String.format("%dTB", value / 1_000_000_000_000L)
            else if (value > 1_000_000_000_000L) return String.format("%.1fTB", value / 1_000_000_000_000f)
            else if (value > 1_000_000_000L * 10) return String.format("%dGB", value / 1_000_000_000L)
            else if (value > 1_000_000_000L) return String.format("%.1fGB", value / 1_000_000_000f)
            else if (value > 1_000_000 * 10) return String.format("%dMB", value / 1_000_000)
            else if (value > 1_000_000) return String.format("%.1fMB", value / 1_000_000f)
            else if (value > 1_000 * 10) return String.format("%dKB", value / 1_000)
            else if (value > 1_000) return String.format("%.1fKB", value / 1_000f)
            else return String.format("%dB", value)
        }
        return value.toString()
    }
}