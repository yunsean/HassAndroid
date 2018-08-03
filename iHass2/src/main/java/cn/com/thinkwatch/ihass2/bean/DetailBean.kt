package cn.com.thinkwatch.ihass2.bean

import android.content.Context
import android.text.method.ScrollingMovementMethod
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.bus.EntityClicked
import cn.com.thinkwatch.ihass2.bus.EntityLongClicked
import cn.com.thinkwatch.ihass2.model.Attribute
import cn.com.thinkwatch.ihass2.model.AttributeRender
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.Metadata
import com.dylan.common.rx.RxBus2
import kotlinx.android.synthetic.main.tile_detail.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.sdk25.coroutines.onLongClick

class DetailBean(entity: JsonEntity): BaseBean(entity) {
    override fun layoutResId(): Int = R.layout.tile_detail
    override fun bindToView(itemView: View, context: Context) {
        itemView.name.text = if (entity.showName.isNullOrBlank()) entity.friendlyName else entity.showName
        itemView.icon.text = entity.mdiIcon
        itemView.state.text = entity.state
        val sb = StringBuffer()
        try {
            val attributes = entity.attributes
            if (attributes != null) {
                Attribute::class.java.declaredFields.forEach {
                    val metadata = it.getAnnotation(Metadata::class.java)
                    try {
                        if (metadata != null) {
                            it.isAccessible = true
                            var value: Any? = it.get(attributes)
                            if (value != null) {
                                if (metadata.display.isNotBlank()) {
                                    val clazz = Class.forName(metadata.display)
                                    if (clazz != null) {
                                        val obj = clazz.newInstance()
                                        if (obj is AttributeRender) value = obj.render(value)
                                    }
                                }
                                sb.append(metadata.name).append("：").append(value.toString()).append("\n")
                            }
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
            sb.trim { it == '\r' || it == '\n' }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        itemView.detail.visibility = if (sb.isEmpty()) View.GONE else View.VISIBLE
        itemView.detail.text = sb.toString()
        itemView.detail.setMovementMethod(ScrollingMovementMethod.getInstance())
        itemView.detail.setHorizontallyScrolling(true)
        itemView.detail.setFocusable(false)
        itemView.contentView.onClick { RxBus2.getDefault().post(EntityClicked(entity)) }
        itemView.contentView.onLongClick {
            RxBus2.getDefault().post(EntityLongClicked(entity))
            true
        }
        itemView.contentView.setOnTouchListener(getTouchListener(itemView.contentView))
    }
}