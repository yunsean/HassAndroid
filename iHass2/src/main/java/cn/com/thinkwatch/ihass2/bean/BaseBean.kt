package cn.com.thinkwatch.ihass2.bean

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import cn.com.thinkwatch.ihass2.model.JsonEntity

abstract class BaseBean(var entity: JsonEntity) {
    abstract fun layoutResId(): Int
    abstract fun bindToView(itemView: View, context: Context)

    @SuppressLint("ClickableViewAccessibility")
    protected fun getTouchListener(upperView: View): View.OnTouchListener {
        return View.OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val scaleDownX = ObjectAnimator.ofFloat(upperView, "scaleX", 0.95f)
                    val scaleDownY = ObjectAnimator.ofFloat(upperView, "scaleY", 0.95f)
                    scaleDownX.duration = 200
                    scaleDownY.duration = 200
                    val scaleDown = AnimatorSet()
                    scaleDown.play(scaleDownX).with(scaleDownY)
                    scaleDown.interpolator = OvershootInterpolator()
                    scaleDown.start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val scaleDownX2 = ObjectAnimator.ofFloat(upperView, "scaleX", 1f)
                    val scaleDownY2 = ObjectAnimator.ofFloat(upperView, "scaleY", 1f)
                    scaleDownX2.duration = 200
                    scaleDownY2.duration = 200
                    val scaleDown2 = AnimatorSet()
                    scaleDown2.play(scaleDownX2).with(scaleDownY2)
                    scaleDown2.interpolator = OvershootInterpolator()
                    scaleDown2.start()
                }
            }
            false
        }
    }
}
