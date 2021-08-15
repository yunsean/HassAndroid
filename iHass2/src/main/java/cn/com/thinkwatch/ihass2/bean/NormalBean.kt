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
import com.yunsean.dynkotlins.extensions.dip2px
import com.yunsean.dynkotlins.extensions.ktime
import kotlinx.android.synthetic.main.tile_sensor.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.text.SimpleDateFormat

class NormalBean(entity: JsonEntity, val translucence: Boolean): BaseBean(entity) {
    override fun layoutResId(): Int = R.layout.tile_normal
    override fun bindToView(itemView: View, context: Context) {
        if (translucence) {
            itemView.cardView.cardElevation = 0f
            itemView.cardView.setCardBackgroundColor(0xaaffffff.toInt())
        } else {
            itemView.cardView.cardElevation = context.dip2px(2f).toFloat()
            itemView.cardView.setCardBackgroundColor(0xffffffff.toInt())
        }
        itemView.friendlyName.text = if (entity.showName.isNullOrEmpty()) entity.friendlyName else entity.showName
        itemView.group.text = try { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZZZZZ").parse(entity.attributes?.lastTriggered)?.ktime() } catch (_: Exception) { entity.attributes?.lastTriggered } ?: "无"
        itemView.state.typeface = ResourcesCompat.getFont(context, if (entity.hasStateIcon) R.font.mdi else R.font.dincond)
        MDIFont.get().setIcon(itemView.state, if (entity.showIcon.isNullOrBlank()) entity.iconState else entity.showIcon)
        itemView.indicator.visibility = if (entity.hasIndicator) View.VISIBLE else View.INVISIBLE
        itemView.isActivated = entity.isActivated
        itemView.state.isActivated = entity.isActivated
        itemView.group.isActivated = entity.isActivated
        itemView.friendlyName.isActivated = entity.isActivated
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