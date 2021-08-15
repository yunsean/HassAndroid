package cn.com.thinkwatch.ihass2.widget

import android.app.Activity
import android.app.Dialog
import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.adapter.CreatableAdapter
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.WidgetChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.enums.WidgetType
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.ui.MdiListActivity
import cn.com.thinkwatch.ihass2.utils.SimpleItemTouchHelperCallback
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Animations
import com.dylan.common.sketch.Sketch
import com.dylan.common.utils.Utility
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.annimation.MarginAnimation
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import io.reactivex.Observable
import kotlinx.android.synthetic.main.activity_row_widget_config.*
import kotlinx.android.synthetic.main.dialog_color_picker.view.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import kotlinx.android.synthetic.main.dialog_panel_add_entity.view.*
import kotlinx.android.synthetic.main.listitem_entity_item.view.*
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk25.coroutines.onClick


class RowConfigActivity : BaseActivity() {

    private var widgetId: Int = 0
    private var backColorValue: Int = 0
    private var normalColorValue: Int = 0xff656565.toInt()
    private var activeColorValue: Int = 0xff0288D1.toInt()
    private var textSizeValue: Int = 12
    private var imageSizeValue: Int = 30
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_row_widget_config)
        setTitle("Hass小部件", false, "确定")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        this.widgetId = intent.extras?.getInt(EXTRA_APPWIDGET_ID, INVALID_APPWIDGET_ID) ?: INVALID_APPWIDGET_ID
        if (widgetId == INVALID_APPWIDGET_ID) return finish()
        db.getWidget(widgetId)?.let {
            backColorValue = it.backColor
            normalColorValue = it.normalColor
            activeColorValue = it.activeColor
            textSizeValue = it.textSize
            imageSizeValue = it.imageSize
        }
        ui()
        data()
    }
    override fun doRight() {
        if (checkedEntities.size < 1) return showError("请选择要添加的元素！")
        db.addWidget(widgetId, WidgetType.row, backColorValue, normalColorValue, activeColorValue, textSizeValue, imageSizeValue, checkedEntities)
        RowWidgetProvider.updateEntityWidget(this, widgetId, checkedEntities)
        RxBus2.getDefault().post(WidgetChanged())
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_APPWIDGET_ID, widgetId).putExtra("widgetId", widgetId))
        finish()
    }

    private var showEntities = mutableListOf<JsonEntity>()
    private var checkedEntities = mutableListOf<JsonEntity>()
    private var allEntities: List<JsonEntity>? = null
    private lateinit var adapter: RecyclerAdapter<JsonEntity>
    private lateinit var touchHelper: ItemTouchHelper
    private var filterRunnable: Runnable = object: Runnable {
        override fun run() {
            filter()
        }
    }
    private fun ui() {
        this.titlebarRight?.setOnLongClickListener(object: View.OnLongClickListener {
            override fun onLongClick(p0: View?): Boolean {
                val widgets = db.getWidgets(WidgetType.row)
                if (widgets.size < 1) return true
                showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
                    override fun onSettingDialog(dialog: Dialog, contentView: View) {
                        contentView.recyclerView.layoutManager = LinearLayoutManager(ctx)
                        val adaptar = RecyclerAdapter(R.layout.listitem_widget_item, widgets) {
                            view, index, widget ->
                            Sketch.set_iv(view, R.id.icon, if (widget.widgetType == WidgetType.detail) R.drawable.group_type_tile else R.drawable.ic_view_week_black_24dp)
                            Sketch.set_tv(view, R.id.name, widget.widgetType.desc)
                            val entity = db.getWidgetEntity(widget.widgetId)
                            Sketch.set_tv(view, R.id.count, "共${entity.size}个组件")
                            val detail = entity.fold(StringBuffer()) { t1, t2-> t1.append(t2.friendlyName).append(" ")}
                            Sketch.set_tv(view, R.id.entites, detail.toString())
                            view.onClick {
                                checkedEntities.clear()
                                showEntities.clear()
                                allEntities?.forEach {  e->
                                    entity.find { it.entityId == e.entityId }?.let {
                                        e.showIcon = it.showIcon
                                        e.showName = it.showName
                                        e.displayOrder = it.displayOrder
                                        checkedEntities.add(e)
                                    } ?: {
                                        showEntities.add(e)
                                    }()
                                }
                                checkedEntities.sortBy { it.displayOrder }
                                backColorValue = widget.backColor
                                normalColorValue = widget.normalColor
                                activeColorValue = widget.activeColor
                                imageSizeValue = widget.imageSize
                                textSizeValue = widget.textSize
                                demos.setBackgroundColor(backColorValue)
                                backColor.backgroundColor = backColorValue
                                normalColor.backgroundColor = normalColorValue
                                activeColor.backgroundColor = activeColorValue
                                textSize.progress = textSizeValue - 8
                                imageSize.progress = imageSizeValue - 20
                                updateDemo()
                                dialog.dismiss()
                            }
                        }
                        val footer = layoutInflater.inflate(R.layout.layout_cancel, contentView.recyclerView, false)
                        footer.onClick { dialog.dismiss() }
                        contentView.recyclerView.adapter = RecyclerAdapterWrapper(adaptar)
                                .addFootView(footer)
                        contentView.recyclerView.addItemDecoration(RecyclerViewDivider()
                                .setColor(0xffeeeeee.toInt())
                                .setSize(1))
                    }
                }, null, null)
                return true
            }
        })
        this.adapter = RecyclerAdapter(R.layout.listitem_entity_item, showEntities) {
            view, index, item ->
            view.name.text = item.friendlyName
            MDIFont.get().setIcon(view.icon, item.mdiIcon)
            view.value.text = item.friendlyState
            view.checked.visibility = View.GONE
            view.onClick {
                if (checkedEntities.size >= 10) return@onClick toastex("最多只允许添加十个控件！")
                checkedEntities.add(item)
                showEntities.remove(item)
                updateDemo()
            }
        }
        this.recyclerView.adapter = adapter
        this.recyclerView.layoutManager = LinearLayoutManager(this)
        this.recyclerView.addItemDecoration(RecyclerViewDivider()
                .setColor(0xffeeeeee.toInt())
                .setSize(dip2px(1f)))
        this.keyword.onChanged {
            act.keyword.removeCallbacks(filterRunnable)
            act.keyword.postDelayed(filterRunnable, 1000)
        }
        this.keyword.setOnEditorActionListener() { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                act.keyword.removeCallbacks(filterRunnable)
                Utility.hideSoftKeyboard(act)
                filter()
            }
            false
        }

        val adapter = CreatableAdapter(R.layout.widget_item, checkedEntities) {
            view, index, entity, holder ->
            val iconColor = if (entity.isActivated) activeColorValue else normalColorValue
            val iconText = if (entity.showIcon.isNullOrBlank()) if (entity.isSensor && entity.attributes?.unitOfMeasurement != null) entity.state else entity.iconState else entity.showIcon
            view.findViewById<TextView>(R.id.name).apply {
                textColor = iconColor
                text = if (entity.showName.isNullOrEmpty()) entity.friendlyName else entity.showName
                textSize = textSizeValue.toFloat()
            }
            view.findViewById<ImageView>(R.id.icon).apply {
                imageBitmap = MDIFont.get().drawIcon(context, iconText, iconColor, context.dip(if (iconText?.startsWith("mdi:") ?: false) imageSizeValue else imageSizeValue * 2 / 3), context.dip(imageSizeValue))
            }
            view.onClick {
                showDialog(R.layout.dialog_widget_item_edit, object: OnSettingDialogListener {
                    override fun onSettingDialog(dialog: Dialog, contentView: View) {
                        contentView.entityName.setText(entity.showName ?: entity.friendlyName)
                        MDIFont.get().setIcon(contentView.entityIcon, if (entity.showIcon.isNullOrBlank()) entity.mdiIcon else entity.showIcon)
                        contentView.entityIcon.tag = entity.showIcon
                        contentView.iconPanel.onClick {
                            hotEntity = entity
                            hotMdiView = contentView.entityIcon
                            activity(MdiListActivity::class.java, 106)
                        }
                    }
                }, intArrayOf(R.id.delete, R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                    override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                        dialog.dismiss()
                        if (clickedView.id == R.id.ok) {
                            entity.showName = contentView.entityName.text()
                            entity.showIcon = contentView.entityIcon.tag as String?
                            updateDemo()
                        } else if (clickedView.id == R.id.delete) {
                            checkedEntities.remove(entity)
                            showEntities.add(entity)
                            updateDemo()
                        }
                    }
                })
            }
        }
        this.demos.adapter = adapter
        this.demos.layoutManager = GridLayoutManager(ctx, 1)
        val callback = SimpleItemTouchHelperCallback(adapter)
        this.touchHelper = ItemTouchHelper(callback)
        this.touchHelper.attachToRecyclerView(this.demos)

        setupColor.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                recyclerView.visibility = View.GONE
                customizePanel.visibility = View.VISIBLE
                Animations.MarginAnimation(customizePanel, MarginAnimation.Margin.Bottom, recyclerView.height, 0)
                        .duration(300)
                        .start()
            } else {
                recyclerView.visibility = View.VISIBLE
                Animations.MarginAnimation(customizePanel, MarginAnimation.Margin.Bottom, 0, recyclerView.height)
                        .duration(300)
                        .animationListener { customizePanel.visibility = View.GONE }
                        .start()
            }
        }
        demos.setBackgroundColor(backColorValue)
        backColor.backgroundColor = backColorValue
        normalColor.backgroundColor = normalColorValue
        activeColor.backgroundColor = activeColorValue
        textSize.progress = textSizeValue - 8
        imageSize.progress = imageSizeValue - 20
        backColor.onClick {
            colorPicker(backColorValue, backColor) { backColorValue = it; demos.setBackgroundColor(backColorValue) }
        }
        normalColor.onClick {
            colorPicker(normalColorValue, normalColor) { normalColorValue = it }
        }
        activeColor.onClick {
            colorPicker(activeColorValue, activeColor) { activeColorValue = it }
        }
        textSize.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {}
            override fun onStartTrackingTouch(p0: SeekBar?) { }
            override fun onStopTrackingTouch(p0: SeekBar?) {
                textSizeValue = textSize.progress + 8
                updateDemo()
            }
        })
        imageSize.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {}
            override fun onStartTrackingTouch(p0: SeekBar?) { }
            override fun onStopTrackingTouch(p0: SeekBar?) {
                imageSizeValue = imageSize.progress + 20
                updateDemo()
            }
        })
    }
    private fun colorPicker(initValue: Int, view: View, onPicked: ((Int)->Unit)) {
        showDialog(R.layout.dialog_color_picker, object: OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                contentView.colorPicker.addSVBar(contentView.svbarPicker)
                contentView.colorPicker.addOpacityBar(contentView.opacityPicker)
                contentView.colorPicker.color = initValue
            }
        }, intArrayOf(R.id.cancel, R.id.ok), object : OnDialogItemClickedListener {
            override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                dialog.dismiss()
                if (clickedView.id == R.id.ok) {
                    onPicked(contentView.colorPicker.color)
                    view.backgroundColor = contentView.colorPicker.color
                    updateDemo()
                }
            }
        })
    }
    private var hotEntity: JsonEntity? = null
    private var hotMdiView: TextView? = null
    @ActivityResult(requestCode = 106)
    private fun afterChooseIcon(data: Intent) {
        val icon = data.getStringExtra("icon")
        MDIFont.get().setIcon(hotMdiView, if (icon.isNullOrBlank()) hotEntity?.mdiIcon else icon)
        hotMdiView?.tag = data.getStringExtra("icon")
        adapter.notifyDataSetChanged()
    }


    private fun updateDemo() {
        this.adapter.notifyDataSetChanged()
        this.demos.layoutManager = GridLayoutManager(ctx, Math.max(1, checkedEntities.size))
        this.demos.adapter?.notifyDataSetChanged()
    }
    private fun filter() {
        val keyword = act.keyword.text()
        showEntities.clear()
        allEntities?.filter {
            (keyword.isBlank() or it.friendlyName.contains(keyword, true) or it.entityId.contains(keyword) or (it.state?.contains(keyword, true) ?: false)) and !checkedEntities.contains(it)
        }?.let {
            showEntities.addAll(it)
        }
        adapter.notifyDataSetChanged()
    }
    private fun data() {
        db.async {
            db.listEntity()
        }.flatMap {
            allEntities = it
            Observable.just(db.getWidgetEntity(widgetId))
        }.nextOnMain {
            checkedEntities.clear()
            checkedEntities.addAll(it)
            loading?.visibility = View.GONE
            filter()
            updateDemo()
        }.error {
            it.printStackTrace()
            toastex(it.message ?: "未知错误")
            finish()
        }
    }
}

