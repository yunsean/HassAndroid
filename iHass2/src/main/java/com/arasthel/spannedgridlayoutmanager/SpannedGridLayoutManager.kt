/*
 * Copyright © 2017 Jorge Martín Espinosa
 */

package com.arasthel.spannedgridlayoutmanager

import android.graphics.PointF
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.support.v7.widget.LinearSmoothScroller
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.util.SparseArray
import android.view.View

open class SpannedGridLayoutManager(val orientation: Orientation,
                                    var spanCount: Int) : RecyclerView.LayoutManager() {

    enum class Orientation {
        VERTICAL, HORIZONTAL
    }
    enum class Direction {
        START, END
    }

    protected var scroll = 0
    protected lateinit var rectsHelper: RectsHelper

    open val firstVisiblePosition: Int get() {
        if (childCount == 0) { return 0 }
        return getPosition(getChildAt(0)!!)
    }
    open val lastVisiblePosition: Int get() {
        if (childCount == 0) { return 0 }
        return getPosition(getChildAt(childCount-1)!!)
    }

    protected var layoutStart = 0
    protected var layoutEnd = 0
    val size: Int
        get() = if (orientation == Orientation.VERTICAL) height else width
    protected val childFrames = mutableMapOf<Int, Rect>()
    protected var pendingScrollToPosition: Int? = null
    var itemOrderIsStable = false
    var spanSizeLookup: SpanSizeLookup? = null
        set(newValue) {
            field = newValue
            requestLayout()
        }

    open class SpanSizeLookup(var lookupFunction: ((Int) -> SpanSize)? = null) {

        private var cache = SparseArray<SpanSize>()
        var usesCache = false
        open fun getSpanSize(position: Int): SpanSize {
            if (usesCache) {
                val cachedValue = cache[position]
                if (cachedValue != null) return cachedValue
                val value = getSpanSizeFromFunction(position)
                cache.put(position, value)
                return value
            } else {
                return getSpanSizeFromFunction(position)
            }
        }
        
        private fun getSpanSizeFromFunction(position: Int): SpanSize {
            return lookupFunction?.invoke(position) ?: getDefaultSpanSize()
        }
        protected open fun getDefaultSpanSize(): SpanSize {
            return SpanSize(1, 1)
        }
        fun invalidateCache() {
            cache.clear()
        }
    }

    init {
        if (spanCount < 1) {
            throw InvalidMaxSpansException(spanCount)
        }
    }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
    }
    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        rectsHelper = RectsHelper(this, orientation)
        layoutStart = getPaddingStartForOrientation()
        layoutEnd = if (scroll != 0) {
            val currentRow = (scroll - layoutStart) / rectsHelper.itemSize
            currentRow * rectsHelper.itemSize
        } else {
            getPaddingEndForOrientation()
        }

        childFrames.clear()
        detachAndScrapAttachedViews(recycler)
        val start = System.currentTimeMillis()
        for (i in 0 until state.itemCount) {
            val spanSize = spanSizeLookup?.getSpanSize(i) ?: SpanSize(1, 1)
            val childRect = rectsHelper.findRect(i, spanSize)
            rectsHelper.pushRect(i, childRect)
        }
        if (DEBUG) {
            val elapsed = System.currentTimeMillis() - start
            debugLog("Elapsed time: $elapsed ms")
        }
        val pendingScrollToPosition = pendingScrollToPosition
        if (itemCount != 0 && pendingScrollToPosition != null && pendingScrollToPosition >= spanCount) {
            val currentRow = rectsHelper.rows.filter { (_, value) -> value.contains(pendingScrollToPosition) }.keys.firstOrNull()
            if (currentRow != null) {
                scroll = getPaddingStartForOrientation() + (currentRow * rectsHelper.itemSize)
            }
            this.pendingScrollToPosition = null
        }
        fillGap(Direction.END, recycler, state)
        recycleChildrenOutOfBounds(Direction.END, recycler)
        val overScroll = scroll + size - layoutEnd - getPaddingEndForOrientation()
        val isLastItemInScreen = (0 until childCount).map { getPosition(getChildAt(it)!!) }.contains(itemCount - 1)
        val allItemsInScreen = itemCount == 0 || (firstVisiblePosition == 0 && isLastItemInScreen)
        if (!allItemsInScreen && overScroll > 0) {
            scrollBy(overScroll, state)
            if (overScroll > 0) fillBefore(recycler)
            else fillAfter(recycler)
        }
    }

    protected open fun measureChild(position: Int, view: View) {
        val freeRectsHelper = this.rectsHelper
        val itemWidth = freeRectsHelper.itemSize
        val itemHeight = freeRectsHelper.itemSize
        val spanSize = spanSizeLookup?.getSpanSize(position) ?: SpanSize(1, 1)
        val usedSpan = if (orientation == Orientation.HORIZONTAL) spanSize.height else spanSize.width
        if (usedSpan > this.spanCount || usedSpan < 1) {
            throw InvalidSpanSizeException(errorSize = usedSpan, maxSpanSize = spanCount)
        }

        val rect = freeRectsHelper.findRect(position, spanSize)
        val left = rect.left * itemWidth
        val right = rect.right * itemWidth
        val top = rect.top * itemHeight
        val bottom = rect.bottom * itemHeight

        val insetsRect = Rect()
        calculateItemDecorationsForChild(view, insetsRect)

        val width = right - left - insetsRect.left - insetsRect.right
        val height = bottom - top - insetsRect.top - insetsRect.bottom
        val layoutParams = view.layoutParams
        layoutParams.width = width
        layoutParams.height = height
        measureChildWithMargins(view, width, height)
        childFrames[position] = Rect(left, top, right, bottom)
    }

    protected open fun layoutChild(position: Int, view: View) {
        val frame = childFrames[position]
        if (frame != null) {
            val scroll = this.scroll
            val startPadding = getPaddingStartForOrientation()
            if (orientation == Orientation.VERTICAL) {
                layoutDecorated(view, frame.left + paddingLeft, frame.top - scroll + startPadding, frame.right + paddingLeft, frame.bottom - scroll + startPadding)
            } else {
                layoutDecorated(view, frame.left - scroll + startPadding, frame.top + paddingTop, frame.right - scroll + startPadding, frame.bottom + paddingTop)
            }
        }
        updateEdgesWithNewChild(view)
    }

    protected open fun makeAndAddView(position: Int, direction: Direction, recycler: RecyclerView.Recycler): View {
        val view = makeView(position, direction, recycler)
        if (direction == Direction.END) {
            addView(view)
        } else {
            addView(view, 0)
        }
        return view
    }
    protected open fun makeView(position: Int, direction: Direction, recycler: RecyclerView.Recycler): View {
        val view = recycler.getViewForPosition(position)
        measureChild(position, view)
        layoutChild(position, view)
        return view
    }

    protected open fun updateEdgesWithNewChild(view: View) {
        val childStart = getChildStart(view) + scroll + getPaddingStartForOrientation()
        if (childStart < layoutStart) {
            layoutStart = childStart
        }
        val newLayoutEnd = childStart + rectsHelper.itemSize
        if (newLayoutEnd > layoutEnd) {
            layoutEnd = newLayoutEnd
        }
    }

    protected open fun recycleChildrenOutOfBounds(direction: Direction, recycler: RecyclerView.Recycler) {
        if (direction == Direction.END) {
            recycleChildrenFromStart(direction, recycler)
        } else {
            recycleChildrenFromEnd(direction, recycler)
        }
    }

    protected open fun recycleChildrenFromStart(direction: Direction, recycler: RecyclerView.Recycler) {
        val childCount = childCount
        val start = getPaddingStartForOrientation()
        val toDetach = mutableListOf<View>()
        for (i in 0 until childCount) {
            val child = getChildAt(i)!!
            val childEnd = getChildEnd(child)
            if (childEnd < start) {
                toDetach.add(child)
            }
        }
        for (child in toDetach) {
            removeAndRecycleView(child, recycler)
            updateEdgesWithRemovedChild(child, direction)
        }
    }

    protected open fun recycleChildrenFromEnd(direction: Direction, recycler: RecyclerView.Recycler) {
        val childCount = childCount
        val end = size + getPaddingEndForOrientation()
        val toDetach = mutableListOf<View>()
        for (i in (0 until childCount).reversed()) {
            val child = getChildAt(i)!!
            val childStart = getChildStart(child)
            if (childStart > end) {
                toDetach.add(child)
            }
        }
        for (child in toDetach) {
            removeAndRecycleView(child, recycler)
            updateEdgesWithRemovedChild(child, direction)
        }
    }
    protected open fun updateEdgesWithRemovedChild(view: View, direction: Direction) {
        val childStart = getChildStart(view) + scroll
        val childEnd = getChildEnd(view) + scroll
        if (direction == Direction.END) {
            layoutStart = getPaddingStartForOrientation() + childEnd
        } else if (direction == Direction.START) {
            layoutEnd = getPaddingStartForOrientation() + childStart
        }
    }
    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int = computeScrollOffset()
    override fun computeHorizontalScrollOffset(state: RecyclerView.State): Int = computeScrollOffset()
    private fun computeScrollOffset(): Int = if (childCount == 0) 0 else firstVisiblePosition
    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int = childCount
    override fun computeHorizontalScrollExtent(state: RecyclerView.State): Int = childCount
    override fun computeVerticalScrollRange(state: RecyclerView.State): Int = state.itemCount
    override fun computeHorizontalScrollRange(state: RecyclerView.State): Int = state.itemCount
    override fun canScrollVertically(): Boolean = orientation == Orientation.VERTICAL
    override fun canScrollHorizontally(): Boolean = orientation == Orientation.HORIZONTAL
    override fun scrollHorizontallyBy(dx: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int = scrollBy(dx, recycler, state)
    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int = scrollBy(dy, recycler, state)
    protected open fun scrollBy(delta: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        if (delta == 0) return 0
        val canScrollBackwards = (firstVisiblePosition) >= 0 &&
                0 < scroll &&
                delta < 0
        val canScrollForward = (firstVisiblePosition + childCount) <= state.itemCount &&
                (scroll + size) < (layoutEnd + rectsHelper.itemSize + getPaddingEndForOrientation())
                delta > 0
        if (!(canScrollBackwards || canScrollForward)) return 0
        val correctedDistance = scrollBy(-delta, state)
        val direction = if (delta > 0) Direction.END else Direction.START
        recycleChildrenOutOfBounds(direction, recycler)
        fillGap(direction, recycler, state)
        return -correctedDistance
    }
    protected open fun scrollBy(distance: Int, state: RecyclerView.State): Int {
        val paddingEndLayout = getPaddingEndForOrientation()
        val start = 0
        val end = layoutEnd + rectsHelper.itemSize + paddingEndLayout
        scroll -= distance
        var correctedDistance = distance
        if (scroll < start) {
            correctedDistance += scroll
            scroll = start
        }
        if (scroll + size > end && (firstVisiblePosition + childCount + spanCount) >= state.itemCount) {
            correctedDistance -= (end - scroll - size)
            scroll = end - size
        }
        if (orientation == Orientation.VERTICAL) {
            offsetChildrenVertical(correctedDistance)
        } else{
            offsetChildrenHorizontal(correctedDistance)
        }
        return correctedDistance
    }
    override fun scrollToPosition(position: Int) {
        pendingScrollToPosition = position
        requestLayout()
    }
    override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
        val smoothScroller = object: LinearSmoothScroller(recyclerView.context) {
            override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
                if (childCount == 0) {
                    return null
                }
                val direction = if (targetPosition < firstVisiblePosition) -1 else 1
                return PointF(0f, direction.toFloat())
            }
            override fun getVerticalSnapPreference(): Int {
                return LinearSmoothScroller.SNAP_TO_START
            }
        }

        smoothScroller.targetPosition = position
        startSmoothScroll(smoothScroller)
    }
    protected open fun fillGap(direction: Direction, recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (direction == Direction.END) {
            fillAfter(recycler)
        } else {
            fillBefore(recycler)
        }
    }
    protected open fun fillBefore(recycler: RecyclerView.Recycler) {
        val currentRow = (scroll - getPaddingStartForOrientation()) / rectsHelper.itemSize
        val lastRow = (scroll + size - getPaddingStartForOrientation()) / rectsHelper.itemSize
        for (row in (currentRow until lastRow).reversed()) {
            val positionsForRow = rectsHelper.findPositionsForRow(row).reversed()
            for (position in positionsForRow) {
                if (findViewByPosition(position) != null) continue
                makeAndAddView(position, Direction.START, recycler)
            }
        }
    }
    protected open fun fillAfter(recycler: RecyclerView.Recycler) {
        val visibleEnd = scroll + size
        val lastAddedRow = layoutEnd / rectsHelper.itemSize
        val lastVisibleRow =  visibleEnd / rectsHelper.itemSize
        for (rowIndex in lastAddedRow .. lastVisibleRow) {
            val row = rectsHelper.rows[rowIndex] ?: continue
            for (itemIndex in row) {
                if (findViewByPosition(itemIndex) != null) continue
                makeAndAddView(itemIndex, Direction.END, recycler)
            }
        }
    }
    override fun getDecoratedMeasuredWidth(child: View): Int {
        val position = getPosition(child)
        return childFrames[position]!!.width()
    }
    override fun getDecoratedMeasuredHeight(child: View): Int {
        val position = getPosition(child)
        return childFrames[position]!!.height()
    }
    override fun getDecoratedTop(child: View): Int {
        val position = getPosition(child)
        val decoration = getTopDecorationHeight(child)
        var top = childFrames[position]!!.top + decoration
        if (orientation == Orientation.VERTICAL) {
            top -= scroll
        }
        return top
    }
    override fun getDecoratedRight(child: View): Int {
        val position = getPosition(child)
        val decoration = getLeftDecorationWidth(child) + getRightDecorationWidth(child)
        var right = childFrames[position]!!.right + decoration
        if (orientation == Orientation.HORIZONTAL) {
            right -= scroll - getPaddingStartForOrientation()
        }
        return right
    }

    override fun getDecoratedLeft(child: View): Int {
        val position = getPosition(child)
        val decoration = getLeftDecorationWidth(child)
        var left = childFrames[position]!!.left + decoration
        if (orientation == Orientation.HORIZONTAL) {
            left -= scroll
        }
        return left
    }
    override fun getDecoratedBottom(child: View): Int {
        val position = getPosition(child)
        val decoration = getTopDecorationHeight(child) + getBottomDecorationHeight(child)
        var bottom = childFrames[position]!!.bottom + decoration
        if (orientation == Orientation.VERTICAL) {
            bottom -= scroll - getPaddingStartForOrientation()
        }
        return bottom
    }
    protected open fun getPaddingStartForOrientation(): Int {
        return if (orientation == Orientation.VERTICAL) {
            paddingTop
        } else {
            paddingLeft
        }
    }
    protected open fun getPaddingEndForOrientation(): Int {
        return if (orientation == Orientation.VERTICAL) {
            paddingBottom
        } else {
            paddingRight
        }
    }
    protected open fun getChildStart(child: View): Int {
        return if (orientation == Orientation.VERTICAL) {
            getDecoratedTop(child)
        } else {
            getDecoratedLeft(child)
        }
    }
    protected open fun getChildEnd(child: View): Int {
        return if (orientation == Orientation.VERTICAL) {
            getDecoratedBottom(child)
        } else {
            getDecoratedRight(child)
        }
    }
    override fun onSaveInstanceState(): Parcelable? {
        return if (itemOrderIsStable && childCount > 0) {
            debugLog("Saving first visible position: $firstVisiblePosition")
            SavedState(firstVisiblePosition)
        } else {
            null
        }
    }
    override fun onRestoreInstanceState(state: Parcelable) {
        debugLog("Restoring state")
        val savedState = state as? SavedState
        if (savedState != null) {
            val firstVisibleItem = savedState.firstVisibleItem
            scrollToPosition(firstVisibleItem)
        }
    }
    companion object {
        const val TAG = "SpannedGridLayoutMan"
        const val DEBUG = false
        fun debugLog(message: String) {
            if (DEBUG) Log.d(TAG, message)
        }
    }
    class SavedState(val firstVisibleItem: Int): Parcelable {
        companion object {
            @JvmField val CREATOR = object: Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState {
                    return SavedState(source.readInt())
                }
                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(firstVisibleItem)
        }
        override fun describeContents(): Int {
            return 0
        }
    }

}

