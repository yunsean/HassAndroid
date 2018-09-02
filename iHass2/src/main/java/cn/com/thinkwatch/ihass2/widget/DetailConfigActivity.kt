package cn.com.thinkwatch.ihass2.widget

import android.app.Activity
import android.app.Dialog
import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.SeekBar
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.enums.WidgetType
import cn.com.thinkwatch.ihass2.model.*
import cn.com.thinkwatch.ihass2.ui.MdiListActivity
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
import kotlinx.android.synthetic.main.activity_detail_widget_config.*
import kotlinx.android.synthetic.main.dialog_color_picker.view.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import kotlinx.android.synthetic.main.dialog_widget_item_edit.view.*
import kotlinx.android.synthetic.main.listitem_entity_item.view.*
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk25.coroutines.onCheckedChange
import org.jetbrains.anko.sdk25.coroutines.onClick


class DetailConfigActivity : BaseActivity() {

    private var widgetId: Int = 0
    private var backColorValue: Int = 0
    private var normalColorValue: Int = 0xff656565.toInt()
    private var activeColorValue: Int = 0xff0288D1.toInt()
    private var textSizeValue: Int = 12
    private var imageSizeValue: Int = 30
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail_widget_config)
        setTitle("详情元素", false, "确定")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        this.demos.visibility = View.GONE
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
        if (checkedEntity == null) return showError("请选择要添加的元素！")
        db.addWidget(widgetId, WidgetType.detail, backColorValue, normalColorValue, activeColorValue, textSizeValue, imageSizeValue, listOf(checkedEntity!!))
        DetailWidgetProvider.updateEntityWidget(this, widgetId, checkedEntity!!)
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_APPWIDGET_ID, widgetId).putExtra("widgetId", widgetId))
        finish()
    }

    private var showEntities = mutableListOf<JsonEntity>()
    private var checkedEntity: JsonEntity? = null
    set(value) {
        field = value
        updateDemo()
    }
    private var allEntities: List<JsonEntity>? = null
    private lateinit var adapter: RecyclerAdapter<JsonEntity>
    private var filterRunnable: Runnable = object: Runnable {
        override fun run() {
            filter()
        }
    }
    private fun ui() {
        this.titlebarRight?.setOnLongClickListener(object: View.OnLongClickListener {
            override fun onLongClick(p0: View?): Boolean {
                val widgets = db.getWidgets(WidgetType.detail)
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
                                if (entity.size > 0) checkedEntity = entity.get(0)
                                else checkedEntity = null
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
                                adapter.notifyDataSetChanged()
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
            view.checked.visibility = if (checkedEntity == item) View.VISIBLE else View.GONE
            view.onClick {
                checkedEntity = item
                adapter.notifyDataSetChanged()
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

        setupColor.onCheckedChange { buttonView, isChecked ->
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
        backColor.onClick {
            colorPicker(backColorValue, backColor) { backColorValue = it }
        }
        normalColor.backgroundColor = normalColorValue
        normalColor.onClick {
            colorPicker(normalColorValue, normalColor) { normalColorValue = it }
        }
        activeColor.backgroundColor = activeColorValue
        activeColor.onClick {
            colorPicker(activeColorValue, activeColor) { activeColorValue = it }
        }
        textSize.progress = textSizeValue - 8
        textSize.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {}
            override fun onStartTrackingTouch(p0: SeekBar?) { }
            override fun onStopTrackingTouch(p0: SeekBar?) {
                textSizeValue = textSize.progress + 8
                updateDemo()
            }
        })
        imageSize.progress = imageSizeValue - 20
        imageSize.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {}
            override fun onStartTrackingTouch(p0: SeekBar?) { }
            override fun onStopTrackingTouch(p0: SeekBar?) {
                imageSizeValue = imageSize.progress + 20
                updateDemo()
            }
        })

        demos.onClick {
            checkedEntity?.let { entity->
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
                        contentView.delete.visibility = View.GONE
                    }
                }, intArrayOf(R.id.delete, R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                    override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                        dialog.dismiss()
                        if (clickedView.id == R.id.ok) {
                            entity.showName = contentView.entityName.text()
                            entity.showIcon = contentView.entityIcon.tag as String?
                            updateDemo()
                        }
                    }
                })
            }
        }
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
    private fun updateDemo() {
        checkedEntity?.let {
            val sb = StringBuffer()
            try {
                val attributes = it.attributes
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
            val iconText = if (it.showIcon.isNullOrBlank()) it.mdiIcon else it.showIcon
            name.textColor = if (it.isActivated) activeColorValue else normalColorValue
            state.textColor = if (it.isActivated) activeColorValue else normalColorValue
            icon.imageBitmap = MDIFont.get().drawIcon(ctx, iconText, if (it.isActivated) activeColorValue else normalColorValue, dip(imageSizeValue))
            detail.textColor = normalColorValue
            name.text = if (it.showName.isNullOrBlank()) it.friendlyName else it.showName
            state.text = it.friendlyStateRow
            detail.text = sb.toString()
            detail.visibility = if (sb.isEmpty()) View.GONE else View.VISIBLE
        }
        demos.visibility = if (checkedEntity == null) View.GONE else View.VISIBLE
        demos.setBackgroundColor(backColorValue)
        name.textSize = textSizeValue + 2f
        state.textSize = textSizeValue.toFloat()
        detail.textSize = textSizeValue - 2f
    }
    private fun filter() {
        val keyword = act.keyword.text()
        showEntities.clear()
        checkedEntity?.let { showEntities.add(it) }
        allEntities?.filter {
            (keyword.isBlank() or it.friendlyName.contains(keyword) or (it.state?.contains(keyword) ?: false)) and (checkedEntity != it)
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
            if (it.size > 0) checkedEntity = it.get(0)
            loading?.visibility = View.GONE
            filter()
        }.error {
            it.printStackTrace()
            toastex(it.message ?: "未知错误")
            finish()
        }
    }
}

