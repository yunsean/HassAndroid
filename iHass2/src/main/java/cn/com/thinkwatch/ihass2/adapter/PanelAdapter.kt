package cn.com.thinkwatch.ihass2.adapter

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import cn.com.thinkwatch.ihass2.R
import com.truizlop.sectionedrecyclerview.SectionedRecyclerViewAdapter
import kotlinx.android.synthetic.main.headerview_hass_panel_group.view.*

class PanelAdapter(val context: Context,
                   groups: List<PanelGroup>? = null)
    : SectionedRecyclerViewAdapter<PanelAdapter.HeaderViewHolder, PanelAdapter.ViewHolder, PanelAdapter.HeaderViewHolder>() {

    override fun hasHeaderInSection(section: Int): Boolean = true
    override fun hasFooterInSection(section: Int): Boolean = false
    override fun onCreateSectionFooterViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.headerview_hass_panel_group, parent, false)
        return HeaderViewHolder(view)
    }
    override fun onCreateSectionHeaderViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.headerview_hass_panel_group, parent, false)
        return HeaderViewHolder(view)
    }
    override fun onBindSectionFooterViewHolder(holder: HeaderViewHolder, section: Int) {
    }
    override fun onBindSectionHeaderViewHolder(holder: HeaderViewHolder, section: Int) {
        holder.itemView?.headerName?.visibility = if (groups?.get(section)?.group?.showName.isNullOrEmpty()) View.GONE else View.VISIBLE
        holder.itemView?.headerName?.text = groups?.get(section)?.group?.showName ?: ""
    }
    override fun getColumnCountForItem(section: Int, position: Int): Int {
        return groups?.get(section)?.group?.columnCount ?: 4
    }
    override fun getColumnSpanForItem(section: Int, position: Int): Int {
        return groups?.get(section)?.entities?.get(position)?.entity?.columnCount ?: 1
    }

    override fun getSectionCount(): Int = groups?.size ?: 0
    override fun getItemCountForSection(section: Int): Int = groups?.get(section)?.entities?.size ?: 0
    override fun onCreateItemViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return ViewHolder(view)
    }
    override fun onBindItemViewHolder(holder: ViewHolder, section: Int, position: Int) {
        groups!![section].entities[position].bindToView(holder.itemView, context)
    }
    override fun getSectionItemViewType(section: Int, position: Int): Int {
        return groups!![section].entities[position].layoutResId()
    }

    var groups: List<PanelGroup>? = null
        set(value) { field = value;  notifyDataSetChanged(); }
    init { this.groups = groups }

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView)
    class HeaderViewHolder(itemView: View): RecyclerView.ViewHolder(itemView)
}