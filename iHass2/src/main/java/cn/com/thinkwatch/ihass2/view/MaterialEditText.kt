package cn.com.thinkwatch.ihass2.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet

class MaterialEditText : com.rengwuxian.materialedittext.MaterialEditText {

    constructor(context: Context) : super(context) {}
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}
    constructor(context: Context, attrs: AttributeSet, style: Int) : super(context, attrs, style) {}
    override fun onDraw(canvas: Canvas) {
        focusFraction = 1f
        focusFraction = floatingLabelFraction
        super.onDraw(canvas)
    }
}
