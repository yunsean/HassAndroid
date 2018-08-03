/*
 * Copyright (C) 2015 Tomás Ruiz-López.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.truizlop.sectionedrecyclerview

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup

abstract class SectionedRecyclerViewAdapter<H : RecyclerView.ViewHolder, VH : RecyclerView.ViewHolder, F : RecyclerView.ViewHolder> : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var sectionForPosition: IntArray? = null
    private var positionWithinSection: IntArray? = null
    private var isHeader: BooleanArray? = null
    private var isFooter: BooleanArray? = null
    private var count = 0

    init {
        registerAdapterDataObserver(SectionDataObserver())
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView?) {
        super.onAttachedToRecyclerView(recyclerView)
        setupIndices()
    }
    abstract fun getSectionCount(): Int
    override fun getItemCount(): Int = count
    private fun setupIndices() {
        count = countItems()
        allocateAuxiliaryArrays(count)
        precomputeIndices()
    }
    private fun countItems(): Int {
        var count = 0
        val sections = getSectionCount()
        for (i in 0 until sections) {
            count += (if (hasHeaderInSection(i)) 1 else 0) + getItemCountForSection(i) + if (hasFooterInSection(i)) 1 else 0
        }
        return count
    }
    private fun precomputeIndices() {
        val sections = getSectionCount()
        var index = 0
        for (i in 0 until sections) {
            if (hasHeaderInSection(i)) {
                setPrecomputedItem(index, true, false, i, 0)
                index++
            }
            for (j in 0 until getItemCountForSection(i)) {
                setPrecomputedItem(index, false, false, i, j)
                index++
            }
            if (hasFooterInSection(i)) {
                setPrecomputedItem(index, false, true, i, 0)
                index++
            }
        }
    }
    private fun allocateAuxiliaryArrays(count: Int) {
        sectionForPosition = IntArray(count)
        positionWithinSection = IntArray(count)
        isHeader = BooleanArray(count)
        isFooter = BooleanArray(count)
    }
    private fun setPrecomputedItem(index: Int, isHeader: Boolean, isFooter: Boolean, section: Int, position: Int) {
        this.isHeader!![index] = isHeader
        this.isFooter!![index] = isFooter
        sectionForPosition!![index] = section
        positionWithinSection!![index] = position
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val viewHolder: RecyclerView.ViewHolder
        if (isSectionHeaderViewType(viewType)) viewHolder = onCreateSectionHeaderViewHolder(parent, viewType)
        else if (isSectionFooterViewType(viewType)) viewHolder = onCreateSectionFooterViewHolder(parent, viewType)
        else viewHolder = onCreateItemViewHolder(parent, viewType)
        return viewHolder
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val section = sectionForPosition!![position]
        val index = positionWithinSection!![position]
        if (isSectionHeaderPosition(position)) onBindSectionHeaderViewHolder(holder as H, section)
        else if (isSectionFooterPosition(position)) onBindSectionFooterViewHolder(holder as F, section)
        else onBindItemViewHolder(holder as VH, section, index)
    }
    override fun getItemViewType(position: Int): Int {
        if (sectionForPosition == null) setupIndices()
        val section = sectionForPosition!![position]
        val index = positionWithinSection!![position]
        return if (isSectionHeaderPosition(position)) getSectionHeaderViewType(section)
        else if (isSectionFooterPosition(position)) getSectionFooterViewType(section)
        else getSectionItemViewType(section, index)
    }
    fun isSectionHeaderPosition(position: Int): Boolean {
        if (isHeader == null) setupIndices()
        return isHeader!![position]
    }
    fun isSectionFooterPosition(position: Int): Boolean {
        if (isFooter == null) setupIndices()
        return isFooter!![position]
    }
    fun getColumnCountForItem(position: Int): Int {
        if (sectionForPosition == null) setupIndices()
        val section = sectionForPosition!![position]
        val index = positionWithinSection!![position]
        return getColumnCountForItem(section, index)
    }
    fun getColumnSpanForItem(position: Int): Int {
        if (sectionForPosition == null) setupIndices()
        val section = sectionForPosition!![position]
        val index = positionWithinSection!![position]
        return getColumnSpanForItem(section, index)
    }

    protected fun getSectionHeaderViewType(section: Int): Int = TYPE_SECTION_HEADER
    protected fun getSectionFooterViewType(section: Int): Int = TYPE_SECTION_FOOTER
    protected open fun getSectionItemViewType(section: Int, position: Int): Int = TYPE_ITEM
    protected fun isSectionHeaderViewType(viewType: Int): Boolean = viewType == TYPE_SECTION_HEADER
    protected fun isSectionFooterViewType(viewType: Int): Boolean = viewType == TYPE_SECTION_FOOTER
    protected open fun getColumnCountForItem(section: Int, position: Int): Int = 0
    protected open fun getColumnSpanForItem(section: Int, position: Int): Int = 1

    protected abstract fun getItemCountForSection(section: Int): Int
    protected abstract fun hasHeaderInSection(section: Int): Boolean
    protected abstract fun hasFooterInSection(section: Int): Boolean
    protected abstract fun onCreateSectionHeaderViewHolder(parent: ViewGroup, viewType: Int): H
    protected abstract fun onCreateSectionFooterViewHolder(parent: ViewGroup, viewType: Int): F
    protected abstract fun onCreateItemViewHolder(parent: ViewGroup, viewType: Int): VH
    protected abstract fun onBindSectionHeaderViewHolder(holder: H, section: Int)
    protected abstract fun onBindSectionFooterViewHolder(holder: F, section: Int)
    protected abstract fun onBindItemViewHolder(holder: VH, section: Int, position: Int)

    internal inner class SectionDataObserver : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = setupIndices()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = setupIndices()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = setupIndices()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = setupIndices()
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = setupIndices()
    }

    companion object {
        protected val TYPE_SECTION_HEADER = -1
        protected val TYPE_SECTION_FOOTER = -2
        protected val TYPE_ITEM = -3
    }
}
