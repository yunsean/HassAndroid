package cn.com.thinkwatch.ihass2.bean

import android.content.Context
import android.graphics.Bitmap
import android.support.v7.widget.CardView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.bus.EntityClicked
import cn.com.thinkwatch.ihass2.bus.EntityLongClicked
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import cn.com.thinkwatch.ihass2.utils.viewAt
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.dip2px
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.io.File
import java.io.FileOutputStream

class CameraBean(entity: JsonEntity, val translucence: Boolean): BaseBean(entity) {
    private var entryPictureLoaded = false
    override fun layoutResId(): Int = R.layout.tile_camera
    override fun bindToView(itemView: View, context: Context) {
        if (translucence) {
            itemView.viewAt<CardView>(R.id.cardView).apply {
                cardElevation = 0f
                setCardBackgroundColor(0xaaffffff.toInt())
            }
        } else {
            itemView.viewAt<CardView>(R.id.cardView).apply {
                cardElevation = context.dip2px(2f).toFloat()
                setCardBackgroundColor(0xffffffff.toInt())
            }
        }
        itemView.viewAt<TextView>(R.id.friendlyName).text = if (entity.showName.isNullOrEmpty()) entity.friendlyName else entity.showName
        val cacheFile = "${context.cacheDir}/${entity.entityId}.jpg"
        Glide.with(context).asBitmap()
                .load(cacheFile)
                .apply(RequestOptions().placeholder(R.drawable.ic_camera).diskCacheStrategy(DiskCacheStrategy.NONE))
                .into(object: SimpleTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap?, transition: Transition<in Bitmap>?) {
                        if (resource == null && entryPictureLoaded) return
                        itemView.viewAt<ImageView>(R.id.image).setImageBitmap(resource)
                    }
                })
        Glide.with(context).asBitmap()
                .load(entity.attributes?.entityPicture?.let { context.cfg.getString(HassConfig.Hass_HostUrl, "") + it } ?: "")
                .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.NONE))
                .into(object : SimpleTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap?, transition: Transition<in Bitmap>?) {
                        resource?.let {
                            bitmap2File(it, cacheFile)
                            itemView.viewAt<ImageView>(R.id.image).setImageBitmap(it)
                            entryPictureLoaded = true
                        }
                    }
                })
        itemView.viewAt<View>(R.id.contentView).apply {
            onClick { RxBus2.getDefault().post(EntityClicked(entity)) }
            setOnLongClickListener {
                RxBus2.getDefault().post(EntityLongClicked(entity))
                true
            }
            setOnTouchListener(getTouchListener(this))
        }
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