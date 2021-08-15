package cn.com.thinkwatch.ihass2.bean

import android.content.Context
import android.support.v7.widget.CardView
import android.view.View
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.bus.EntityLongClicked
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.utils.viewAt
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.dip2px

class SwitchBean(entity: JsonEntity, val translucence: Boolean): BaseBean(entity) {
    override fun layoutResId(): Int = R.layout.tile_switch
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
        if (entity.isStateful || entity.isInputBoolean) {
            itemView.viewAt<View>(R.id.stateful).apply {
                visibility = View.VISIBLE
                setOnClickListener { RxBus2.getDefault().post(ServiceRequest(entity.domain, "toggle", entity.entityId))}
                setOnLongClickListener {
                    RxBus2.getDefault().post(EntityLongClicked(entity))
                    true
                }
            }
            itemView.viewAt<View>(R.id.stateless).apply {
                visibility = View.GONE
            }
            itemView.viewAt<TextView>(R.id.friendlyName).apply {
                text = if (entity.showName.isNullOrEmpty()) entity.friendlyName else entity.showName
                isActivated = entity.isActivated
            }
            itemView.viewAt<TextView>(R.id.group).apply {
                text = entity.groupName
                isActivated = entity.isActivated
            }
            itemView.viewAt<TextView>(R.id.state).apply {
                MDIFont.get().setIcon(this, if (entity.showIcon.isNullOrBlank()) entity.iconState else entity.showIcon)
                isActivated = entity.isActivated
            }
            itemView.isActivated = entity.isActivated
        } else {
            itemView.viewAt<View>(R.id.stateful).apply {
                visibility = View.GONE
            }
            itemView.viewAt<View>(R.id.stateless).apply {
                visibility = View.VISIBLE
                alpha = if (translucence) 0.8f else 1f
                setOnLongClickListener {
                    RxBus2.getDefault().post(EntityLongClicked(entity))
                    true
                }
                itemView.viewAt<View>(R.id.contentView).setOnTouchListener(getTouchListener(this))
            }
            itemView.viewAt<TextView>(R.id.friendlyName1).apply {
                isActivated = false
                text = if (entity.showName.isNullOrEmpty()) entity.friendlyName else entity.showName
            }
            itemView.viewAt<View>(R.id.turnOn).apply {
                setOnClickListener { RxBus2.getDefault().post(ServiceRequest(entity.domain, "turn_on", entity.entityId)) }
                setOnLongClickListener {
                    RxBus2.getDefault().post(EntityLongClicked(entity))
                    true
                }
            }
            itemView.viewAt<View>(R.id.turnOff).apply {
                setOnClickListener { RxBus2.getDefault().post(ServiceRequest(entity.domain, "turn_off", entity.entityId)) }
                setOnLongClickListener {
                    RxBus2.getDefault().post(EntityLongClicked(entity))
                    true
                }
            }
        }
    }
}