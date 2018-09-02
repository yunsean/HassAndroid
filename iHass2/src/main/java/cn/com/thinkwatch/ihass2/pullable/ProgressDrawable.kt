package cn.com.thinkwatch.ihass2.pullable

import android.content.Context
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue

abstract class ProgressDrawable(protected val context: Context) : Drawable(), Animatable {
    private var mRunning: Boolean = false
    private var mHandler: Handler? = null
    private var mColors: IntArray? = null
    fun setColors(vararg colors: Int) {
        mColors = colors
    }
    fun getColors(): IntArray? {
        return mColors
    }
    fun setPercent(progress: Float) {
        setPercent(progress, false)
    }
    fun setRunning(running: Boolean) {
        this.mRunning = running
    }

    abstract fun setPercent(progress: Float, isUser: Boolean)
    override fun isRunning(): Boolean {
        return mRunning
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    protected fun post(runnable: Runnable) {
        postDelayed(runnable, 0)
    }

    protected fun postDelayed(runnable: Runnable, delayMillis: Int) {
        if (mHandler == null) {
            synchronized(ProgressDrawable::class.java) {
                mHandler = Handler(Looper.getMainLooper())
            }
        }
        mHandler!!.postDelayed(runnable, delayMillis.toLong())
    }

    protected fun removeCallBacks(runnable: Runnable) {
        if (mHandler != null) {
            mHandler!!.removeCallbacks(runnable)
        }
    }

    protected fun dp2px(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
    }

    companion object {

        val DELAY = 1000 / 60
    }
}
