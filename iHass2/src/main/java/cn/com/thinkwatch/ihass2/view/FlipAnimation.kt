package cn.com.thinkwatch.ihass2.view

import android.view.animation.Transformation

open class FlipAnimation private constructor(protected val mDirection: Int, 
                                             protected val mEnter: Boolean,
                                             duration: Long) : ViewPropertyAnimation() {

    init {
        setDuration(duration)
    }

    private class VerticalFlipAnimation(direction: Int, enter: Boolean, duration: Long) : FlipAnimation(direction, enter, duration) {
        override fun initialize(width: Int, height: Int, parentWidth: Int, parentHeight: Int) {
            super.initialize(width, height, parentWidth, parentHeight)
            mPivotX = width * 0.5f
            mPivotY = if (mEnter == (mDirection == UP)) 0.0f else height.toFloat()
            mCameraZ = -height * 0.015f
        }

        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            var value = if (mEnter) interpolatedTime - 1.0f else interpolatedTime
            if (mDirection == DOWN) value *= -1.0f
            mRotationX = value * 180.0f
            mTranslationY = -value * mHeight
            super.applyTransformation(interpolatedTime, t)
            if (mEnter) mAlpha = if (interpolatedTime <= 0.5f) 0.0f else 1.0f
            else mAlpha = if (interpolatedTime <= 0.5f) 1.0f else 0.0f
            applyTransformation(t)
        }
    }

    private class HorizontalFlipAnimation(direction: Int, enter: Boolean, duration: Long) : FlipAnimation(direction, enter, duration) {

        override fun initialize(width: Int, height: Int, parentWidth: Int, parentHeight: Int) {
            super.initialize(width, height, parentWidth, parentHeight)
            mPivotX = if (mEnter == (mDirection == LEFT)) 0.0f else width.toFloat()
            mPivotY = height * 0.5f
            mCameraZ = -width * 0.015f
        }

        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            var value = if (mEnter) interpolatedTime - 1.0f else interpolatedTime
            if (mDirection == RIGHT) value *= -1.0f
            mRotationY = -value * 180.0f
            mTranslationX = -value * mWidth
            super.applyTransformation(interpolatedTime, t)
            if (mEnter) mAlpha = if (interpolatedTime <= 0.5f) 0.0f else 1.0f
            else mAlpha = if (interpolatedTime <= 0.5f) 1.0f else 0.0f
            applyTransformation(t)
        }

    }

    companion object {
        val UP = 1
        val DOWN = 2
        val LEFT = 3
        val RIGHT = 4
        fun create(direction: Int, enter: Boolean, duration: Long): FlipAnimation {
            when (direction) {
                UP, DOWN -> return VerticalFlipAnimation(direction, enter, duration)
                LEFT, RIGHT -> return HorizontalFlipAnimation(direction, enter, duration)
                else -> return HorizontalFlipAnimation(direction, enter, duration)
            }
        }
    }

}
