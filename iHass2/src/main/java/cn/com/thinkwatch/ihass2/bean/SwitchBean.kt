package cn.com.thinkwatch.ihass2.bean

import android.content.Context
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.bus.EntityLongClicked
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.dip2px
import kotlinx.android.synthetic.main.tile_switch.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick

class SwitchBean(entity: JsonEntity, val translucence: Boolean): BaseBean(entity) {
    override fun layoutResId(): Int = R.layout.tile_switch
    override fun bindToView(itemView: View, context: Context) {
        if (translucence) {
            itemView.cardView.cardElevation = 0f
            itemView.cardView.setCardBackgroundColor(0xaaffffff.toInt())
        } else {
            itemView.cardView.cardElevation = context.dip2px(2f).toFloat()
            itemView.cardView.setCardBackgroundColor(0xffffffff.toInt())
        }
        if (entity.isStateful || entity.isInputBoolean) {
            itemView.stateful.visibility = View.VISIBLE
            itemView.stateless.visibility = View.GONE
            itemView.friendlyName.text = if (entity.showName.isNullOrBlank()) entity.friendlyName else entity.showName
            itemView.group.text = entity.groupName
            MDIFont.get().setIcon(itemView.state, if (entity.showIcon.isNullOrBlank()) entity.iconState else entity.showIcon)
            itemView.isActivated = entity.isActivated
            itemView.state.isActivated = entity.isActivated
            itemView.group.isActivated = entity.isActivated
            itemView.friendlyName.isActivated = entity.isActivated
            itemView.stateful.onClick { RxBus2.getDefault().post(ServiceRequest(entity.domain, "toggle", entity.entityId))}
            itemView.stateful.setOnLongClickListener {
                RxBus2.getDefault().post(EntityLongClicked(entity))
                true
            }
            itemView.contentView.setOnTouchListener(getTouchListener(itemView.stateful))
        } else {
            itemView.stateful.visibility = View.GONE
            itemView.stateless.visibility = View.VISIBLE
            itemView.stateless.alpha = if (translucence) 0.8f else 1f
            itemView.friendlyName1.isActivated = false
            itemView.friendlyName1.text = if (entity.showName.isNullOrBlank()) entity.friendlyName else entity.showName
            itemView.turnOn.onClick { RxBus2.getDefault().post(ServiceRequest(entity.domain, "turn_on", entity.entityId)) }
            itemView.turnOff.onClick { RxBus2.getDefault().post(ServiceRequest(entity.domain, "turn_off", entity.entityId)) }
            itemView.turnOn.setOnLongClickListener {
                RxBus2.getDefault().post(EntityLongClicked(entity))
                true
            }
            itemView.turnOff.setOnLongClickListener {
                RxBus2.getDefault().post(EntityLongClicked(entity))
                true
            }
            itemView.stateless.setOnLongClickListener {
                RxBus2.getDefault().post(EntityLongClicked(entity))
                true
            }
            itemView.contentView.setOnTouchListener(getTouchListener(itemView.stateless))
        }
    }
}