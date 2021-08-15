package cn.com.thinkwatch.ihass2.bean

import android.content.Context
import android.support.v4.content.res.ResourcesCompat
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.bus.EntityClicked
import cn.com.thinkwatch.ihass2.bus.EntityLongClicked
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import com.bumptech.glide.Glide
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.dip2px
import kotlinx.android.synthetic.main.dialog_mdi_text.view.*
import kotlinx.android.synthetic.main.tile_tracker.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick

class TrackerBean(entity: JsonEntity, val translucence: Boolean): BaseBean(entity) {
    override fun layoutResId(): Int = R.layout.tile_tracker
    override fun bindToView(itemView: View, context: Context) {
        if (translucence) {
            itemView.cardView.cardElevation = 0f
            itemView.cardView.setCardBackgroundColor(0xAA29B6FC.toInt())
        } else {
            itemView.cardView.cardElevation = context.dip2px(2f).toFloat()
            itemView.cardView.setCardBackgroundColor(0xFF29B6FC.toInt())
        }
        itemView.friendlyName.text = if (entity.showName.isNullOrEmpty()) entity.friendlyName else entity.showName
        if (entity.attributes?.entityPicture.isNullOrBlank()) {
            MDIFont.get().setIcon(itemView.state, if (entity.showIcon.isNullOrBlank()) entity.iconState else entity.showIcon)
            itemView.state.visibility = View.VISIBLE
            itemView.image.visibility = View.GONE
        } else {
            Glide.with(context).load(entity.attributes?.entityPicture).into(itemView.image)
            itemView.state.visibility = View.GONE
            itemView.image.visibility = View.VISIBLE
        }
        itemView.group.text = entity.groupName
        itemView.group.isActivated = entity.isActivated
        itemView.state.isActivated = entity.isActivated
        itemView.indicator.visibility = if (entity.isScript || entity.isInputSelect) View.INVISIBLE else View.VISIBLE
        itemView.contentView.onClick { RxBus2.getDefault().post(EntityClicked(entity)) }
        itemView.contentView.setOnLongClickListener(object: View.OnLongClickListener {
            override fun onLongClick(p0: View?): Boolean {
                RxBus2.getDefault().post(EntityLongClicked(entity))
                return true
            }
        })
        itemView.contentView.setOnTouchListener(getTouchListener(itemView.contentView))
    }
}