open class RectsHelper(val layoutManager: SpannedGridLayoutManager,
                       val orientation: SpannedGridLayoutManager.Orientation) {
    private val rectComparator = Comparator<Rect> { rect1, rect2 ->
        when (orientation) {
            SpannedGridLayoutManager.Orientation.VERTICAL -> {
                if (rect1.top == rect2.top) {
                    if (rect1.left < rect2.left) { -1 } else { 1 }
                } else {
                    if (rect1.top < rect2.top) { -1 } else { 1 }
                }
            }
            SpannedGridLayoutManager.Orientation.HORIZONTAL -> {
                if (rect1.left == rect2.left) {
                    if (rect1.top < rect2.top) { -1 } else { 1 }
                } else {
                    if (rect1.left < rect2.left) { -1 } else { 1 }
                }
            }
        }

    }

    val rows = mutableMapOf<Int, Set<Int>>()
    private val rectsCache = mutableMapOf<Int, Rect>()
    private val freeRects = mutableListOf<Rect>()
    val size: Int get() {
        return if (orientation == SpannedGridLayoutManager.Orientation.VERTICAL) {
            layoutManager.width - layoutManager.paddingLeft - layoutManager.paddingRight
        } else {
            layoutManager.height - layoutManager.paddingTop - layoutManager.paddingBottom
        }
    }
    val itemSize: Int get() = size / layoutManager.spanCount
    val start: Int get() {
        return if (orientation == SpannedGridLayoutManager.Orientation.VERTICAL) {
            freeRects[0].top * itemSize
        } else {
            freeRects[0].left * itemSize
        }
    }
    val end: Int get() {
        return if (orientation == SpannedGridLayoutManager.Orientation.VERTICAL) {
            (freeRects.last().top + 1) * itemSize
        } else {
            (freeRects.last().left + 1) * itemSize
        }
    }
    init {
        val initialFreeRect = if (orientation == SpannedGridLayoutManager.Orientation.VERTICAL) {
            Rect(0, 0, layoutManager.spanCount, Int.MAX_VALUE)
        } else {
            Rect(0, 0, Int.MAX_VALUE, layoutManager.spanCount)
        }
        freeRects.add(initialFreeRect)
    }
    fun findRect(position: Int, spanSize: SpanSize): Rect {
        return rectsCache[position] ?: findRectForSpanSize(spanSize)
    }
    protected open fun findRectForSpanSize(spanSize: SpanSize): Rect {
        val lane = freeRects.first {
            val itemRect = Rect(it.left, it.top, it.left+spanSize.width, it.top + spanSize.height)
            it.contains(itemRect)
        }
        return Rect(lane.left, lane.top, lane.left+spanSize.width, lane.top + spanSize.height)
    }
    fun pushRect(position: Int, rect: Rect) {
        val start = if (orientation == SpannedGridLayoutManager.Orientation.VERTICAL) rect.top else rect.left
        val startRow = rows[start]?.toMutableSet() ?: mutableSetOf()
        startRow.add(position)
        rows[start] = startRow
        val end = if (orientation == SpannedGridLayoutManager.Orientation.VERTICAL) rect.bottom else rect.right
        val endRow = rows[end - 1]?.toMutableSet() ?: mutableSetOf()
        endRow.add(position)
        rows[end - 1] = endRow
        rectsCache[position] = rect
        subtract(rect)
    }
    fun findPositionsForRow(rowPosition: Int): Set<Int> {
        return rows[rowPosition] ?: emptySet()
    }
    protected open fun subtract(subtractedRect: Rect) {
        val interestingRects = freeRects.filter { it.isAdjacentTo(subtractedRect) || it.intersects(subtractedRect) }
        val possibleNewRects = mutableListOf<Rect>()
        val adjacentRects = mutableListOf<Rect>()

        for (free in interestingRects) {
            if (free.isAdjacentTo(subtractedRect) && !subtractedRect.contains(free)) {
                adjacentRects.add(free)
            } else {
                freeRects.remove(free)
                if (free.left < subtractedRect.left) {
                    possibleNewRects.add(Rect(free.left, free.top, subtractedRect.left, free.bottom))
                }
                if (free.right > subtractedRect.right) {
                    possibleNewRects.add(Rect(subtractedRect.right, free.top, free.right, free.bottom))
                }
                if (free.top < subtractedRect.top) {
                    possibleNewRects.add(Rect(free.left, free.top, free.right, subtractedRect.top))
                }
                if (free.bottom > subtractedRect.bottom) {
                    possibleNewRects.add(Rect(free.left, subtractedRect.bottom, free.right, free.bottom))
                }
            }
        }
        for (rect in possibleNewRects) {
            val isAdjacent = adjacentRects.firstOrNull { it != rect && it.contains(rect) } != null
            if (isAdjacent) continue
            val isContained = possibleNewRects.firstOrNull { it != rect && it.contains(rect) } != null
            if (isContained) continue
            freeRects.add(rect)
        }
        freeRects.sortWith(rectComparator)
    }
}

class SpanSize(val width: Int, val height: Int)