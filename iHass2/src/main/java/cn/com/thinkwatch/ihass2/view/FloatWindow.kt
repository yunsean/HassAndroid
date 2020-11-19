package cn.com.thinkwatch.ihass2.view

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.ui.CameraViewActivity
import cn.com.thinkwatch.ihass2.utils.Gsons
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Animations
import com.dylan.common.utils.Utility
import com.dylan.medias.player.MxPlayerView
import com.dylan.medias.stream.MxStreamReader
import com.yunsean.dynkotlins.extensions.loges
import com.yunsean.dynkotlins.extensions.start
import com.yunsean.dynkotlins.extensions.toastex
import org.jetbrains.anko.sdk25.coroutines.onClick

class FloatWindow(val act: Activity) : View.OnTouchListener {

    private var windowParams: WindowManager.LayoutParams
    private var windowManager: WindowManager
    private var floatLayout: View
    private var inViewX = 0f
    private var inViewY = 0f
    private var downInScreenX = 0f
    private var downInScreenY = 0f
    private var inScreenX = 0f
    private var inScreenY = 0f

    init {
        floatLayout = act.layoutInflater.inflate(R.layout.layout_hass_camera_overlay, null) as View
        floatLayout.setOnTouchListener(this)
        windowParams = WindowManager.LayoutParams()
        windowManager = act.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= 26)
            windowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            windowParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        windowParams.format = PixelFormat.RGBA_8888
        windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        windowParams.gravity = Gravity.START or Gravity.TOP
        windowParams.width = Utility.screenWidth(act) * 9 / 10
        windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT
    }

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                inViewX = motionEvent.x
                inViewY = motionEvent.y
                downInScreenX = motionEvent.rawX
                downInScreenY = motionEvent.rawY - getSysBarHeight(act)
                inScreenX = motionEvent.rawX
                inScreenY = motionEvent.rawY - getSysBarHeight(act)
            }
            MotionEvent.ACTION_MOVE -> {
                inScreenX = motionEvent.rawX
                inScreenY = motionEvent.rawY - getSysBarHeight(act)
                windowParams.x = (inScreenX - inViewX).toInt()
                windowParams.y = (inScreenY - inViewY).toInt()
                windowManager.updateViewLayout(floatLayout, windowParams)
            }
            MotionEvent.ACTION_UP -> {
                if (downInScreenX  == inScreenX && downInScreenY == inScreenY){
                    floatLayout.findViewById<View>(R.id.controller).visibility = View.VISIBLE
                    Animations.AlphaAnimation(floatLayout.findViewById<View>(R.id.controller), 1f)
                            .duration(300)
                            .start()
                    floatLayout.removeCallbacks(hideController)
                    floatLayout.postDelayed(hideController, 2000)
                }
            }
        }
        return true
    }
    private var hideController = object : Runnable {
        override fun run() {
            Animations.AlphaAnimation(floatLayout.findViewById<View>(R.id.controller), 0f)
                    .duration(300)
                    .animationListener { floatLayout.findViewById<View>(R.id.controller).visibility = View.GONE }
                    .start()
        }
    }
    fun showFloatWindow(entity: JsonEntity) {
        val url = entity.attributes?.previewUrl
        if (url == null) return
        if (floatLayout.parent == null) {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            windowParams.x = metrics.widthPixels
            windowParams.y = metrics.heightPixels / 2 - getSysBarHeight(act)
            windowManager.addView(floatLayout, windowParams)
        }
        val playerView: MxPlayerView? = floatLayout.findViewById(R.id.playerView)
        if (playerView == null) return
        val mute = floatLayout.findViewById<CheckBox>(R.id.mute)
        val ptz = floatLayout.findViewById<CheckBox>(R.id.ptz)
        val subStreamPanel = floatLayout.findViewById<RadioGroup>(R.id.subStreamPanel)
        mute.isChecked = true
        playerView.setMuted(true)
        if (url.contains("0.sdp") ?: false) subStreamPanel.visibility = View.VISIBLE
        else subStreamPanel.visibility = View.GONE
        floatLayout.findViewById<RadioButton>(R.id.subStream1).onClick { play(url) }
        floatLayout.findViewById<RadioButton>(R.id.subStream2).onClick { play(url) }
        floatLayout.findViewById<RadioButton>(R.id.subStream3).onClick { play(url) }
        mute.setOnCheckedChangeListener { _, isChecked -> playerView.setMuted(isChecked) }
        floatLayout.findViewById<RadioButton>(R.id.full).onClick {
            Intent(act, CameraViewActivity::class.java)
                    .putExtra("entity", Gsons.gson.toJson(entity))
                    .start(act)
        }
        ptz.setOnCheckedChangeListener { _, isChecked->  floatLayout.findViewById<View>(R.id.ptzPanel).visibility = if (isChecked) View.VISIBLE else View.GONE }
        floatLayout.findViewById<RadioButton>(R.id.close).onClick { hideFloatWindow() }
        floatLayout.findViewById<View>(R.id.left).setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) callService(entity.entityId, "LEFT")
            else if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP) callService(entity.entityId)
            true
        }
        floatLayout.findViewById<View>(R.id.right).setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) callService(entity.entityId, "RIGHT")
            else if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP) callService(entity.entityId)
            true
        }
        floatLayout.findViewById<View>(R.id.up).setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) callService(entity.entityId, null, "UP")
            else if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP) callService(entity.entityId)
            true
        }
        floatLayout.findViewById<View>(R.id.down).setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) callService(entity.entityId, null, "DOWN")
            else if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP) callService(entity.entityId)
            true
        }
        play(url)
    }
    private fun callService(entityId: String, pan: String? = null, tilt: String? = null) {
        loges("callService($pan, $tilt)")
        RxBus2.getDefault().post(ServiceRequest("camera", "onvif_ptz", entityId = entityId, pan = pan, tilt = tilt))
    }
    private var stopped = true
    private fun play(url: String) {
        var url = url
        if (url.contains("0.sdp")) {
            if ((floatLayout.findViewById<RadioButton>(R.id.subStream2)).isChecked) url = url.replace("0.sdp", "1.sdp")
            if ((floatLayout.findViewById<RadioButton>(R.id.subStream2)).isChecked) url = url.replace("0.sdp", "2.sdp")
        }
        val playerView: MxPlayerView? = floatLayout.findViewById(R.id.playerView)
        if (playerView == null) return
        stopped = false
        playerView.stop()
        playerView
                .setAutoPlay(true)
                .setCallback(object: MxPlayerView.Callback {
                    override fun onPlay(width: Int, height: Int) {
                        act.runOnUiThread {
                            playerView.layoutParams?.let {
                                it.height = playerView.width * height / width
                                playerView.requestLayout()
                            }
                        }
                    }
                    override fun onStop() {
                        stopped = true
                        act.runOnUiThread {
                            hideFloatWindow()
                        }
                    }
                    override fun onError(reason: String?) {
                        if (stopped) return
                        act.runOnUiThread {
                            act.toastex("打开摄像头失败")
                            hideFloatWindow()
                        }
                    }
                })
                .setNoDelay(true)
                .open(MxStreamReader()
                        .setAutoRetry(false)
                        .setTcpOnly(true)
                        .open(url))
    }

    fun hideFloatWindow() {
        floatLayout.findViewById<MxPlayerView>(R.id.playerView).stop()
        if (floatLayout.parent != null) windowManager.removeView(floatLayout)
    }
    fun setFloatLayoutAlpha(alpha: Boolean) {
        if (alpha) floatLayout.alpha = 0.5.toFloat()
        else floatLayout.alpha = 1f
    }

    private fun getSysBarHeight(contex: Context): Int {
        try {
            val c = Class.forName("com.android.internal.R\$dimen")
            val obj = c.newInstance()
            val field = c.getField("status_bar_height")
            val x = Integer.parseInt(field.get(obj).toString())
            return contex.resources.getDimensionPixelSize(x)
        } catch (e1: Exception) {
            e1.printStackTrace()
            return 0
        }
    }
}