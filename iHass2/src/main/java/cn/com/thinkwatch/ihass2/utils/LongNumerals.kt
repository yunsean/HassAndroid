package cn.com.thinkwatch.ihass2.utils

import java.util.regex.Pattern

object LongNumerals {
    private fun isOrdinal(ch: Char): Boolean {
        return ch == '一' || ch == '二' || ch == '三' || ch == '四' || ch == '五' || ch == '六' || ch == '七' || ch == '八' || ch == '九' || ch == '〇' ||
                ch == '壹' || ch == '贰' || ch == '叁' || ch == '肆' || ch == '伍' || ch == '陆' || ch == '柒' || ch == '捌' || ch == '玖' || ch == '零' ||
                ch == '两'
    }

    private fun isMultiple(ch: Char): Boolean {
        return ch == '十' || ch == '百' || ch == '千' ||
                ch == '拾' || ch == '佰' || ch == '仟'
    }

    private fun isComposite(ch: Char): Boolean {
        return ch == '万' || ch == '亿' || ch == '萬'
    }

    private fun ordinal(ch: Char): Long {
        when (ch) {
            '一', '壹' -> return 1
            '二', '两', '贰' -> return 2
            '三', '叁' -> return 3
            '四', '肆' -> return 4
            '五', '伍' -> return 5
            '六', '陆' -> return 6
            '七', '柒' -> return 7
            '八', '捌' -> return 8
            '九', '玖' -> return 9
            '〇', '零' -> return 0
            else -> throw IllegalArgumentException()
        }
    }
    private fun multiple(ch: Char): Long {
        when (ch) {
            '十', '拾' -> return 10
            '百', '佰' -> return 100
            '千', '仟' -> return 1000
            else -> throw IllegalArgumentException()
        }
    }
    private fun composite(ch: Char, base: Long): Long {
        when (ch) {
            '万', '萬' -> return base / 100000 * 100000L + base % 100000 * 10000
            '亿' -> return base / 1000000000 * 1000000000 + base % 1000000000 * 100000000
            else -> throw IllegalArgumentException()
        }
    }
    fun convert(number: String): Long {
        var result: Long = 0
        var ordinal: Long = 0
        for (index in 0 until number.length) {
            val ch = number[index]
            if (isOrdinal(ch)) {
                result += ordinal
                ordinal = ordinal(ch)
            } else if (isMultiple(ch)) {
                ordinal *= multiple(ch)
            } else if (isComposite(ch)) {
                result = composite(ch, result + ordinal)
                ordinal = 0
            } else {
                break
            }
        }
        result += ordinal
        return result
    }

    val numberPattern by lazy { Pattern.compile("([壹贰叁肆伍陆柒捌玖零拾佰仟萬一二三四五六七八九〇十百千万亿两]+)") }
}
