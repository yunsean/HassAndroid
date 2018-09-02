package cn.com.thinkwatch.ihass2.pullable

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

import com.dylan.common.sketch.Colors
import com.dylan.common.sketch.Sketch
import com.dylan.dyn3rdparts.swipetoloadlayout.SwipeRefreshTrigger
import com.dylan.dyn3rdparts.swipetoloadlayout.SwipeTrigger
import cn.com.thinkwatch.ihass2.R

class RefreshHeader @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr), SwipeTrigger, SwipeRefreshTrigger {
    private val mTriggerOffset: Int
    private val ringProgressDrawable: RingProgressDrawable

    init {
        ringProgressDrawable = RingProgressDrawable(context)
        ringProgressDrawable.setColors(
                Colors.getColor(context, R.color.refreshBlue),
                Colors.getColor(context, R.color.refreshRed),
                Colors.getColor(context, R.color.refreshYellow),
                Colors.getColor(context, R.color.refreshGreen))
        mTriggerOffset = context.resources.getDimensionPixelOffset(R.dimen.pullable_refresh_trigger_offset)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        Sketch.set_iv(this, R.id.refreshing_view, ringProgressDrawable)
    }
    override fun onRefresh() {
        ringProgressDrawable.start()
    }
    override fun onPrepare() {}
    override fun onMove(y: Int, isComplete: Boolean, automatic: Boolean) {
        if (!isComplete) {
            ringProgressDrawable.setPercent(y / mTriggerOffset.toFloat())
        }
    }
    override fun onRelease() {}
    override fun onComplete() {
        ringProgressDrawable.stop()
    }
    override fun onReset() {}
}