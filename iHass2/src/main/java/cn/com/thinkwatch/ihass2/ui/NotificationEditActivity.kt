package cn.com.thinkwatch.ihass2.ui

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
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.NotificationChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.utils.SimpleItemTouchHelperCallback
import com.dylan.common.rx.RxBus2
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.dylan.uiparts.recyclerview.SwipeItemLayout
import com.yunsean.dynkotlins.extensions.*
import kotlinx.android.synthetic.main.activity_notification_edit.*
import kotlinx.android.synthetic.main.dialog_notification_edit.view.*
import kotlinx.android.synthetic.main.listitem_notification_edit_item.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.layoutInflater
import org.jetbrains.anko.sdk25.coroutines.onClick

class NotificationEditActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_edit)
        setTitle("通知栏按钮", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        ui()
        data()
    }
    override fun doRight() {
        entities.forEachIndexed { index, entity -> entity.displayOrder = index }
        db.saveNotification(entities)
        RxBus2.getDefault().post(NotificationChanged())
        finish()
    }

    private var entities = mutableListOf<JsonEntity>()
    private lateinit var adapter: RecyclerAdapter<JsonEntity>
    private lateinit var touchHelper: ItemTouchHelper
    private fun ui() {
        this.adapter = RecyclerAdapter(entities) {
            view, _, item, holder ->
            view.backgroundColor = 0xffffffff.toInt()
            MDIFont.get().setIcon(view.icon, if (item.showIcon.isNullOrBlank()) item.mdiIcon else item.showIcon)
            view.name.text = if (item.showName.isNullOrBlank()) item.friendlyName else item.showName ?: ""
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
                createDialog(R.layout.dialog_notification_edit, object: OnSettingDialogListener {
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
    private fun data() {
        db.async {
            db.readNotification()
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
        Intent(act, EntityListActivity::class.java)
                .putExtra("position", position)
                .start(act, 105)
    }
    @ActivityResult(requestCode = 105)
    private fun afterAddEntity(data: Intent?) {
        val entityIds = data?.getStringArrayExtra("entityIds")
        var position = data?.getIntExtra("position", -1) ?: -1
        if (entityIds == null || entityIds.size < 1) return
        for (it in entityIds) {
            if (entities.size >= 10) {
                showError("最多只能添加8个元素，多余部分已被移除！")
                break
            }
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
            if (position < items.size) return R.layout.listitem_notification_edit_item
            else return R.layout.listitem_notification_add_item
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

