package cn.com.thinkwatch.ihass2.bean

import android.content.Context
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.bus.EntityClicked
import cn.com.thinkwatch.ihass2.bus.EntityLongClicked
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import com.dylan.common.rx.RxBus2
import kotlinx.android.synthetic.main.tile_circle.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.sdk25.coroutines.onLongClick

class CircleBean(entity: JsonEntity): BaseBean(entity) {
    override fun layoutResId(): Int = R.layout.tile_circle
    override fun bindToView(itemView: View, context: Context) {
        MDIFont.setIcon(itemView.icon, if (entity.showIcon.isNullOrBlank()) entity.iconState else entity.showIcon)
        itemView.name.setText(if (entity.showName.isNullOrBlank()) entity.friendlyName else entity.showName)
        itemView.contentView.onClick { RxBus2.getDefault().post(EntityClicked(entity)) }
        itemView.contentView.onLongClick {
            RxBus2.getDefault().post(EntityLongClicked(entity))
            true
        }
    }
}