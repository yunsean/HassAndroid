package cn.com.thinkwatch.ihass2.ui

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.PanelChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.model.Panel
import cn.com.thinkwatch.ihass2.utils.SimpleItemTouchHelperCallback
import com.dylan.common.rx.RxBus2
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.activity_hass_panel_list.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import kotlinx.android.synthetic.main.listitem_panel_list_item.view.*
import kotlinx.android.synthetic.main.tile_square.*
import org.jetbrains.anko.act
import org.jetbrains.anko.find
import org.jetbrains.anko.image
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
            if (it.isAdd) {
                db.getPanel(it.panelId)?.let { adapter.items.add(it) }
                adapter.notifyDataSetChanged()
            } else {
                data()
            }
        }, disposable)
    }
    override fun doRight() {
        val panels = adapter.items
        panels.forEachIndexed { index, panel -> panel.order = index }
        db.savePanels(panels)
        RxBus2.getDefault().post(PanelChanged(0L, true))
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
            add()
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
    private fun add() {
        showDialog(R.layout.dialog_list_view, object : OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                val types = mutableListOf<HassApplication.Companion.FragmentItem>().apply {
                    add(HassApplication.Companion.FragmentItem("自定义", "mdi:view-list", ""))
                    addAll(app.stubbornTabs)
                }
                contentView.recyclerView.layoutManager = LinearLayoutManager(act)
                contentView.recyclerView.adapter = RecyclerAdapter(R.layout.listitem_add_panel, types) { view, index, item ->
                    view.find<TextView>(R.id.name).text = item.name
                    MDIFont.get().setIcon(view.find(R.id.icon), item.icon)
                    view.setOnClickListener {
                        if (item.clazz.isBlank()) {
                            Intent(act, PanelEditActivity::class.java)
                                .start(act, 100)
                        } else {
                            db.addPanel(Panel(name = item.name, icon = item.icon, stubbornClass = item.clazz))
                            data()
                        }
                        dialog.dismiss()
                    }
                }
                contentView.recyclerView.addItemDecoration(
                    RecyclerViewDivider()
                    .setColor(0xffeeeeee.toInt())
                    .setSize(1))
            }
        }, cancelable = true)
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

