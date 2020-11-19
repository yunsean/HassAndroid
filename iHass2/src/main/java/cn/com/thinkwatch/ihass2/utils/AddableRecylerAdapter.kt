package cn.com.thinkwatch.ihass2.utils

import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.ui.AutomationEditActivity
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import org.jetbrains.anko.layoutInflater
import org.jetbrains.anko.sdk25.coroutines.onClick

class AddableRecylerAdapter<T>(val layoutResourceId: Int,
                               var items: MutableList<T>? = null,
                               val init: (View, Int, T, ViewHolder<T>) -> Unit) :
        RecyclerView.Adapter<AddableRecylerAdapter.ViewHolder<T>>(),
        SimpleItemTouchHelperCallback.ItemTouchHelperAdapter {

    private var createLayoutResId: Int = 0
    private var onCreateClicked: (()->Unit)? = null

    fun setOnCreateClicked(createLayoutResId: Int, onCreate: ()->Unit): AddableRecylerAdapter<T> {
        this.createLayoutResId = createLayoutResId
        this.onCreateClicked = onCreate
        return this
    }
    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        items?.let {
            val panel = it.get(fromPosition)
            it.removeAt(fromPosition)
            it.add(toPosition, panel)
            notifyItemMoved(fromPosition, toPosition)
        }
        return false
    }
    override fun onItemDismiss(position: Int) {
        items?.removeAt(position)
        notifyItemRemoved(position)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<T> {
        val view = parent.context.layoutInflater.inflate(viewType, parent, false)
        return ViewHolder(view, init)
    }
    override fun onBindViewHolder(holder: ViewHolder<T>, position: Int) {
        if (position < (items?.size ?: 0)) holder.bindForecast(position, items?.get(position))
        else holder.itemView.onClick { onCreateClicked?.invoke() }
    }
    override fun getItemViewType(position: Int): Int {
        if (position < (items?.size ?: 0)) return layoutResourceId
        else return createLayoutResId
    }
    override fun getItemCount() = (items?.size ?: 0) + (if (createLayoutResId == 0) 0 else 1)

    class ViewHolder<T>(view: View, val init: (View, Int, T, ViewHolder<T>) -> Unit) : RecyclerView.ViewHolder(view) {
        fun bindForecast(index: Int, item: T?) {
            item?.apply {
                try { init(itemView, index, item, this@ViewHolder) } catch (ex: Exception) { ex.printStackTrace() }
            }
        }
    }
}