package cn.com.thinkwatch.ihass2.bean

import android.content.Context
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.bus.EntityClicked
import cn.com.thinkwatch.ihass2.bus.EntityLongClicked
import cn.com.thinkwatch.ihass2.model.JsonEntity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.dylan.common.rx.RxBus2
import kotlinx.android.synthetic.main.tile_camera.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick

class CameraBean(entity: JsonEntity): BaseBean(entity) {
    override fun layoutResId(): Int = R.layout.tile_camera
    override fun bindToView(itemView: View, context: Context) {
        itemView.friendlyName.text = if (entity.showName.isNullOrBlank()) entity.friendlyName else entity.showName
        Glide.with(context).asBitmap()
                .load(entity.attributes?.entityPicture?.let { context.app.haHostUrl + it } ?: "")
                .apply(RequestOptions().placeholder(R.drawable.ic_camera)
                        .diskCacheStrategy(DiskCacheStrategy.NONE))
                .into(itemView.image)
        itemView.contentView.onClick { RxBus2.getDefault().post(EntityClicked(entity)) }
        itemView.contentView.setOnLongClickListener {
            RxBus2.getDefault().post(EntityLongClicked(entity))
            true
        }
        itemView.contentView.setOnTouchListener(getTouchListener(itemView.contentView))
    }
}