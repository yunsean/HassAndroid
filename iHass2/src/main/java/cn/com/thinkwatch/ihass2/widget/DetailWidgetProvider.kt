package cn.com.thinkwatch.ihass2.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.db.LocalStorage
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.*
import cn.com.thinkwatch.ihass2.ui.EmptyActivity
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.loges
import org.jetbrains.anko.dip
import org.json.JSONObject

class DetailWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context?, appWidgetManager: AppWidgetManager?, appWidgetIds: IntArray?) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        if (context == null) return
        appWidgetIds?.forEach {
            val entity = context.db.getWidgetEntity(it)
            if (entity.size != 1) return@forEach
            updateEntityWidget(context, it, entity.get(0))
        }
    }

    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        if (context == null) return
        val entityId = intent?.extras?.getString("entityId")
        if (entityId == null) return
        val entity = context.db.getEntity(entityId)
        if (entity == null) return
        loges(entity.entityId)
        if (intent.action.equals(CLICK_ACTION)) {
            if (entity.isSwitch && entity.isStateful) {
                RxBus2.getDefault().post(ServiceRequest(entity.domain, "toggle", entity.entityId))
            }
        }
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        if (context == null) return
        appWidgetIds?.forEach { context.db.delWidget(it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context?, appWidgetManager: AppWidgetManager?, appWidgetId: Int, newOptions: Bundle?) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    companion object {
        private val CLICK_ACTION = "cn.com.thinkwatch.ihass2.action.CLICK"
        fun updateEntityWidget(context: Context, widgetId: Int, entity: JsonEntity?) {
            if (entity == null) return
            val manager = AppWidgetManager.getInstance(context)
            val widget = context.db.getWidget(widgetId)
            if (widget == null) return
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
            val iconColor = if (entity.isActivated) widget.activeColor else widget.normalColor
            val iconText = if (entity.showIcon.isNullOrBlank()) entity.mdiIcon else entity.showIcon
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_detail)
            remoteViews.setViewVisibility(R.id.detail, if (sb.isEmpty()) View.GONE else View.VISIBLE)
            remoteViews.setTextViewText(R.id.detail, sb.toString())
            remoteViews.setTextViewText(R.id.name, if (entity.showName.isNullOrEmpty()) entity.friendlyName else entity.showName)
            remoteViews.setTextViewText(R.id.state, entity.friendlyStateRow)
            remoteViews.setTextColor(R.id.name, iconColor)
            remoteViews.setTextColor(R.id.state, iconColor)
            remoteViews.setTextViewTextSize(R.id.name, TypedValue.COMPLEX_UNIT_SP, widget.textSize + 2f)
            remoteViews.setTextViewTextSize(R.id.state, TypedValue.COMPLEX_UNIT_SP, widget.textSize.toFloat())
            remoteViews.setTextViewTextSize(R.id.detail, TypedValue.COMPLEX_UNIT_SP, widget.textSize - 2f)
            remoteViews.setImageViewBitmap(R.id.icon, MDIFont.get().drawIcon(context, iconText, iconColor, context.dip(widget.imageSize)))
            remoteViews.setTextColor(R.id.detail, widget.normalColor)
            remoteViews.setInt(R.id.contentView, "setBackgroundColor", widget.backColor)

            val pendingIntent = if (entity.isSwitch && entity.isStateful) {
                PendingIntent.getBroadcast(context, widgetId, Intent(context, RowWidgetProvider::class.java)
                        .setAction(CLICK_ACTION)
                        .putExtra("entityId", entity.entityId), PendingIntent.FLAG_CANCEL_CURRENT)
            } else {
                PendingIntent.getActivity(context, widgetId, Intent(context, EmptyActivity::class.java)
                        .setAction(CLICK_ACTION)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        .putExtra("entityId", entity.entityId)
                        .putExtra("event", "widgetClicked"), PendingIntent.FLAG_CANCEL_CURRENT)
            }
            remoteViews.setOnClickPendingIntent(R.id.contentView, pendingIntent)
            manager.updateAppWidget(widgetId, remoteViews)
        }
    }
}