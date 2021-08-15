package cn.com.thinkwatch.ihass2.bean

import android.content.Context
import android.support.v4.content.res.ResourcesCompat
import android.support.v7.widget.CardView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.bus.EntityClicked
import cn.com.thinkwatch.ihass2.bus.EntityLongClicked
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.utils.viewAt
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
        itemView.viewAt<TextView>(R.id.friendlyName).apply {
            text = if (entity.showName.isNullOrEmpty()) entity.friendlyName else entity.showName
        }
        if (entity.attributes?.entityPicture.isNullOrBlank()) {
            itemView.viewAt<TextView>(R.id.state).apply {
                visibility = View.VISIBLE
                isActivated = entity.isActivated
                MDIFont.get().setIcon(this, if (entity.showIcon.isNullOrBlank()) entity.iconState else entity.showIcon)
            }
            itemView.viewAt<ImageView>(R.id.image).visibility = View.GONE
        } else {
            itemView.viewAt<TextView>(R.id.state).visibility = View.GONE
            itemView.viewAt<ImageView>(R.id.image).apply {
                visibility = View.VISIBLE
                Glide.with(context).load(entity.attributes?.entityPicture).into(this)
            }
        }
        itemView.viewAt<TextView>(R.id.group).apply {
            text = entity.groupName
            isActivated = entity.isActivated
        }
        itemView.viewAt<View>(R.id.indicator).visibility = if (entity.isScript || entity.isInputSelect) View.INVISIBLE else View.VISIBLE
        itemView.viewAt<View>(R.id.contentView).apply {
            setOnClickListener { RxBus2.getDefault().post(EntityClicked(entity)) }
            setOnLongClickListener {
                RxBus2.getDefault().post(EntityLongClicked(entity))
                true
            }
            setOnTouchListener(getTouchListener(this))
        }
    }
}