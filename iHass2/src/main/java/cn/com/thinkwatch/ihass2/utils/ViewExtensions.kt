package cn.com.thinkwatch.ihass2.utils

import android.util.SparseArray
import android.view.View


@Suppress("UNCHECKED_CAST")
fun <T : View> View.viewAt(viewId: Int): T {
    val viewHolder: SparseArray<View> = tag as? SparseArray<View> ?: SparseArray()
    tag = viewHolder
    var childView: View? = viewHolder.get(viewId)
    if (null == childView) {
        childView = findViewById(viewId)
        viewHolder.put(viewId, childView)
    }
    return childView as T
}