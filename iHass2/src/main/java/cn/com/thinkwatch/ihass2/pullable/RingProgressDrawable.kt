package cn.com.thinkwatch.ihass2.pullable

import android.content.Context
import android.graphics.*

class RingProgressDrawable(context: Context) : ProgressDrawable(context) {

    private val mPaint: Paint
    private val mPath: Path
    private val mBounds: RectF
    private var mAlpha: Int = 0
    private var mDegrees: Float = 0.toFloat()
    private var mAngle: Float = 0.toFloat()
    private var mColorIndex: Int = 0
    private val mPercent: Float = 0.toFloat()

    init {
        mBounds = RectF()
        mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mPaint.style = Paint.Style.STROKE
        mPaint.strokeWidth = dp2px(DEFAULT_BORDER_WIDTH).toFloat()
        mPath = Path()
        mPaint.color = Color.WHITE
    }

    override fun setPercent(percent: Float, isUser: Boolean) {
        var percent = percent
        if (percent >= 1) {
            percent = 1f
        }
        val colors = getColors()
        if (colors != null) mPaint.color = colors[0]
        mAngle = DEFAULT_FINAL_DEGREES * percent - 0.001f
        mAlpha = (255 * percent).toInt()
        mDegrees = 360 * percent
        invalidateSelf()
    }

    override fun start() {
        isRunning = true
        post(mAnimRunnable)
    }

    override fun stop() {
        isRunning = false
        removeCallBacks(mAnimRunnable)
        mAngle = 0f
        mDegrees = 0f
    }

    private val mAnimRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                val colors = getColors()
                val length = colors?.size ?: 0
                mDegrees += 5f
                if (mDegrees >= 360) {
                    mDegrees = 0f
                    mColorIndex++
                    if (mColorIndex >= length) {
                        mColorIndex = 0
                    }
                    if (colors != null) {
                        mPaint.color = colors[mColorIndex]
                    }
                }
                mAngle = mDegrees
                invalidateSelf()
                postDelayed(this, ProgressDrawable.DELAY)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        mAlpha = alpha
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.rotate(mDegrees, (canvas.width / 2).toFloat(), (canvas.height / 2).toFloat())
        mPath.reset()
        val offset = dp2px(3).toFloat()
        val d = Math.min(canvas.width, canvas.height) - offset
        val left = dp2px(DEFAULT_BORDER_WIDTH) + offset
        val top = dp2px(DEFAULT_BORDER_WIDTH) + offset
        val right = d - dp2px(DEFAULT_BORDER_WIDTH)
        val bottom = d - dp2px(DEFAULT_BORDER_WIDTH)
        mBounds.set(left, top, right, bottom)
        mPath.arcTo(mBounds, DEFAULT_START_ANGLE.toFloat(), mAngle, true)
        mPaint.alpha = mAlpha
        canvas.drawPath(mPath, mPaint)
        canvas.restore()
    }

    companion object {
        private val DEFAULT_BORDER_WIDTH = 3
        private val DEFAULT_START_ANGLE = 270
        private val DEFAULT_FINAL_DEGREES = 360
    }
}