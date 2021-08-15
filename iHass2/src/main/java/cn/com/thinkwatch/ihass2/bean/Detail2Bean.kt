package cn.com.thinkwatch.ihass2.bean

import android.content.Context
import android.support.v7.widget.CardView
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.bus.EntityClicked
import cn.com.thinkwatch.ihass2.bus.EntityLongClicked
import cn.com.thinkwatch.ihass2.db.LocalStorage
import cn.com.thinkwatch.ihass2.model.*
import cn.com.thinkwatch.ihass2.utils.viewAt
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.dip2px
import kotlinx.android.synthetic.main.tile_detail.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.json.JSONObject

class Detail2Bean(entity: JsonEntity, val translucence: Boolean): BaseBean(entity) {
    override fun layoutResId(): Int = R.layout.tile_detail2
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
        itemView.viewAt<TextView>(R.id.name).apply {
            text = if (entity.showName.isNullOrEmpty()) entity.friendlyName else entity.showName
            isActivated = entity.isActivated
        }
        itemView.viewAt<TextView>(R.id.icon).apply {
            MDIFont.get().setIcon(this, if (entity.showIcon.isNullOrBlank()) entity.mdiIcon else entity.showIcon)
            isActivated = entity.isActivated
        }
        if (entity.isPersistentNotification) {
            itemView.viewAt<TextView>(R.id.state).text = entity.attributes?.message
            itemView.viewAt<TextView>(R.id.detail).visibility = View.GONE
        } else {
            val isNumberic = entity.friendlyState?.toDoubleOrNull() != null
            itemView.viewAt<TextView>(R.id.state).text = entity.friendlyState + if (isNumberic) (entity.attributes?.unitOfMeasurement ?: "") else ""
            val sb = StringBuffer()
            try {
                val attributes = entity.attributes
                if (attributes?.ihassDetail != null && !attributes.ihassDetail.isNullOrBlank()) {
                    LocalStorage.instance.getDbEntity(entity.entityId)?.let {
                        try {
                            JSONObject(it.rawJson).optJSONObject("attributes")?.let { attr->
                                attributes.ihassDetail.toString().trim('"').split(',').forEach {
                                    it.trim().let {
                                        if (it.isBlank()) return@let
                                        val parts = it.split('!')
                                        val value = attr.optString(parts[0])
                                        if (value.isNullOrBlank()) return@let
                                        when (parts.size) {
                                            1-> sb.append(parts[0]).append("：").append(value).append("\n")
                                            2-> sb.append(parts[1]).append("：").append(value).append("\n")
                                            3-> sb.append(parts[1]).append("：").append(value).append(parts[2]).append("\n")
                                            else-> return@let
                                        }
                                    }
                                }
                            }
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                } else if (attributes != null) {
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
                                    sb.append(metadata.name).append("：").append(value.toString()).append(metadata.unit).append("\n")
                                }
                            }
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            itemView.viewAt<TextView>(R.id.detail).apply {
                visibility = if (sb.isEmpty()) View.GONE else View.VISIBLE
                text = sb.toString().trim { it == '\r' || it == '\n' }
            }
        }
        itemView.viewAt<TextView>(R.id.detail).apply {
            movementMethod = ScrollingMovementMethod.getInstance()
            setHorizontallyScrolling(true)
            isFocusable = false
        }
        itemView.viewAt<View>(R.id.contentView).apply {
            setOnClickListener { RxBus2.getDefault().post(EntityClicked(entity)) }
            setOnLongClickListener {
                RxBus2.getDefault().post(EntityLongClicked(entity))
                true
            }
            setOnTouchListener(getTouchListener(this))
        }
    }
}