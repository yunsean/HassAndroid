package cn.com.thinkwatch.ihass2.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import org.jetbrains.anko.sp



class UseRatioView(context: Context, attr: AttributeSet?): View(context, attr) {
    constructor(context: Context): this(context, null)
    private val paint: Paint
    private val textPaint: TextPaint
    var segments = listOf<Segment>()
        set
    var freeColor = 0xFF8C46E2.toInt()
    var usedColor = 0xFF46D5F5.toInt()
    var freeText = "外出"
    var usedText = "在家"
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
                       val used: Boolean)

    override fun draw(canvas: Canvas?) {
        super.draw(canvas)
        val width = this.width
        val height = this.height
        var prevState = false
        var prevBegin = 0
        paint.color = 0xfff2f2f2.toInt()
        canvas?.drawRect(0F, 0F, width.toFloat(), height.toFloat(), paint)
        segments.forEachIndexed { index, segment ->
            if (index == 0) prevState = !segment.used
            var end = segment.position * width / 100
            if (end != 0 && end - prevBegin < 1) end++
            paint.color = if (prevState) usedColor else freeColor
            val rect = Rect(prevBegin, 0, end, height)
            canvas?.drawRect( rect, paint)
            drawText(canvas, rect, if (prevState) usedText else freeText)
            prevBegin = end
            prevState = segment.used
        }
    }
    private fun drawText(canvas: Canvas?, rect: Rect, text: String) {
        var width = Layout.getDesiredWidth(text, textPaint).toInt()
        if (width * 3 / 2 > rect.width()) return
        val fontMetrics = textPaint.fontMetrics
        val top = fontMetrics.top
        val bottom = fontMetrics.bottom
        val baseLineY = rect.centerY() - top.toInt() / 2 - bottom.toInt() / 2
        canvas?.drawText(text, rect.centerX().toFloat(), baseLineY.toFloat(), textPaint)
    }
}