package cn.com.thinkwatch.ihass2.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentStatePagerAdapter
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.fragment.CameraPtzFragment
import cn.com.thinkwatch.ihass2.fragment.CameraTtsFragment
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.utils.Gsons
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Observers
import com.dylan.medias.player.MxPlayerView
import com.dylan.medias.stream.MxStreamReader
import com.yunsean.dynkotlins.extensions.loge
import com.yunsean.dynkotlins.extensions.loges
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_hass_camera_view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.dip
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CameraViewActivity : BaseActivity() {

    private lateinit var entity: JsonEntity
    private var imageWidth = 16
    private var imageHeight = 9
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_hass_camera_view)
        val entity = try { Gsons.gson.fromJson<JsonEntity>(intent.getStringExtra("entity"), JsonEntity::class.java) } catch (_: Exception) { null }
        if (entity == null) return finish()
        this.entity = entity
        setTitle(this.entity.friendlyName, true)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        ui()
        tabbar()
        gesture()
    }
    override fun onPause() {
        super.onPause()
        finish()
    }
    override fun onDestroy() {
        intervalDisposable?.dispose()
        stop()
        super.onDestroy()
    }
    override fun onBackPressed() {
        if (isFullscreen) toggle()
        else super.onBackPressed()
    }

    private var isFullscreen = false
    private fun toggle() {
        if (isFullscreen) {
            isFullscreen = false
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            supportActionBar?.apply { show() }
            val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            playerViewPanel.layoutParams = layoutParams
            playerViewFooter.visibility = View.VISIBLE
            tabbar.visibility = View.VISIBLE
            viewPager.visibility = View.VISIBLE
            toolbarLayout.visibility = View.GONE
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            isFullscreen = true
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            playerViewPanel.layoutParams = layoutParams
            supportActionBar?.apply { hide() }
            playerViewFooter.visibility = View.GONE
            tabbar.visibility = View.GONE
            viewPager.visibility = View.GONE
            toolbarLayout.visibility = View.VISIBLE
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    private fun ui() {
        Observers.observeLayout(playerViewContainer) {
            val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, it.width * imageHeight / imageWidth)
            it.layoutParams = layoutParams
            true
        }
        this.mute.isChecked = true
        this.playerView.setMuted(true)
        if (entity.attributes?.previewUrl?.contains("0.sdp") ?: false) this.subStreamPanel.visibility = View.VISIBLE
        else this.subStreamPanel.visibility = View.GONE
        this.subStream1.onClick { play() }
        this.subStream2.onClick { play() }
        this.subStream3.onClick { play() }
        if (entity.attributes?.previewUrl.isNullOrBlank()) {
            this.playerViewPanel.visibility = View.GONE
            refresh()
        } else {
            this.imageViewPanel.visibility = View.GONE
            play()
        }
        this.refresh.onClick { refresh() }
        this.play.onClick { if (playerView.isPlaying) stop() else play() }
        this.mute.setOnCheckedChangeListener { buttonView, isChecked -> playerView.setMuted(isChecked) }
        this.full.onClick { toggle() }
        this.exitFullscreen.onClick { toggle() }
    }
    private fun stop() {
        this.playerView.stop()
    }
    private fun play() {
        var url = entity.attributes?.previewUrl ?: ""
        if (url.contains("0.sdp")) {
            if (subStream2.isChecked) url = url.replace("0.sdp", "1.sdp")
            if (subStream3.isChecked) url = url.replace("0.sdp", "2.sdp")
        }
        this.playerView
                .setAutoPlay(true)
                .setCallback(object: MxPlayerView.Callback {
                    override fun onPlay(width: Int, height: Int) {
                        runOnUiThread {
                            play.imageResource = R.drawable.ic_stop_blue_24dp
                            imageWidth = width
                            imageHeight = height
                            playerViewContainer.requestLayout()
                        }
                    }
                    override fun onStop() {
                        runOnUiThread { play.imageResource = R.drawable.ic_play_arrow_blue_24dp }
                    }
                    override fun onError(reason: String?) {
                        showError(reason ?: "打开摄像头失败", "重试") { play() }
                    }
                })
                .setNoDelay(true)
                .open(MxStreamReader()
                        .setAutoRetry(false)
                        .setTcpOnly(true)
                        .open(url))
    }
    private fun refresh() {
        this.progressbar.visibility = View.VISIBLE
        Glide.with(this)
                .load(entity.attributes?.entityPicture?.let { cfg.getString(HassConfig.Hass_HostUrl, "") + it + "&time=${System.currentTimeMillis()}" } ?: "")
                .apply(RequestOptions()
                        .timeout(60_000)
                        .dontAnimate()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true))
                .listener(object: RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                        progressbar.visibility = View.GONE
                        return false
                    }
                    override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                        progressbar.visibility = View.GONE
                        return false
                    }
                })
                .into(this.imageView)
    }

    private data class ActionFragment(val name: String,
                                      val fragment: BaseFragment)
    private var fragments = mutableListOf<ActionFragment>()
    private lateinit var adapter: FragmentStatePagerAdapter
    private fun tabbar() {
        db.getDbEntity(entity?.entityId!!)?.let {
            try {
                JSONObject(it.rawJson).optJSONObject("attributes")?.let { attr ->
                    cameraType = attr.optString("type")
                    if (attr.has("speed")) ptzSpeed = attr.optDouble("speed", 0.1)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
        fragments.add(ActionFragment("云台控制", BaseFragment.newInstance(CameraPtzFragment::class.java, Intent().putExtra("entityId", entity.entityId).putExtra("cameraType", cameraType).putExtra("speed", ptzSpeed))))
        if (!entity.attributes?.ttsSensor.isNullOrBlank()) {
            fragments.add(ActionFragment("语音发送", BaseFragment.newInstance(CameraTtsFragment::class.java, Intent().putExtra("entityId", entity.attributes?.ttsSensor))))
        }
        adapter = object : FragmentStatePagerAdapter(supportFragmentManager) {
            override fun getPageTitle(position: Int): CharSequence = fragments.get(position).name
            override fun getCount(): Int = fragments.size
            override fun getItem(position: Int): Fragment = fragments.get(position).fragment
        }
        this.viewPager.setCanScroll(false)
        this.viewPager.adapter = adapter
        this.tabbar.setupWithViewPager(this.viewPager)
        this.tabbar.setSelectedTabIndicatorHeight(dip(3))
    }

    private fun gesture() {
        act.playerView.setOnTouchListener(object: View.OnTouchListener {
            private var beginX = 0F
            private var beginY = 0F
            private var isFlipping = false
            private var isPressed = false
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null || !isFullscreen) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN-> {
                        beginX = event.rawX
                        beginY = event.rawY
                        isPressed = true
                        isFlipping = false
                    }
                    MotionEvent.ACTION_MOVE-> {
                        if (!isFlipping) {
                            val deltaX = Math.abs(event.rawX - beginX)
                            val deltaY = Math.abs(event.rawY - beginY)
                            val diff = deltaX / deltaY
                            if ((deltaX > 200 || deltaY > 200) && (diff > 3 || diff < 0.3)) {
                                isFlipping = true
                                if (diff > 3 && event.rawX > beginX) {
                                    act.ptz_right.visibility = View.VISIBLE
                                    callService("LEFT")
                                } else if (diff > 3 && event.rawX < beginX) {
                                    act.ptz_left.visibility = View.VISIBLE
                                    callService("RIGHT")
                                } else if (diff < 0.3 && event.rawY > beginY) {
                                    act.ptz_down.visibility = View.VISIBLE
                                    callService(null, "UP")
                                } else if (diff < 0.3 && event.rawY < beginY) {
                                    act.ptz_up.visibility = View.VISIBLE
                                    callService(null, "DOWN")
                                }
                            }
                        }
                    }
                    MotionEvent.ACTION_UP-> {
                        isPressed = false
                        isFlipping = false
                        act.ptz_right.visibility = View.GONE
                        act.ptz_left.visibility = View.GONE
                        act.ptz_down.visibility = View.GONE
                        act.ptz_up.visibility = View.GONE
                        callService()
                    }
                }
                return true
            }
        })
    }

    private var ptzSpeed: Double? = null
    private var cameraType: String? = null
    private var intervalDisposable: Disposable? = null
    private fun callService(pan: String? = null,
                            tilt: String? = null) {
        if (cameraType == "hass_old") {
            RxBus2.getDefault().post(ServiceRequest("camera", "onvif_ptz", entityId = entity.entityId, pan = pan, tilt = tilt))
        } else if (cameraType == "hass") {
            RxBus2.getDefault().post(ServiceRequest("onvif", "ptz", entityId = entity.entityId, pan = pan, tilt = tilt, moveMode = "ContinuousMove", speed = ptzSpeed?.toString()))
        } else {
            intervalDisposable?.dispose()
            if (pan != null || tilt != null) intervalDisposable = Observable.interval(0, 500, TimeUnit.MILLISECONDS).doOnNext {
                RxBus2.getDefault().post(ServiceRequest("onvif", "ptz", entityId = entity.entityId, pan = pan, tilt = tilt, moveMode = "ContinuousMove", continuousDuration = 0.5f, speed = ptzSpeed?.toString()))
            }.subscribe()
        }
    }
}

