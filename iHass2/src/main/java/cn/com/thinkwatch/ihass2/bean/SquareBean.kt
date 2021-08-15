package cn.com.thinkwatch.ihass2.bean

import android.content.Context
import android.support.v7.widget.CardView
import android.view.View
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.bus.EntityClicked
import cn.com.thinkwatch.ihass2.bus.EntityLongClicked
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.utils.viewAt
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.dip2px

class SquareBean(entity: JsonEntity, val translucence: Boolean): BaseBean(entity) {
    override fun layoutResId(): Int = R.layout.tile_square
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
        if ((entity.isBroadcastMusic || entity.isBroadcastRadio || entity.isMiioGateway) && entity.isActivated) {
            itemView.viewAt<TextView>(R.id.icon).visibility = View.GONE
            itemView.viewAt<TextView>(R.id.group).apply {
                visibility = View.VISIBLE
                text = entity.groupName
                isActivated = entity.isActivated
            }
        } else {
            itemView.viewAt<TextView>(R.id.group).visibility = View.GONE
            itemView.viewAt<TextView>(R.id.icon).apply {
                visibility = View.VISIBLE
                textSize = if (entity.showIcon.isNullOrBlank()) 16f else 30f
                MDIFont.get().setIcon(this, if (entity.showIcon.isNullOrBlank()) entity.friendlyStateRow else entity.showIcon)
                isActivated = entity.isActivated
            }
        }
        itemView.viewAt<TextView>(R.id.name).apply {
            text = if (entity.showName.isNullOrEmpty()) entity.friendlyName else entity.showName
            isActivated = entity.isActivated
        }
        itemView.viewAt<View>(R.id.contentView).apply {
            setOnClickListener {
                if ((entity.isSwitch && entity.isStateful) || entity.isInputBoolean) {
                    RxBus2.getDefault().post(ServiceRequest(entity.domain, "toggle", entity.entityId))
                } else {
                    RxBus2.getDefault().post(EntityClicked(entity))
                }
            }
            setOnLongClickListener {
                RxBus2.getDefault().post(EntityLongClicked(entity))
                true
            }
            setOnTouchListener(getTouchListener(this))
        }
    }
}