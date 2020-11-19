package cn.com.thinkwatch.ihass2.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.PanelChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.enums.ItemType
import cn.com.thinkwatch.ihass2.enums.TileType
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.model.Panel
import cn.com.thinkwatch.ihass2.utils.SimpleItemTouchHelperCallback
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Drawables
import com.dylan.dyn3rdparts.gallerypicker.ImageGridActivity
import com.dylan.dyn3rdparts.gallerypicker.ImagePicker
import com.dylan.dyn3rdparts.gallerypicker.bean.ImageItem
import com.dylan.dyn3rdparts.gallerypicker.loader.PicassoImageLoader
import com.dylan.dyn3rdparts.gallerypicker.view.CropImageView
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.dylan.uiparts.recyclerview.SwipeItemLayout
import com.fendoudebb.util.QuickBlur
import com.yunsean.dynkotlins.extensions.*
import kotlinx.android.synthetic.main.activity_hass_panel_edit.*
import kotlinx.android.synthetic.main.dialog_panel_add_entity.view.*
import kotlinx.android.synthetic.main.dialog_panel_add_group.view.*
import kotlinx.android.synthetic.main.listitem_panel_edit_item.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.ctx
import org.jetbrains.anko.layoutInflater
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.io.File
import java.io.FileOutputStream

class PanelEditActivity : BaseActivity() {

