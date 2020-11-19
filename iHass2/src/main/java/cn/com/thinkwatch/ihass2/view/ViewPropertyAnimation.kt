package cn.com.thinkwatch.ihass2.view

import android.graphics.Camera
import android.os.Build
import android.support.annotation.FloatRange
import android.view.animation.Animation
import android.view.animation.Transformation

open class ViewPropertyAnimation : Animation() {

    private val mCamera = Camera()
    protected var mWidth = 0
    protected var mHeight = 0
    protected var mAlpha = 1.0f
    protected var mPivotX = 0.0f
    protected var mPivotY = 0.0f
    protected var mScaleX = 1.0f
    protected var mScaleY = 1.0f
    protected var mRotationX = 0.0f
    protected var mRotationY = 0.0f
    protected var mRotationZ = 0.0f
    protected var mTranslationX = 0.0f
    protected var mTranslationY = 0.0f
    protected var mTranslationZ = 0.0f
    protected var mCameraX = 0.0f
    protected var mCameraY = 0.0f
    protected var mCameraZ = -8.0f

    private var mFromAlpha = -1.0f
    private var mToAlpha = -1.0f

    fun fading(@FloatRange(from = 0.0, to = 1.0) fromAlpha: Float, @FloatRange(from = 0.0, to = 1.0) toAlpha: Float): ViewPropertyAnimation {
        mFromAlpha = fromAlpha
        mToAlpha = toAlpha
        return this
    }

    override fun initialize(width: Int, height: Int, parentWidth: Int, parentHeight: Int) {
        super.initialize(width, height, parentWidth, parentHeight)
        mWidth = width
        mHeight = height
    }

    override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
        super.applyTransformation(interpolatedTime, t)
        if (mFromAlpha >= 0 && mToAlpha >= 0) {
            mAlpha = mFromAlpha + (mToAlpha - mFromAlpha) * interpolatedTime
        }
    }

    protected fun applyTransformation(t: Transformation) {
        val m = t.matrix
        val w = mWidth.toFloat()
        val h = mHeight.toFloat()
        val pX = mPivotX
        val pY = mPivotY

        val rX = mRotationX
        val rY = mRotationY
        val rZ = mRotationZ
        if (rX != 0f || rY != 0f || rZ != 0f) {
            val camera = mCamera
            camera.save()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
                camera.setLocation(mCameraX, mCameraY, mCameraZ)
            }
            if (mTranslationZ != 0f) {
                camera.translate(0f, 0f, mTranslationZ)
            }
            camera.rotateX(rX)
            camera.rotateY(rY)
            camera.rotateZ(-rZ)
            camera.getMatrix(m)
            camera.restore()
            m.preTranslate(-pX, -pY)
            m.postTranslate(pX, pY)
        }

        val sX = mScaleX
        val sY = mScaleY
        if (sX != 1.0f || sY != 1.0f) {
            m.postScale(sX, sY)
            val sPX = -(pX / w) * (sX * w - w)
            val sPY = -(pY / h) * (sY * h - h)
            m.postTranslate(sPX, sPY)
        }

        m.postTranslate(mTranslationX, mTranslationY)
        t.alpha = mAlpha
    }
}
