package cn.com.thinkwatch.ihass2.ui

import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import android.view.ViewGroup
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.PanelChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.Panel
import cn.com.thinkwatch.ihass2.utils.SimpleItemTouchHelperCallback
import com.dylan.common.rx.RxBus2
import com.dylan.uiparts.activity.ActivityResult
import com.yunsean.dynkotlins.extensions.nextOnMain
import com.yunsean.dynkotlins.extensions.start
import com.yunsean.dynkotlins.extensions.toastex
import kotlinx.android.synthetic.main.activity_hass_panel_list.*
import kotlinx.android.synthetic.main.listitem_panel_list_item.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.layoutInflater
import org.jetbrains.anko.sdk25.coroutines.onClick


class PanelListActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_panel_list)
        setTitle("面板列表", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        ui()
        data()
        disposable = RxBus2.getDefault().register(PanelChanged::class.java, {
            data()
        }, disposable)
    }
    override fun doRight() {
        val panels = adapter.items
        panels.forEachIndexed { index, panel -> panel.order = index }
        db.savePanels(panels)
        RxBus2.getDefault().post(PanelChanged(0L))
        finish()
    }

    private var isEdited = false
    private var isEditing = false
    private lateinit var adapter: RecyclerAdapter<Panel>
    private lateinit var touchHelper: ItemTouchHelper
    private fun ui() {
        this.adapter = RecyclerAdapter(mutableListOf<Panel>()) {
            view, index, item, holder ->
            view.name.text = item.name
            view.icon.visibility = View.GONE
            view.deleteIt.visibility = if (isEditing) View.VISIBLE else View.GONE
            view.onClick {
                if (isEditing) {
                    adapter.items?.remove(item)
                    adapter.notifyDataSetChanged()
                    isEdited = true
                } else {
                    Intent(act, PanelEditActivity::class.java)
                            .putExtra("panelId", item.id)
                            .start(act, 100)
                }
            }
        }.setOnCreateClicked {
            Intent(act, PanelEditActivity::class.java)
                    .start(act, 100)
        }
        this.recyclerView.adapter = adapter
        this.recyclerView.layoutManager = GridLayoutManager(this, 4)
        val callback = SimpleItemTouchHelperCallback(this.adapter)
        this.touchHelper = ItemTouchHelper(callback)
        this.touchHelper.attachToRecyclerView(this.recyclerView)
        this.edit.onClick {
            isEditing = !isEditing
            adapter.notifyDataSetChanged()
            edit.text = if (isEditing) "完成" else "编辑"
        }
    }
    @ActivityResult(requestCode = 100)
    private fun afterEditPanel() {
        data()
    }
    private fun data() {
        db.async {
            db.listPanel()
        }
                .nextOnMain {
                    adapter.items.clear()
                    adapter.items.addAll(it.toMutableList())
                    adapter.notifyDataSetChanged()
                }
                .error {
                    it.printStackTrace()
                    toastex(it.message ?: "未知错误")
                    finish()
                }
    }

    private inner class RecyclerAdapter<T>(val items: MutableList<T>, val init: (View, Int, T, ViewHolder<T>) -> Unit) :
            RecyclerView.Adapter<ViewHolder<T>>(), SimpleItemTouchHelperCallback.ItemTouchHelperAdapter {
        private var onCreateClicked: (()->Unit)? = null
        fun setOnCreateClicked(onCreate: ()->Unit): RecyclerAdapter<T> {
            this.onCreateClicked = onCreate
            return this
        }
        override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
            val panel = items.get(fromPosition)
            items.removeAt(fromPosition)
            items.add(toPosition, panel)
            isEdited = true
            notifyItemMoved(fromPosition, toPosition)
            return false
        }
        override fun onItemDismiss(position: Int) {
            items.removeAt(position)
            isEdited = true
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
            if (position < items.size) return R.layout.listitem_panel_list_item
            else return R.layout.listitem_panel_list_add
        }
        override fun getItemCount() = items.size + if (isEditing) 0 else 1
    }
    private class ViewHolder<T>(view: View, val init: (View, Int, T, ViewHolder<T>) -> Unit) : RecyclerView.ViewHolder(view) {
        fun bindForecast(index: Int, item: T) {
            with(item) {
                try { init(itemView, index, item, this@ViewHolder) } catch (ex: Exception) { ex.printStackTrace() }
            }
        }
    }
}

