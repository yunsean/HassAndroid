package cn.com.thinkwatch.ihass2.view

import android.content.Context
import android.util.AttributeSet

class AutoScrollTextView : android.support.v7.widget.AppCompatTextView {
    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {}

    override fun isFocused(): Boolean {
        return true
    }
}
