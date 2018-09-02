package cn.com.thinkwatch.ihass2.bean

import android.content.Context
import android.support.v4.content.res.ResourcesCompat
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.bus.EntityClicked
import cn.com.thinkwatch.ihass2.bus.EntityLongClicked
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import com.dylan.common.rx.RxBus2
import kotlinx.android.synthetic.main.tile_normal.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick

class NormalBean(entity: JsonEntity): BaseBean(entity) {
    override fun layoutResId(): Int = R.layout.tile_normal
    override fun bindToView(itemView: View, context: Context) {
        itemView.friendlyName.text = if (entity.showName.isNullOrBlank()) entity.friendlyName else entity.showName
        itemView.group.text = entity.groupName
        MDIFont.get().setIcon(itemView.state, if (entity.showIcon.isNullOrBlank()) entity.iconState else entity.showIcon)
        itemView.indicator.visibility = if (entity.hasIndicator) View.VISIBLE else View.INVISIBLE
        itemView.isActivated = entity.isActivated
        itemView.state.isActivated = entity.isActivated
        itemView.group.isActivated = entity.isActivated
        itemView.friendlyName.isActivated = entity.isActivated
        itemView.indicator.visibility = if (entity.isScript || entity.isInputSelect) View.INVISIBLE else View.VISIBLE
        val resColorState = if (entity.isSensor) R.color.color_sensor
        else if (entity.isDeviceTracker) R.color.color_devicetracker
        else R.color.color_xiaomi
        val colorStateList = context.resources.getColorStateList(resColorState)
        itemView.state.setTextColor(colorStateList)
        itemView.group.setTextColor(colorStateList)
        itemView.friendlyName.setTextColor(colorStateList)
        itemView.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), if (entity.isCircle) R.color.md_light_blue_400 else R.color.md_white_1000, null))
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