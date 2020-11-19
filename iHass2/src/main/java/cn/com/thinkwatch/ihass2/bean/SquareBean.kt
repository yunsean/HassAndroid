package cn.com.thinkwatch.ihass2.bean

import android.content.Context
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.bus.EntityClicked
import cn.com.thinkwatch.ihass2.bus.EntityLongClicked
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.dip2px
import kotlinx.android.synthetic.main.tile_square.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick

class SquareBean(entity: JsonEntity, val translucence: Boolean): BaseBean(entity) {
    override fun layoutResId(): Int = R.layout.tile_square
    override fun bindToView(itemView: View, context: Context) {
        if (translucence) {
            itemView.cardView.cardElevation = 0f
            itemView.cardView.setCardBackgroundColor(0xaaffffff.toInt())
        } else {
            itemView.cardView.cardElevation = context.dip2px(2f).toFloat()
            itemView.cardView.setCardBackgroundColor(0xffffffff.toInt())
        }
        if ((entity.isBroadcastMusic || entity.isBroadcastRadio || entity.isMiioGateway) && entity.isActivated) {
            itemView.icon.visibility = View.GONE
            itemView.group.visibility = View.VISIBLE
            itemView.group.setText(entity.groupName)
            itemView.group.isActivated = entity.isActivated
        } else {
            itemView.group.visibility = View.GONE
            itemView.icon.visibility = View.VISIBLE
            itemView.icon.setTextSize(if (entity.showIcon.isNullOrBlank()) 16f else 30f)
            MDIFont.get().setIcon(itemView.icon, if (entity.showIcon.isNullOrBlank()) entity.friendlyStateRow else entity.showIcon)
            itemView.icon.isActivated = entity.isActivated
        }
        itemView.name.setText(if (entity.showName.isNullOrBlank()) entity.friendlyName else entity.showName)
        itemView.name.isActivated = entity.isActivated
        itemView.contentView.onClick {
            if ((entity.isSwitch && entity.isStateful) || entity.isInputBoolean) {
                RxBus2.getDefault().post(ServiceRequest(entity.domain, "toggle", entity.entityId))
            } else {
                RxBus2.getDefault().post(EntityClicked(entity))
            }
        }
        itemView.contentView.setOnLongClickListener(object: View.OnLongClickListener {
            override fun onLongClick(p0: View?): Boolean {
                RxBus2.getDefault().post(EntityLongClicked(entity))
                return true
            }
        })
        itemView.contentView.setOnTouchListener(getTouchListener(itemView.contentView))
    }
}