package cn.com.thinkwatch.ihass2.adapter

import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import cn.com.thinkwatch.ihass2.utils.SimpleItemTouchHelperCallback
import org.jetbrains.anko.layoutInflater
import org.jetbrains.anko.sdk25.coroutines.onClick

class CreatableAdapter<T>(val layoutResId: Int, var items: MutableList<T>, val init: (View, Int, T, CreatableAdapter.ViewHolder<T>) -> Unit) :
        RecyclerView.Adapter<CreatableAdapter.ViewHolder<T>>(), SimpleItemTouchHelperCallback.ItemTouchHelperAdapter {
    private var onCreateClicked: (()->Unit)? = null
    private var createResId = 0
    fun setCreatable(createResId: Int, onCreateClicked: ()->Unit): CreatableAdapter<T> {
        this.createResId = createResId
        this.onCreateClicked = onCreateClicked
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
        if (position < items.size) return layoutResId
        else return createResId
    }
    override fun getItemCount() = items.size + if (createResId != 0) 1 else 0

    class ViewHolder<T>(view: View, val init: (View, Int, T, ViewHolder<T>) -> Unit) : RecyclerView.ViewHolder(view) {
        fun bindForecast(index: Int, item: T) {
            with(item) {
                try { init(itemView, index, item, this@ViewHolder) } catch (ex: Exception) { ex.printStackTrace() }
            }
        }
    }
}