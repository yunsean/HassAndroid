package cn.com.thinkwatch.ihass2.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.support.v4.content.ContextCompat
import android.text.Layout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import cn.com.thinkwatch.ihass2.R
import org.jetbrains.anko.sp


class UseRatioView(context: Context, attr: AttributeSet?): View(context, attr) {
    constructor(context: Context): this(context, null)
    private val paint: Paint
    private val textPaint: TextPaint
    var segments = listOf<Segment>()
        set(value) {
            field = value
            invalidate()
        }
    var colorMap: Map<String, Int> = mapOf("home" to R.color.trackerInhome, "not_home" to R.color.trackerOutter)
    var textMap: Map<String, String> = mapOf("home" to "在家", "not_home" to "外出")
    var defaultText: String? = null
    init {
        paint = Paint()
        textPaint = TextPaint()
        textPaint.isAntiAlias = true
        textPaint.isSubpixelText = true
        textPaint.style = Paint.Style.FILL
        textPaint.color = Color.WHITE
        textPaint.textSize = context.sp(12).toFloat()
        textPaint.textAlign = Paint.Align.CENTER
    }

    data class Segment(val position: Int,
                       val status: String?)

    private val stateColors = mutableMapOf<String, Int>()
    private fun colorForState(state: String): Int {
        var color = stateColors.get(state)
        if (color != null) return color
        if (state.isBlank()) {
            color = 0xffeeeeee.toInt()
        } else {
            color = 0
            val scale = Math.pow(0xffffff.toDouble(), 1.0 / state.length).toInt()
            state.forEach { color = (color!! * scale) + (it.toInt() * scale / 128) }
        }
        color = (0xff000000 + (color!! and 0x00ffffff)).toInt()
        stateColors.put(state, color!!)
        return color!!
    }
    override fun draw(canvas: Canvas?) {
        super.draw(canvas)
        val width = this.width
        val height = this.height
        var prevState: String? = null
        var prevBegin = 0
        paint.color = 0xfff2f2f2.toInt()
        canvas?.drawRect(0F, 0F, width.toFloat(), height.toFloat(), paint)
        segments.forEachIndexed { index, segment ->
            var end = segment.position * width / 100
            if (end != 0 && end - prevBegin < 1) end++
            paint.color = colorMap.get(prevState)?.let { ContextCompat.getColor(context, it) } ?: colorForState(prevState ?: "")
            val rect = Rect(prevBegin, 0, end, height)
            canvas?.drawRect( rect, paint)
            drawText(canvas, rect, textMap.get(prevState) ?: defaultText ?: prevState)
            prevBegin = end
            prevState = segment.status
        }
    }
    private fun drawText(canvas: Canvas?, rect: Rect, text: String?) {
        if (text.isNullOrBlank()) return
        var width = Layout.getDesiredWidth(text, textPaint).toInt()
        if (width * 3 / 2 > rect.width()) return
        val fontMetrics = textPaint.fontMetrics
        val top = fontMetrics.top
        val bottom = fontMetrics.bottom
        val baseLineY = rect.centerY() - top.toInt() / 2 - bottom.toInt() / 2
        canvas?.drawText(text, rect.centerX().toFloat(), baseLineY.toFloat(), textPaint)
    }
}