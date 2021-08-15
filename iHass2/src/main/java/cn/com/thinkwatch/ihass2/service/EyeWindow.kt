package cn.com.thinkwatch.ihass2.service

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.R
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.layoutInflater

class EyeWindow() {

    private val floatLayout: View
    private val windowManager : WindowManager
    private val windowParams: WindowManager.LayoutParams
    init {
        val act = HassApplication.application
        floatLayout = act.layoutInflater.inflate(R.layout.overlay_eye, null) as View
        windowParams = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT)
        windowManager = act.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowParams.gravity = Gravity.START or Gravity.TOP
    }
    fun show() {
        val metrics = DisplayMetrics()
        this.windowManager.defaultDisplay.getMetrics(metrics)
        this.windowParams.x = 0
        this.windowParams.y = 0
        this.windowParams.width = metrics.widthPixels
        this.windowParams.height = metrics.heightPixels
        if (floatLayout.parent != null) this.windowManager.updateViewLayout(floatLayout, windowParams)
        else this.windowManager.addView(floatLayout, windowParams)
    }
    fun setColor(color: Int) {
        floatLayout.backgroundColor = color
    }
    fun dismiss() {
        if (floatLayout.parent == null) return
        windowManager.removeView(floatLayout)
    }
    fun update() {
        if (floatLayout.parent != null) {
            val metrics = DisplayMetrics()
            this.windowManager.defaultDisplay.getMetrics(metrics)
            this.windowParams.x = 0
            this.windowParams.y = 0
            this.windowParams.width = metrics.widthPixels
            this.windowParams.height = metrics.heightPixels
            this.windowManager.updateViewLayout(floatLayout, windowParams)
        }
    }
    fun isShown() = floatLayout.parent != null
}