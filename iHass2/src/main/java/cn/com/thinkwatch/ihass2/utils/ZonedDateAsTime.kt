package cn.com.thinkwatch.ihass2.utils

import cn.com.thinkwatch.ihass2.model.AttributeRender
import com.yunsean.dynkotlins.extensions.ktime
import java.text.SimpleDateFormat

class ZonedDateAsTime: AttributeRender {
    override fun render(value: Any?): String {
        if (value is String) {
            val date = try {
                if (value.length == 32) SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZZZZZ").parse(value)
                else if (value.length == 25) SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ").parse(value)
                else null
            } catch (ex: Exception) {
                ex.printStackTrace()
                null
            }
            return date.ktime() ?: ""
        }
        return value.toString()
    }
}