    private var backImage: String? = null
    private var panelId: Long = 0L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_panel_edit)
        setTitle("面板编辑", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        panelId = intent.getLongExtra("panelId", 0L)
        ui()
        if (panelId != 0L) data()
    }
    override fun doRight() {
        val panelName = this.panelName.text()
        var created = false
        if (panelId == 0L) {
            panelId = db.addPanel(Panel(panelName, backImage = backImage, tileAlpha = if (translucence.isChecked) .7f else 1f))
            if (panelId == 0L) return
            created = true
        } else {
            db.getPanel(panelId)?.let {
                it.name = panelName
                it.backImage = backImage
                it.tileAlpha = if (!backImage.isNullOrBlank() and translucence.isChecked) .8f else 1f
                db.savePanel(it)
            }
        }
        entities.forEachIndexed { index, entity -> entity.displayOrder = index }
        db.saveDashboard(panelId, entities)
        RxBus2.getDefault().post(PanelChanged(panelId, created))
        finish()
    }

    private var entities = mutableListOf<JsonEntity>()
    private lateinit var adapter: RecyclerAdapter<JsonEntity>
    private lateinit var touchHelper: ItemTouchHelper
    private fun ui() {
        this.image.onClick {
            if (backImage.isNullOrEmpty()) {
                ImagePicker.getInstance()
                        .setImageLoader(PicassoImageLoader())
                        .setShowCamera(false)
                        .setCrop(true)
                        .setSaveRectangle(true)
                        .setMultiMode(false)
                        .setStyle(CropImageView.Style.RECTANGLE)
                        .setFocusWidth(containerView.width)
                        .setFocusHeight(containerView.height)
                        .setOutPutX(containerView.width)
                        .setOutPutY(containerView.height)
                startActivityForResult(Intent(this@PanelEditActivity, ImageGridActivity::class.java), 109)
            } else {
                backImage = null
                containerView.visibility = View.GONE
                translucence.visibility = View.GONE
            }
        }
        this.adapter = RecyclerAdapter(entities) {
            view, index, item, holder ->
            val bgColor: Int
            val name: String
            val icon: String?
            when (item.itemType) {
                ItemType.entity-> {
                    bgColor = 0xffffffff.toInt()
                    name = if (item.showName.isNullOrBlank()) item.friendlyName else item.showName ?: ""
                    icon = if (item.showIcon.isNullOrBlank()) item.mdiIcon else item.showIcon
                }
                ItemType.divider-> {
                    bgColor = 0xfff2f2f2.toInt()
                    name = item.showName ?: "分组"
                    icon = if (item.showIcon.isNullOrBlank()) "mdi:google-circles-communities" else item.showIcon
                }
                else-> {
                    bgColor = 0xffffffff.toInt()
                    name = ""
                    icon = null
                }
            }
            view.backgroundColor = bgColor
            view.name.text = name
            MDIFont.get().setIcon(view.icon, icon)
            view.icon.visibility = if (icon.isNullOrBlank()) View.INVISIBLE else View.VISIBLE
            view.order.setOnTouchListener { v, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) touchHelper.startDrag(holder)
                false
            }
            view.insert.onClick {
                doAdd(item)
            }
            view.delete.onClick {
                entities.remove(item)
                adapter.notifyDataSetChanged()
            }
            view.content.onClick {
                when(item.itemType) {
                    ItemType.entity-> {
                        createDialog(R.layout.dialog_panel_add_entity, object: OnSettingDialogListener {
                            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                                contentView.entityName.setText(item.showName ?: item.friendlyName)
                                MDIFont.get().setIcon(contentView.entityIcon, if (item.showIcon.isNullOrBlank()) item.mdiIcon else item.showIcon)
                                contentView.entityId.text = item.entityId
                                contentView.entityIcon.tag = item.showIcon
                                contentView.iconPanel.onClick {
                                    hotEntity = item
                                    hotMdiView = contentView.entityIcon
                                    Intent(act, MdiListActivity::class.java)
                                            .putExtra("icon", item.state)
                                            .start(act, 106)
                                }
                                contentView.entityType.clearCheck()
                                when (item.tileType) {
                                    TileType.list-> contentView.entityTypeList.isChecked = true
                                    TileType.circle-> contentView.entityTypeCircle.isChecked = true
                                    TileType.square-> contentView.entityTypeSquare.isChecked = true
                                    TileType.tile-> contentView.entityTypeTile.isChecked = true
                                    TileType.list2-> contentView.entityTypeList2.isChecked = true
                                    else-> contentView.entityTypeInherit.isChecked = true
                                }
                                contentView.entityAdd.onClick {
                                    contentView.entitySpan.setText(Math.min((contentView.entitySpan.text().toIntOrNull() ?: 0) + 1, 9).toString())
                                }
                                contentView.entitySub.onClick {
                                    contentView.entitySpan.setText(Math.max((contentView.entitySpan.text().toIntOrNull() ?: 2) - 1, 1).toString())
                                }
                                contentView.entitySpan.setText((if (item.columnCount < 1) 1 else item.columnCount).toString())
                            }
                        }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                            override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                                dialog.dismiss()
                                if (clickedView.id == R.id.ok) {
                                    var span = contentView.entitySpan.text().toIntOrNull() ?: 1
                                    if (span < 1) span = 1
                                    val type = if (contentView.entityTypeList.isChecked) TileType.list
                                    else if (contentView.entityTypeCircle.isChecked) TileType.circle
                                    else if (contentView.entityTypeSquare.isChecked) TileType.square
                                    else if (contentView.entityTypeTile.isChecked) TileType.tile
                                    else if (contentView.entityTypeList2.isChecked) TileType.list2
                                    else TileType.inherit
                                    item.columnCount = span
                                    item.tileType = type
                                    item.showName = contentView.entityName.text()
                                    item.showIcon = contentView.entityIcon.tag as String?
                                    adapter.notifyDataSetChanged()
                                }
                            }
                        }).show()
                    }
                    ItemType.divider-> {
                        createDialog(R.layout.dialog_panel_add_group, object: OnSettingDialogListener {
                            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                                contentView.groupName.setText(item.showName ?: "分组")
                                contentView.groupType.clearCheck()
                                when (item.tileType) {
                                    TileType.list-> contentView.groupTypeList.isChecked = true
                                    TileType.circle-> contentView.groupTypeCircle.isChecked = true
                                    TileType.square-> contentView.groupTypeSquare.isChecked = true
                                    TileType.list2-> contentView.groupTypeList2.isChecked = true
                                    else-> contentView.groupTypeTile.isChecked = true
                                }
                                contentView.groupAdd.onClick {
                                    contentView.groupColumns.setText(Math.min((contentView.groupColumns.text().toIntOrNull() ?: 0) + 1, 99).toString())
                                }
                                contentView.groupSub.onClick {
                                    contentView.groupColumns.setText(Math.max((contentView.groupColumns.text().toIntOrNull() ?: 2) - 1, 1).toString())
                                }
                                contentView.groupColumns.setText(item.columnCount.toString())
                            }
                        }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                            override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                                if (clickedView.id == R.id.ok) {
                                    val name = contentView.groupName.text.toString().trim()
                                    val type = if (contentView.groupTypeList.isChecked) TileType.list
                                    else if (contentView.groupTypeCircle.isChecked) TileType.circle
                                    else if (contentView.groupTypeSquare.isChecked) TileType.square
                                    else if (contentView.groupTypeList2.isChecked) TileType.list2
                                    else TileType.tile
                                    var column = contentView.groupColumns.text().toIntOrNull() ?: 0
                                    if (column < 1) return toastex("请设置每行显示列数！")
                                    item.showName = name
                                    item.tileType = type
                                    item.columnCount = column
                                    adapter.notifyDataSetChanged()
                                }
                                dialog.dismiss()
                            }
                        }).show()
                    }
                }
            }
        }.setOnCreateClicked {
            doAdd(null)
        }
        this.recyclerView.layoutManager = LinearLayoutManager(this)
        this.recyclerView.addOnItemTouchListener(SwipeItemLayout.OnSwipeItemTouchListener(this))
        this.recyclerView.adapter = this.adapter
        this.recyclerView.addItemDecoration(RecyclerViewDivider()
                .setColor(0xffeeeeee.toInt())
                .setSize(dip2px(1f)))
        val callback = SimpleItemTouchHelperCallback(adapter, false)
        this.touchHelper = ItemTouchHelper(callback)
        this.touchHelper.attachToRecyclerView(recyclerView)
    }
    private var panel: Panel? = null
    private fun data() {
        loading.visibility = View.VISIBLE
        db.async {
            panel = db.getPanel(panelId)
            db.readPanel(panelId)
        }.nextOnMain {
            loading.visibility = View.GONE
            panelName.setText(panel?.name)
            translucence.isChecked = panel?.tileAlpha ?: 1f < 0.9f
            panel?.backImage?.let {
                Glide.with(ctx).load(it).listener(object: RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean = false
                    override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                        translucence.visibility = View.VISIBLE
                        containerView.visibility = View.VISIBLE
                        return false
                    }
                }).into(containerView)
            }
            this.backImage = panel?.backImage
            entities.clear()
            entities.addAll(it)
            adapter.notifyDataSetChanged()
        }.error {
            it.toastex()
        }
    }

    @ActivityResult(requestCode = 109)
    private fun imagePicked(resultCode: Int,  data: Intent?) {
        if (data != null) {
            val images = data.getSerializableExtra(ImagePicker.EXTRA_RESULT_ITEMS) as ArrayList<ImageItem>?
            if (images != null && images.size > 0) {
                val uri = Uri.fromFile(File(images[0].path))
                var bitmap = Drawables.decodeUriAsBitmap(this, uri)
                if (bitmap != null) {
                    bitmap = QuickBlur.with(ctx).bitmap(bitmap).scale(2).radius(5).blur()
                    val outDir = "/data/data/${ctx.packageName}/files"
                    val outFile = File(outDir, System.currentTimeMillis().toString() + ".jpg")
                    try {
                        val fileOutputStream = FileOutputStream(outFile)
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 40, fileOutputStream)
                        fileOutputStream.flush()
                        fileOutputStream.close()
                        Glide.with(ctx).load(outFile.absoluteFile).listener(object: RequestListener<Drawable> {
                            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean = false
                            override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                                backImage = outFile.absolutePath
                                translucence.visibility = View.VISIBLE
                                containerView.visibility = View.VISIBLE
                                return false
                            }
                        }).into(containerView)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
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

    private fun doAdd(item: JsonEntity?) {
        val position = if (item == null) -1 else entities.indexOf(item)
        createDialog(R.layout.dialog_panel_add, null,
                intArrayOf(R.id.choiceBlank, R.id.choiceItem, R.id.choiceGroup, R.id.choiceCancel),
                object: OnDialogItemClickedListener {
                    override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                        dialog.dismiss()
                        when (clickedView.id) {
                            R.id.choiceBlank-> {
                                if (position == -1) entities.add(JsonEntity(itemType = ItemType.blank))
                                else entities.add(position, JsonEntity(itemType = ItemType.blank))
                                adapter.notifyDataSetChanged()
                            }
                            R.id.choiceGroup-> {
                                addGroup(position)
                            }
                            R.id.choiceItem-> {
                                Intent(act, EntityListActivity::class.java)
                                        .putExtra("position", position)
                                        .start(act, 105)
                            }
                        }
                    }
                }).show()
    }
    @ActivityResult(requestCode = 105)
    private fun afterAddEntity(data: Intent?) {
        val entityIds = data?.getStringArrayExtra("entityIds")
        var position = data?.getIntExtra("position", -1) ?: -1
        if (entityIds == null || entityIds.size < 1) return
        entityIds.forEach {
            db.getEntity(it)?.let {
                if (position == -1) entities.add(it)
                else entities.add(position++, it)
            }
        }
        adapter.notifyDataSetChanged()
    }
    private fun addGroup(position: Int) {
        createDialog(R.layout.dialog_panel_add_group, object: OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                contentView.groupAdd.onClick {
                    contentView.groupColumns.setText(Math.min((contentView.groupColumns.text().toIntOrNull() ?: 0) + 1, 99).toString())
                }
                contentView.groupSub.onClick {
                    contentView.groupColumns.setText(Math.max((contentView.groupColumns.text().toIntOrNull() ?: 2) - 1, 1).toString())
                }
            }
        }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
            override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                if (clickedView.id == R.id.ok) {
                    val name = contentView.groupName.text.toString().trim()
                    val type = if (contentView.groupTypeList.isChecked) TileType.list
                    else if (contentView.groupTypeCircle.isChecked) TileType.circle
                    else if (contentView.groupTypeSquare.isChecked) TileType.square
                    else TileType.tile
                    var column = contentView.groupColumns.text().toIntOrNull() ?: 0
                    if (column < 1) return toastex("请设置每行显示列数！")
                    val entity = JsonEntity(itemType = ItemType.divider, tileType = type, showName = name, columnCount = column)
                    if (position == -1) entities.add(entity)
                    else entities.add(position, entity)
                    adapter.notifyDataSetChanged()
                }
                dialog.dismiss()
            }
        }).show()
    }

    private inner class RecyclerAdapter<T>(val items: MutableList<T>, val init: (View, Int, T, ViewHolder<T>) -> Unit) :
            RecyclerView.Adapter<ViewHolder<T>>(), SimpleItemTouchHelperCallback.ItemTouchHelperAdapter {
        private var onCreateClicked: (()->Unit)? = null
        fun setOnCreateClicked(onCreate: ()->Unit): RecyclerAdapter<T> {
            this.onCreateClicked = onCreate
            return this
        }
        override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
            items.let {
                val panel = it.get(fromPosition)
                it.removeAt(fromPosition)
                it.add(toPosition, panel)
                notifyItemMoved(fromPosition, toPosition)
            }
            return false
        }
        override fun onItemDismiss(position: Int) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<T> {
            val view = parent.context.layoutInflater.inflate(viewType, parent, false)
            return ViewHolder(view, init)
        }
        override fun onBindViewHolder(holder: ViewHolder<T>, position: Int) {
            if (position < items.size) holder.bindForecast(position, items[position])
            else holder.itemView.onClick { onCreateClicked?.invoke() }
        }
        override fun getItemViewType(position: Int): Int {
            if (position < items.size) return R.layout.listitem_panel_edit_item
            else return R.layout.listitem_panel_add_item
        }
        override fun getItemCount() = items.size + 1
    }
    private class ViewHolder<T>(view: View, val init: (View, Int, T, ViewHolder<T>) -> Unit) : RecyclerView.ViewHolder(view) {
        fun bindForecast(index: Int, item: T) {
            with(item) {
                try { init(itemView, index, item, this@ViewHolder) } catch (ex: Exception) { ex.printStackTrace() }
            }
        }
    }
}

