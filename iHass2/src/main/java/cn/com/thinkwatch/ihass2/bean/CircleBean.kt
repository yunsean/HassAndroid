package cn.com.thinkwatch.ihass2.bean

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.bus.EntityClicked
import cn.com.thinkwatch.ihass2.bus.EntityLongClicked
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.utils.viewAt
import com.dylan.common.rx.RxBus2
import kotlinx.android.synthetic.main.tile_circle.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.w3c.dom.Text

class CircleBean(entity: JsonEntity, val translucence: Boolean): BaseBean(entity) {
    override fun layoutResId(): Int = R.layout.tile_circle
    override fun bindToView(itemView: View, context: Context) {
        itemView.viewAt<ImageView>(R.id.backImage).alpha = if (translucence) .8f else 1f
        itemView.viewAt<TextView>(R.id.icon).apply {
            MDIFont.get().setIcon(this, if (entity.showIcon.isNullOrBlank()) entity.iconState else entity.showIcon)
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