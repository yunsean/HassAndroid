package cn.com.thinkwatch.ihass2.ui

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.utils.cfg
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import kotlinx.android.synthetic.main.activity_hass_imageview.*
import org.jetbrains.anko.act
import org.jetbrains.anko.sdk25.coroutines.onClick


class ImageViewActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewNoTitlebar(R.layout.activity_hass_imageview)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.black)
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        show(intent.getStringExtra("url"))
        act.back.onClick { finish() }
    }
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        show(intent.getStringExtra("url"))
    }
    override fun onPause() {
        super.onPause()
        finish()
    }

    private fun show(url: String?) {
        if (url.isNullOrBlank()) return finish()
        val builder = LazyHeaders.Builder()
        val token = cfg.haToken
        val pwd = cfg.haPassword
        if (token.isNotBlank()) builder.addHeader("Authorization", token)
        if (pwd.isNotBlank()) builder.addHeader("x-ha-access", pwd)
        val glideUrl = GlideUrl(url, builder.build())
        Glide.with(act.imageView)
                .load(glideUrl)
                .apply(RequestOptions().skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE))
                .listener(object: RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                        act.loading.visibility = View.GONE
                        act.error.visibility = View.VISIBLE
                        return false
                    }
                    override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                        act.loading.visibility = View.GONE
                        act.error.visibility = View.GONE
                        return false
                    }
                })
                .into(act.imageView)
    }

}

