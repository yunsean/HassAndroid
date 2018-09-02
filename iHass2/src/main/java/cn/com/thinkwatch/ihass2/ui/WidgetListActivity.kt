package cn.com.thinkwatch.ihass2.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.enums.WidgetType
import cn.com.thinkwatch.ihass2.model.Widget
import cn.com.thinkwatch.ihass2.widget.DetailConfigActivity
import cn.com.thinkwatch.ihass2.widget.RowConfigActivity
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.dip2px
import com.yunsean.dynkotlins.extensions.start
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.activity_hass_widget_list.*
import kotlinx.android.synthetic.main.listitem_widget_item.view.*
import org.jetbrains.anko.ctx
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.sdk25.coroutines.onClick


class WidgetListActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_widget_list)
        setTitle("小部件管理", true)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        ui()
    }
    override fun onResume() {
        super.onResume()
        adapter.items = db.getWidgets()
    }

    private lateinit var adapter: RecyclerAdapter<Widget>
    private fun ui() {
        this.adapter = RecyclerAdapter(R.layout.listitem_widget_item, null) {
            view, index, widget ->
            view.icon.imageResource = if (widget.widgetType == WidgetType.detail) R.drawable.group_type_tile else R.drawable.ic_view_week_black_24dp
            view.name.text = widget.widgetType.desc
            val entity = db.getWidgetEntity(widget.widgetId)
            view.count.text = "共${entity.size}个组件"
            val detail = entity.fold(StringBuffer()) { t1, t2-> t1.append(t2.friendlyName).append(" ")}
            view.entites.text = detail.toString()
            view.onClick {
                when (widget.widgetType) {
                    WidgetType.row-> Intent(ctx, RowConfigActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget.widgetId)
                            .start(ctx)
                    WidgetType.detail-> Intent(ctx, DetailConfigActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget.widgetId)
                            .start(ctx)
                }
            }
        }
        this.recyclerView.adapter = this.adapter
        this.recyclerView.layoutManager = LinearLayoutManager(ctx)
        this.recyclerView.addItemDecoration(RecyclerViewDivider()
                .setColor(0xffeeeeee.toInt())
                .setSize(dip2px(1f)))
        this.cleanInvalid.onClick {
            db.getWidgets().forEach {
                val manager = AppWidgetManager.getInstance(ctx)
                if (manager.getAppWidgetInfo(it.widgetId) == null) {
                    db.delWidget(it.widgetId)
                }
            }
            adapter.items = db.getWidgets()
        }
    }
}

