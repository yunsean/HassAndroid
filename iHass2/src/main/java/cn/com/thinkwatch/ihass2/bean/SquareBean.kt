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
import kotlinx.android.synthetic.main.tile_square.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick

class SquareBean(entity: JsonEntity): BaseBean(entity) {
    override fun layoutResId(): Int = R.layout.tile_square
    override fun bindToView(itemView: View, context: Context) {
        itemView.icon.setTextSize(if (entity.showIcon.isNullOrBlank()) 16f else 30f)
        MDIFont.get().setIcon(itemView.icon, if (entity.showIcon.isNullOrBlank()) entity.friendlyStateRow else entity.showIcon)
        itemView.name.setText(if (entity.showName.isNullOrBlank()) entity.friendlyName else entity.showName)
        itemView.icon.isActivated = entity.isActivated
        itemView.name.isActivated = entity.isActivated
        itemView.contentView.onClick {
            if (entity.isSwitch && entity.isStateful) {
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