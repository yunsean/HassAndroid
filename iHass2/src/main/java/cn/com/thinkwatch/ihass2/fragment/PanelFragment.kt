package cn.com.thinkwatch.ihass2.fragment

import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.adapter.PanelAdapter
import cn.com.thinkwatch.ihass2.adapter.PanelGroup
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.bean.*
import cn.com.thinkwatch.ihass2.bus.EntityChanged
import cn.com.thinkwatch.ihass2.bus.EntityUpdated
import cn.com.thinkwatch.ihass2.bus.PanelChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.enums.ItemType
import cn.com.thinkwatch.ihass2.enums.TileType
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.ui.PanelEditActivity
import com.dylan.common.rx.RxBus2
import com.truizlop.sectionedrecyclerview.SectionedSpanSizeLookup
import com.yunsean.dynkotlins.extensions.ktime
import com.yunsean.dynkotlins.extensions.start
import com.yunsean.dynkotlins.extensions.withNext
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.fragment_hass_panel.*
import kotlinx.android.synthetic.main.fragment_hass_panel.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx


class PanelFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.fragment_hass_panel
    private var panelId: Long = 0
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        panelId = if (arguments?.containsKey("panelId") ?: false) arguments?.getLong("panelId") ?: 0L else 0L

        ui()
        disposable = RxBus2.getDefault().register(EntityUpdated::class.java, {
            data()
        }, RxBus2.getDefault().register(PanelChanged::class.java, {
            if (it.panelId == panelId) data()
        }, RxBus2.getDefault().register(EntityChanged::class.java, { event->
            refreshTime.text = "状态更新于：${app.refreshAt.ktime()}"
            groups?.forEach {
                it.entities.forEach {
                    if (it.entity.entityId.equals(event.entity.entityId)) {
                        it.entity.state = event.entity.state
                        it.entity.attributes = event.entity.attributes
                        it.entity.lastChanged = event.entity.lastChanged
                        it.entity.lastUpdated = event.entity.lastUpdated
                        adapter.notifyDataSetChanged()
                        return@register
                    }
                }
            }
        }, disposable)))
        data()
    }

    private lateinit var adapter: PanelAdapter
    private fun ui() {
        this.adapter = PanelAdapter(ctx)
        val layoutManager = GridLayoutManager(ctx, 4)
        val lookup = SectionedSpanSizeLookup(adapter, layoutManager)
        layoutManager.spanSizeLookup = lookup
        this.recyclerView.layoutManager = layoutManager
        this.recyclerView.adapter = adapter
        this.showEdit.onClick {
            Intent(act, PanelEditActivity::class.java)
                    .putExtra("panelId", panelId)
                    .start(act)
        }
    }
    private fun data() {
        refreshTime.text = "状态更新于：${app.refreshAt.ktime()}"
        db.async {
            val entities = db.readPanel(panelId)
            val groups = mutableListOf<PanelGroup>()
            var latest: PanelGroup? = null
            entities.forEach {
                var group: PanelGroup? = null
                var entity: JsonEntity? = null
                if (it.itemType == ItemType.divider) {
                    group = PanelGroup(it)
                } else {
                    entity = it
                }
                if (group == null && latest == null) {
                    latest = PanelGroup()
                    groups.add(latest!!)
                }
                if (group != null) {
                    latest = group
                    groups.add(group)
                } else if (it.itemType == ItemType.blank) {
                    latest?.entities?.add(BlankBean())
                } else if (entity != null) {
                    if (entity.tileType == TileType.inherit) entity.tileType = latest?.group?.tileType ?: TileType.tile
                    if (entity.tileType == TileType.circle) {
                        latest?.entities?.add(CircleBean(entity))
                    } else if (entity.tileType == TileType.square) {
                        latest?.entities?.add(SquareBean(entity))
                    } else if (entity.tileType == TileType.list) {
                        latest?.entities?.add(DetailBean(entity))
                    } else if (entity.isSwitch) {
                        latest?.entities?.add(SwitchBean(entity))
                    } else if (entity.isCamera) {
                        latest?.entities?.add(CameraBean(entity))
                    } else if (entity.isAutomation) {
                        latest?.entities?.add(AutomationBean(entity))
                    } else if (entity.isDeviceTracker) {
                        latest?.entities?.add(TrackerBean(entity))
                    } else {
                        latest?.entities?.add(NormalBean(entity))
                    }
                }
            }
            groups
        }.withNext {
            show(it)
        }.error {
            show(null)
        }.subscribeOnMain {
            if (disposable == null) disposable = CompositeDisposable(it)
            else disposable?.add(it)
        }
    }
    private var groups: List<PanelGroup>? = null
    private fun show(beans: List<PanelGroup>?) {
        var columnSize = 4
        beans?.forEach { columnSize = minCommonMultiple(columnSize, it.group?.columnCount ?: 4) }
        val layoutManager = GridLayoutManager(ctx, columnSize)
        val lookup = SectionedSpanSizeLookup(adapter, layoutManager)
        layoutManager.spanSizeLookup = lookup
        this.recyclerView?.layoutManager = layoutManager
        groups = beans
        adapter.groups = groups
        fragment.emptyView?.visibility = if (adapter.groups?.size ?: 0 > 0) View.GONE else View.VISIBLE
    }

    fun maxCommonDivisor(m: Int, n: Int): Int {
        var m = m
        var n = n
        if (m < n) {
            val temp = m
            m = n
            n = temp
        }
        while (m % n != 0) {
            val temp = m % n
            m = n
            n = temp
        }
        return n
    }
    fun minCommonMultiple(m: Int, n: Int): Int {
        return m * n / maxCommonDivisor(m, n)
    }

}