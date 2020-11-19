package cn.com.thinkwatch.ihass2.ui

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentStatePagerAdapter
import android.view.View
import android.view.WindowManager
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.base.BaseFragment
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
import com.dylan.medias.player.MxPlayerView
import com.dylan.medias.stream.MxStreamReader
import kotlinx.android.synthetic.main.activity_hass_camera_view.*
import org.jetbrains.anko.dip
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.sdk25.coroutines.onClick

class CameraViewActivity : BaseActivity() {

    private lateinit var entity: JsonEntity
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
    }
    override fun onPause() {
        super.onPause()
        finish()
    }
    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private fun ui() {
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
        this.full.onClick {  }
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
                        runOnUiThread { play.imageResource = R.drawable.ic_stop_blue_24dp }
                        playerView.layoutParams?.let {
                            it.height = playerView.width * height / width
                            playerView.requestLayout()
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
        fragments.add(ActionFragment("云台控制", BaseFragment.newInstance(CameraPtzFragment::class.java, Intent().putExtra("entityId", entity.entityId))))
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
}

