package cn.com.thinkwatch.ihass2.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.ui.EmptyActivity
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.loges
import com.yunsean.dynkotlins.extensions.logis
import org.jetbrains.anko.dip

class RowWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context?, appWidgetManager: AppWidgetManager?, appWidgetIds: IntArray?) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        if (context == null) return
        appWidgetIds?.forEach {
            val entity = context.db.getWidgetEntity(it)
            if (entity.size < 1) return@forEach
            updateEntityWidget(context, it, entity)
        }
    }

    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        logis("onReceive()")
        if (context == null) return
        val entityId = intent?.extras?.getString("entityId")
        logis("onReceive($entityId)")
        if (entityId == null) return
        val entity = context.db.getEntity(entityId)
        if (entity == null) return
        loges(entity.entityId)
        if (intent.action.startsWith("cn.com.thinkwatch.ihass2.action.CLICK")) {
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
        private val CLICK_ACTION0 = "cn.com.thinkwatch.ihass2.action.CLICK0"
        private val CLICK_ACTION1 = "cn.com.thinkwatch.ihass2.action.CLICK1"
        private val CLICK_ACTION2 = "cn.com.thinkwatch.ihass2.action.CLICK2"
        private val CLICK_ACTION3 = "cn.com.thinkwatch.ihass2.action.CLICK3"
        private val CLICK_ACTION4 = "cn.com.thinkwatch.ihass2.action.CLICK4"
        private val CLICK_ACTION5 = "cn.com.thinkwatch.ihass2.action.CLICK5"
        private val CLICK_ACTION6 = "cn.com.thinkwatch.ihass2.action.CLICK6"
        private val CLICK_ACTION7 = "cn.com.thinkwatch.ihass2.action.CLICK7"
        private val CLICK_ACTION8 = "cn.com.thinkwatch.ihass2.action.CLICK8"
        private val CLICK_ACTION9 = "cn.com.thinkwatch.ihass2.action.CLICK9"
        private val actions = arrayOf(CLICK_ACTION0, CLICK_ACTION1, CLICK_ACTION2, CLICK_ACTION3, CLICK_ACTION4, CLICK_ACTION5, CLICK_ACTION6, CLICK_ACTION7, CLICK_ACTION8, CLICK_ACTION9)
        fun updateEntityWidget(context: Context, widgetId: Int, entities: List<JsonEntity>) {
            val manager = AppWidgetManager.getInstance(context)
            val parentView = RemoteViews(context.packageName, R.layout.widget_row)
            parentView.removeAllViews(R.id.contentView)
            val widget = context.db.getWidget(widgetId)
            if (widget == null) return
            entities.forEachIndexed { index, entity->
                if (index >= actions.size) return@forEachIndexed
                val remoteView = RemoteViews(context.packageName, R.layout.widget_item)
                val iconColor = if (entity.isActivated) widget.activeColor else widget.normalColor
                val iconText = if (entity.showIcon.isNullOrBlank()) if (entity.isSensor && entity.attributes?.unitOfMeasurement != null) entity.state else entity.iconState else entity.showIcon
                remoteView.setOnClickPendingIntent(R.id.itemView, PendingIntent.getActivity(context, widgetId, Intent(context, EmptyActivity::class.java)
                        .setAction(actions.get(index))
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        .putExtra("entityId", entity.entityId)
                        .putExtra("event", "widgetClicked"), PendingIntent.FLAG_CANCEL_CURRENT))
                remoteView.setTextViewText(R.id.name, if (entity.showName.isNullOrBlank()) entity.friendlyName else entity.showName)
                remoteView.setImageViewBitmap(R.id.icon, MDIFont.get().drawIcon(context, iconText, iconColor, context.dip(if (iconText?.startsWith("mdi:") ?: false) widget.imageSize else widget.imageSize * 2 / 3), context.dip(widget.imageSize)))
                remoteView.setTextColor(R.id.name, iconColor)
                remoteView.setTextViewTextSize(R.id.name, TypedValue.COMPLEX_UNIT_SP, widget.textSize.toFloat())
                parentView.addView(R.id.contentView, remoteView)
            }
            parentView.setInt(R.id.contentView, "setBackgroundColor", widget.backColor)
            manager.updateAppWidget(widgetId, parentView)
        }
    }
}