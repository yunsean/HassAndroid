package cn.com.thinkwatch.ihass2.ui

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewPager
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.fragment.album.AlbumItemsFragment
import cn.com.thinkwatch.ihass2.fragment.album.AlbumTextViewFragment
import cn.com.thinkwatch.ihass2.model.album.AlbumItem
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
import com.yunsean.dynkotlins.extensions.kdateTime
import kotlinx.android.synthetic.main.activity_album_imageview.*
import org.jetbrains.anko.act
import java.io.File


class AlbumImageViewActivity : BaseActivity() {

    private var imageIndex = 0
    private lateinit var path: String
    private lateinit var userStub: String
    private var isLocal = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewNoTitlebar(R.layout.activity_album_imageview)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.black)
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        isSwipeEnabled = true

        imageIndex = intent.getIntExtra("imageIndex", 0)
        isLocal = intent.getBooleanExtra("isLocal", false)
        path = intent.getStringExtra("path") ?: ""
        userStub = intent.getStringExtra("userStub") ?:if (isLocal) "" else return finish()
        if (imageList == null || imageList!!.size <= imageIndex) return finish()
        act.checked.visibility = if (intent.getBooleanExtra("showChecked", true)) View.VISIBLE else View.GONE

        titlebar()
        show()
        act.back.setOnClickListener {
            finish()
        }
        act.prev.setOnClickListener {
            if (imageIndex > 0) {
                imageIndex--
                show()
            }
        }
        act.next.setOnClickListener {
            if (imageIndex < imageList!!.size - 1) {
                imageIndex++
                show()
            }
        }
        act.checked.setOnClickListener {
            imageList!![imageIndex].checked = !imageList!![imageIndex].checked
            act.checked.isSelected = imageList!![imageIndex].checked
        }
    }
    override fun onDestroy() {
        imageList = null
        super.onDestroy()
    }

    private lateinit var adapter: FragmentStatePagerAdapter
    private fun titlebar() {
        this.adapter = object : FragmentStatePagerAdapter(supportFragmentManager) {
            override fun getPageTitle(position: Int): CharSequence = imageList!![position].name
            override fun getCount(): Int = imageList?.size ?: 0
            override fun getItem(position: Int): Fragment = AlbumTextViewFragment().apply { arguments = Bundle().apply {
                val item = imageList!![position]
                putString("name", item.name)
                putString("size", item.mtime?.let { "${AlbumItemsFragment.formatSize(item.size)}  ${(it * 1000).kdateTime()}" } ?: AlbumItemsFragment.formatSize(item.size))
            } }
        }
        act.viewPager.setCanScroll(true)
        act.viewPager.adapter = adapter
        act.viewPager.addOnPageChangeListener(object: ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) { }
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) { }
            override fun onPageSelected(position: Int) { if (imageIndex != position) { imageIndex = position; show() } }
        })
    }

    private fun show() {
        if (imageList == null || imageIndex >= imageList!!.size) return
        val item = imageList!![imageIndex]
        act.checked.isSelected = item.checked
        act.next.visibility = if (imageIndex == imageList!!.size - 1) View.INVISIBLE else View.VISIBLE
        act.prev.visibility = if (imageIndex == 0) View.INVISIBLE else View.VISIBLE
        act.viewPager.currentItem = imageIndex
        val glideUrl: Any = if (isLocal) {
            File(item.url(userStub, cfg.haHostUrl, this.path))
        } else {
            val builder = LazyHeaders.Builder()
            val token = cfg.haToken
            val pwd = cfg.haPassword
            if (token.isNotBlank()) builder.addHeader("Authorization", token)
            if (pwd.isNotBlank()) builder.addHeader("x-ha-access", pwd)
            GlideUrl(item.url(userStub, cfg.haHostUrl, this.path), builder.build())
        }
        act.loading.visibility = View.VISIBLE
        act.error.visibility = View.GONE
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

    companion object {
        var imageList: List<AlbumItem>? = null
    }
}

