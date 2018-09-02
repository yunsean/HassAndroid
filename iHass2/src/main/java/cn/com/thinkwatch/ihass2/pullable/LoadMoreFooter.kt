package cn.com.thinkwatch.ihass2.pullable

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import cn.com.thinkwatch.ihass2.R
import com.dylan.common.sketch.Colors
import com.dylan.common.sketch.Sketch
import com.dylan.dyn3rdparts.swipetoloadlayout.SwipeLoadMoreTrigger
import com.dylan.dyn3rdparts.swipetoloadlayout.SwipeTrigger

class LoadMoreFooter @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr), SwipeTrigger, SwipeLoadMoreTrigger {
    private val mTriggerOffset: Int
    private val ringProgressDrawable: RingProgressDrawable

    init {
        ringProgressDrawable = RingProgressDrawable(context)
        ringProgressDrawable.setColors(
                Colors.getColor(context, R.color.refreshBlue),
                Colors.getColor(context, R.color.refreshRed),
                Colors.getColor(context, R.color.refreshYellow),
                Colors.getColor(context, R.color.refreshGreen))
        mTriggerOffset = context.resources.getDimensionPixelOffset(R.dimen.pullable_loadmore_trigger_offset)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        Sketch.set_iv(this, R.id.loadmore_view, ringProgressDrawable)
    }
    override fun onLoadMore() {
        ringProgressDrawable.start()
    }
    override fun onPrepare() {}
    override fun onMove(y: Int, isComplete: Boolean, automatic: Boolean) {
        if (!isComplete) {
            ringProgressDrawable.setPercent(-y / mTriggerOffset.toFloat())
        }
    }
    override fun onRelease() {
    }
    override fun onComplete() {
        ringProgressDrawable.stop()
    }
    override fun onReset() {}
}
