package cn.com.thinkwatch.ihass2.fragment

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import cn.com.thinkwatch.ihass2.ui.PanelListActivity
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.dylan.common.rx.RxBus2
import com.truizlop.sectionedrecyclerview.SectionedSpanSizeLookup
import com.yunsean.dynkotlins.extensions.start
import com.yunsean.dynkotlins.extensions.withNext
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.fragment_hass_panel.*
import kotlinx.android.synthetic.main.fragment_hass_panel.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.startActivity


class PanelFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.fragment_hass_panel
    private var panelId: Long = 0
    private var tileAlpha: Boolean = false
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        panelId = if (arguments?.containsKey("panelId") ?: false) arguments?.getLong("panelId") ?: 0L else 0L

        ui()
        disposable = RxBus2.getDefault().register(EntityUpdated::class.java, {
            data()
        }, RxBus2.getDefault().register(PanelChanged::class.java, {
            if (it.panelId == panelId) data()
        }, RxBus2.getDefault().register(EntityChanged::class.java, { event->
            groups?.forEach {
                it.entities.forEach {
                    if (it.entity.entityId.equals(event.entityId)) {
                        db.getEntity(event.entityId)?.apply {
                            it.entity.state = state
                            it.entity.attributes = attributes
                            it.entity.lastChanged = lastChanged
                            it.entity.lastUpdated = lastUpdated
                            adapter.notifyDataSetChanged()
                            return@register
                        }
                    }
                }
            }
        }, disposable)))
        data()
    }

    private lateinit var adapter: PanelAdapter
    private fun ui() {
        this.loading.visibility = View.VISIBLE
        this.editing.visibility = if (cfg.getBoolean(HassConfig.Ui_HomePanels, true)) View.VISIBLE else View.GONE
        this.editing.setOnClickListener { startActivity<PanelEditActivity>(Pair("panelId", panelId)) }
        this.editing.setOnLongClickListener { startActivity<PanelListActivity>(); true }
        this.adapter = PanelAdapter(ctx)
        val layoutManager = GridLayoutManager(ctx, 4)
        val lookup = SectionedSpanSizeLookup(adapter, layoutManager)
        layoutManager.spanSizeLookup = lookup
        this.recyclerView.setRecycledViewPool(app.panelViewPool)
        this.recyclerView.layoutManager = layoutManager
        this.recyclerView.adapter = adapter
        this.showEdit.onClick {
            Intent(act, PanelEditActivity::class.java)
                    .putExtra("panelId", panelId)
                    .start(act)
        }
    }
    private fun data() {
        db.getPanel(panelId)?.let {
            titleItem.text = it.name
            tileAlpha = it.tileAlpha ?: 1f < .9f
            backImage.visibility = View.GONE
            it.backImage?.let {
                try {
                    Glide.with(ctx).asBitmap().load(it).into(object : SimpleTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap?, transition: Transition<in Bitmap>?) {
                            if (resource == null) return
                            backImage?.visibility = View.VISIBLE
                            backImage?.setImageBitmap(resource)
                        }
                    })
                } catch (ex: Exception) {
                }
            }
        }
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
                        latest?.entities?.add(CircleBean(entity, tileAlpha))
                    } else if (entity.tileType == TileType.square) {
                        latest?.entities?.add(SquareBean(entity, tileAlpha))
                    } else if (entity.tileType == TileType.list) {
                        latest?.entities?.add(DetailBean(entity, tileAlpha))
                    } else if (entity.tileType == TileType.list2) {
                        latest?.entities?.add(Detail2Bean(entity, tileAlpha))
                    } else if (entity.isSwitch || entity.isInputBoolean) {
                        latest?.entities?.add(SwitchBean(entity, tileAlpha))
                    } else if (entity.isCamera) {
                        latest?.entities?.add(CameraBean(entity, tileAlpha))
                    } else if (entity.isAnySensors) {
                        latest?.entities?.add(SensorBean(entity, tileAlpha))
                    } else if (entity.isDeviceTracker) {
                        latest?.entities?.add(TrackerBean(entity, tileAlpha))
                    } else {
                        latest?.entities?.add(NormalBean(entity, tileAlpha))
                    }
                }
            }
            val delay = cfg.getInt(HassConfig.Ui_PageDelay, 0)
            if (delay > 10) Thread.sleep(delay.toLong())
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
        fragment?.emptyView?.visibility = if (adapter.groups?.size ?: 0 > 0) View.GONE else View.VISIBLE
        fragment?.loading?.visibility = View.GONE
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