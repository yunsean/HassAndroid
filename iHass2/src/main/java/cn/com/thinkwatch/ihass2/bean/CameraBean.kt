package cn.com.thinkwatch.ihass2.bean

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.bus.EntityClicked
import cn.com.thinkwatch.ihass2.bus.EntityLongClicked
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Bitmaps
import com.yunsean.dynkotlins.extensions.dip2px
import kotlinx.android.synthetic.main.tile_camera.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.io.File
import java.io.FileOutputStream

class CameraBean(entity: JsonEntity, val translucence: Boolean): BaseBean(entity) {
    private var entiryPictureLoaded = false
    override fun layoutResId(): Int = R.layout.tile_camera
    override fun bindToView(itemView: View, context: Context) {
        if (translucence) {
            itemView.cardView.cardElevation = 0f
            itemView.cardView.setCardBackgroundColor(0xaaffffff.toInt())
        } else {
            itemView.cardView.cardElevation = context.dip2px(2f).toFloat()
            itemView.cardView.setCardBackgroundColor(0xffffffff.toInt())
        }
        itemView.friendlyName.text = if (entity.showName.isNullOrEmpty()) entity.friendlyName else entity.showName
        val cacheFile = "${context.getCacheDir()}/${entity.entityId}.jpg"
        Glide.with(context).asBitmap()
                .load(cacheFile)
                .apply(RequestOptions().placeholder(R.drawable.ic_camera).diskCacheStrategy(DiskCacheStrategy.NONE))
                .into(object: SimpleTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap?, transition: Transition<in Bitmap>?) {
                        if (resource == null && entiryPictureLoaded) return
                        itemView.image.setImageBitmap(resource)
                    }
                })
        Glide.with(context).asBitmap()
                .load(entity.attributes?.entityPicture?.let { context.cfg.getString(HassConfig.Hass_HostUrl, "") + it } ?: "")
                .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.NONE))
                .into(object : SimpleTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap?, transition: Transition<in Bitmap>?) {
                        resource?.let {
                            bitmap2File(it, cacheFile)
                            itemView.image.setImageBitmap(it)
                            entiryPictureLoaded = true
                        }
                    }
                })
        itemView.contentView.onClick { RxBus2.getDefault().post(EntityClicked(entity)) }
        itemView.contentView.setOnLongClickListener {
            RxBus2.getDefault().post(EntityLongClicked(entity))
            true
        }
        itemView.contentView.setOnTouchListener(getTouchListener(itemView.contentView))
    }

    fun bitmap2File(bitmap: Bitmap, path: String?) {
        try {
            val outFile = File(path)
            val fileOutputStream = FileOutputStream(outFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 40, fileOutputStream)
            fileOutputStream.flush()
            fileOutputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}