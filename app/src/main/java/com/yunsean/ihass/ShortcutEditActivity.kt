package com.yunsean.ihass

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.enums.ItemType
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.model.Panel
import cn.com.thinkwatch.ihass2.ui.EntityListActivity
import cn.com.thinkwatch.ihass2.ui.MdiListActivity
import cn.com.thinkwatch.ihass2.utils.SimpleItemTouchHelperCallback
import com.dylan.common.rx.RxBus2
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.dylan.uiparts.recyclerview.SwipeItemLayout
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.ihass.db.ShortcutChanged
import com.yunsean.ihass.db.db
import kotlinx.android.synthetic.main.activity_shortcat_edit.*
import kotlinx.android.synthetic.main.dialog_shortcut_edit.view.*
import kotlinx.android.synthetic.main.listitem_shortcut_edit_item.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.layoutInflater
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.sdk25.coroutines.onTouch

class ShortcutEditActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shortcat_edit)
        setTitle("快捷按钮", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        ui()
        data()
    }
    override fun doRight() {
        entities.forEachIndexed { index, entity -> entity.displayOrder = index }
        db.saveShortcut(entities)
        RxBus2.getDefault().post(ShortcutChanged())
        finish()
    }

    private var entities = mutableListOf<JsonEntity>()
    private lateinit var adapter: RecyclerAdapter<JsonEntity>
    private lateinit var touchHelper: ItemTouchHelper
    private fun ui() {
        this.adapter = RecyclerAdapter(entities) {
            view, _, item, holder ->
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
                    name = ""
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
            view.order.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) touchHelper.startDrag(holder)
                true
            }
            view.insert.onClick {
                doAdd(item)
            }
            view.delete.onClick {
                entities.remove(item)
                adapter.notifyDataSetChanged()
            }
            view.content.onClick {
                if (item.itemType == ItemType.entity) {
                    createDialog(R.layout.dialog_shortcut_edit, object: OnSettingDialogListener {
                        override fun onSettingDialog(dialog: Dialog, contentView: View) {
                            contentView.entityName.setText(item.showName ?: item.friendlyName)
                            MDIFont.get().setIcon(contentView.entityIcon, if (item.showIcon.isNullOrBlank()) item.mdiIcon else item.showIcon)
                            contentView.entityIcon.tag = item.showIcon
                            contentView.iconPanel.onClick {
                                hotEntity = item
                                hotMdiView = contentView.entityIcon
                                activity(MdiListActivity::class.java, 106)
                            }
                        }
                    }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                        override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                            dialog.dismiss()
                            if (clickedView.id == R.id.ok) {
                                item.showName = contentView.entityName.text()
                                item.showIcon = contentView.entityIcon.tag as String?
                                adapter.notifyDataSetChanged()
                            }
                        }
                    }).show()
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
        db.async {
            db.readShortcut()
        }.nextOnMain {
            entities.clear()
            entities.addAll(it)
            adapter.notifyDataSetChanged()
        }.error {
            it.toastex()
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
        createDialog(R.layout.dialog_shortcut_add, null,
                intArrayOf(R.id.choiceBlank, R.id.choiceItem, R.id.choiceCancel),
                object: OnDialogItemClickedListener {
                    override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                        dialog.dismiss()
                        when (clickedView.id) {
                            R.id.choiceBlank-> {
                                if (position == -1) entities.add(JsonEntity(itemType = ItemType.blank))
                                else entities.add(position, JsonEntity(itemType = ItemType.blank))
                                adapter.notifyDataSetChanged()
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
            if (position < items.size) return R.layout.listitem_shortcut_edit_item
            else return R.layout.listitem_shortcut_add_item
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

