package cn.com.thinkwatch.ihass2.view

import android.content.Context
import android.support.v4.view.ViewPager
import android.support.v4.widget.SwipeRefreshLayout
import android.util.AttributeSet
import android.view.View

class MultiSwipeRefreshLayout : SwipeRefreshLayout {
    private var swipeableChildren: Array<View?>? = null
    private var isIdle = true

    constructor(context: Context) : super(context) {}
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    fun setSwipeableChildren(vararg ids: Int) {
        assert(ids != null)
        swipeableChildren = arrayOfNulls(ids.size)
        for (i in ids.indices) swipeableChildren!![i] = findViewById(ids[i])
    }

    fun setSwipeableChildren(viewPager: ViewPager) {
        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {}
            override fun onPageScrollStateChanged(state: Int) {
                isIdle = state == ViewPager.SCROLL_STATE_IDLE
            }
        })
    }
    override fun canChildScrollUp(): Boolean {
        return !isIdle
    }
    private fun canViewScrollUp(view: View): Boolean {
        return view.canScrollVertically(-1)
    }
}