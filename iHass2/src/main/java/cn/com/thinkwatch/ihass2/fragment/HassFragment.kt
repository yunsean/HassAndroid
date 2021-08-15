package cn.com.thinkwatch.ihass2.fragment

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.support.design.widget.TabItem
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.content.res.ResourcesCompat
import android.support.v4.view.ViewPager
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.bus.*
import cn.com.thinkwatch.ihass2.control.*
import cn.com.thinkwatch.ihass2.control.AutomationFragment
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.getMessage
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.model.Panel
import cn.com.thinkwatch.ihass2.ui.CameraViewActivity
import cn.com.thinkwatch.ihass2.utils.Gsons
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import cn.com.thinkwatch.ihass2.view.FloatWindow
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Animations
import com.dylan.uiparts.activity.ActivityResult
import com.yunsean.dynkotlins.extensions.start
import com.yunsean.dynkotlins.extensions.toastex
import com.yunsean.dynkotlins.extensions.withComplete
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_hass_main.*
import kotlinx.android.synthetic.main.fragment_hass_main.view.*
import kotlinx.android.synthetic.main.listitem_hass_panel_thumb.view.*
import kotlinx.android.synthetic.main.listitem_panel_item.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.*
import org.jetbrains.anko.vibrator
import q.rorbin.verticaltablayout.VerticalTabLayout
import q.rorbin.verticaltablayout.adapter.TabAdapter
import q.rorbin.verticaltablayout.widget.ITabView
import q.rorbin.verticaltablayout.widget.TabView


class HassFragment : BaseFragment() {

    private var panelsFragment: ControlFragment? = null
    private var dataDisposable: Disposable? = null
    override val layoutResId: Int = R.layout.fragment_hass_main
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        this.panels = db.listPanel()

        ui()
        tabbar()
        group()
        slider()
        disposable = RxBus2.getDefault().register(PanelChanged::class.java, {
            if (it.isAdd) {
                panels = db.listPanel()
                groupAdapter.items = panels
                adapter.notifyDataSetChanged()
                sliderAdapter.items = panels
                allGroups.visibility = if (panels.size > 1 && cfg.getBoolean(HassConfig.Ui_ShowSidebar)) View.VISIBLE else View.GONE
                this.tabbarPanel.visibility = if (panels.size > 1 && cfg.getBoolean(HassConfig.Ui_ShowTopbar, true)) View.VISIBLE else View.GONE
            }
        }, RxBus2.getDefault().register(HassConfiged::class.java, {
            panels = db.listPanel()
            groupAdapter.items = panels
            sliderAdapter.items = panels
            adapter.notifyDataSetChanged()
        }, RxBus2.getDefault().register(DisplayPanel::class.java, {
            showPanel(it.show, bottomMargin = it.bottomMargin)
        }, RxBus2.getDefault().register(ConfigChanged::class.java, {
            swipeRefresh.isEnabled = cfg.getBoolean(HassConfig.Ui_PullRefresh)
            allGroups.visibility = if (panels.size > 1 && cfg.getBoolean(HassConfig.Ui_ShowSidebar)) View.VISIBLE else View.GONE
            tabbarPanel.visibility = if (panels.size > 1 && cfg.getBoolean(HassConfig.Ui_ShowTopbar, true)) View.VISIBLE else View.GONE
        }, RxBus2.getDefault().register(EntityLongClicked::class.java, {
            onLongClicked(it.entity)
        }, RxBus2.getDefault().register(EntityClicked::class.java, {
            onClicked(it.entity)
        }, RxBus2.getDefault().register(ConnectChanged::class.java, {
            showInfo(if (it.isLocal) "已自动切换为内网连接" else "已切换为外网连接", "关闭")
        }, RxBus2.getDefault().register(RefreshEvent::class.java, {
            data()
        }, RxBus2.getDefault().register(NetBusyEvent::class.java, {
            networkBusy.visibility = if (it.busy) View.VISIBLE else View.GONE
        }, RxBus2.getDefault().register(HassErrorEvent::class.java, {
            showError(it.message)
        }, RxBus2.getDefault().register(ChoosePanel::class.java, { event->
            showPanel(event.panel)
        }, RxBus2.getDefault().register(MotionEvent::class.java, { event->
            choiceGroup(event)
        }, disposable))))))))))))
    }
    override fun onResume() {
        super.onResume()
    }
    override fun onPause() {
        //floatWindow?.hideFloatWindow()
        super.onPause()
    }
    override fun onDestroy() {
        dataDisposable?.dispose()
        super.onDestroy()
    }

    fun showPanel(panel: Panel?) {
        if (panel == null) {
            if (panelsFragment == null) panelsFragment = ControlFragment.newInstance(JsonEntity(), PanelsFragment::class.java)
            if (!(panelsFragment?.isAdded ?: false)) panelsFragment?.show(childFragmentManager)
        } else {
            val index = panels.indexOfFirst { it.id == panel.id }
            if (index >= 0) viewPager.setCurrentItem(index, true)
        }
    }
    private fun onClicked(entity: JsonEntity) {
        val fragmentManager = childFragmentManager
        if (entity.isSwitch) {
            return ControlFragment.newInstance(entity, SwitchFragment::class.java).show(fragmentManager)
        } else if (entity.isFan) {
            return ControlFragment.newInstance(entity, FanFragment::class.java).show(fragmentManager)
        } else if (entity.isVacuum) {
            return ControlFragment.newInstance(entity, VacuumFragment::class.java).show(fragmentManager)
        } else if (entity.isAutomation) {
            return ControlFragment.newInstance(entity, cn.com.thinkwatch.ihass2.control.AutomationFragment::class.java).show(fragmentManager)
        } else if (entity.isClimate) {
            return ControlFragment.newInstance(entity, ClimateFragment::class.java).show(fragmentManager)
        } else if (entity.isDeviceTracker) {
            return ControlFragment.newInstance(entity, TrackerFragment::class.java).show(fragmentManager)
        } else if (entity.isBinarySensor) {
            return ControlFragment.newInstance(entity, BinaryFragment::class.java).show(fragmentManager)
        } else if (entity.isSensor && entity.attributes?.unitOfMeasurement != null && (entity.attributes?.ihassDays ?: -1) != 0) {
            return ControlFragment.newInstance(entity, ChartFragment::class.java).show(fragmentManager)
        } else if (entity.isSensor || entity.isSun) {
            return ControlFragment.newInstance(entity, EntityFragment::class.java).show(fragmentManager)
        } else if (entity.isScript) {
            app.callService(ServiceRequest("homeassistant", "turn_on", entity.entityId))
        } else if (entity.isMediaPlayer) {
            return ControlFragment.newInstance(entity, PlayerFragment::class.java).show(fragmentManager)
        } else if (entity.isInputSelect) {
            return ControlFragment.newInstance(entity, InputSelectFragment::class.java).show(fragmentManager)
        } else if (entity.isInputText) {
            return ControlFragment.newInstance(entity, InputTextFragment::class.java).show(fragmentManager)
        } else if (entity.isMiioGateway) {
            return ControlFragment.newInstance(entity, RadioFragment::class.java).show(fragmentManager)
        } else if (entity.isBroadcastRadio) {
            return ControlFragment.newInstance(entity, BroadcastFragment::class.java).show(fragmentManager)
        } else if (entity.isBroadcastVoice) {
            return ControlFragment.newInstance(entity, VoiceFragment::class.java).show(fragmentManager)
        } else if (entity.isBroadcastMusic) {
            return ControlFragment.newInstance(entity, MusicFragment::class.java).show(fragmentManager)
        } else if (entity.isInputSlider) {
            return ControlFragment.newInstance(entity, InputSliderFragment::class.java).show(fragmentManager)
        } else if (entity.isCover) {
            return ControlFragment.newInstance(entity, CoverFragment::class.java).show(fragmentManager)
        } else if (entity.isLock) {
            return ControlFragment.newInstance(entity, LockFragment::class.java).show(fragmentManager)
        } else if (entity.isLight) {
            app.callService(ServiceRequest("homeassistant", entity.nextState, entity.entityId))
        } else if (entity.isCamera) {
            Intent(ctx, CameraViewActivity::class.java)
                    .putExtra("entity", Gsons.gson.toJson(entity))
                    .start(ctx)
        }
    }
    private fun onLongClicked(entity: JsonEntity) {
        val fragmentManager = childFragmentManager
        if (entity.isSwitch || entity.isInputBoolean) {
            return ControlFragment.newInstance(entity, SwitchFragment::class.java).show(fragmentManager)
        } else if (entity.isAutomation) {
            return ControlFragment.newInstance(entity, AutomationFragment::class.java).show(fragmentManager)
        } else if (entity.isLight) {
            return ControlFragment.newInstance(entity, LightFragment::class.java).show(fragmentManager)
        } else if (entity.isCamera) {
            showCameraOverlay(entity)
        } else {
            return ControlFragment.newInstance(entity, DetailFragment::class.java).show(fragmentManager)
        }
    }

    private var floatWindow: FloatWindow? = null
    private fun showCameraOverlay(entity: JsonEntity) {
        val url = entity.attributes?.previewUrl
        if (url == null || url.isBlank()) return ctx.toastex("摄像头无预览地址！")
        if (Build.VERSION.SDK_INT >= 23) {
            if (!Settings.canDrawOverlays(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))
                return startActivityForResult(intent, 1000)
            }
        }
        if (floatWindow == null) floatWindow = FloatWindow(act, ctx.applicationContext)
        floatWindow?.showFloatWindow(entity)
    }
    @ActivityResult(requestCode = 1000)
    private fun afterDrawOverlayRequest() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (!Settings.canDrawOverlays(ctx)) {
                act.toastex("用户取消浮动窗口授权！")
            }
        }
    }

    private lateinit var panels: List<Panel>
    private lateinit var adapter: FragmentStatePagerAdapter
    private fun tabbar() {
        adapter = object : FragmentStatePagerAdapter(childFragmentManager) {
            override fun getPageTitle(position: Int): CharSequence = panels.get(position).name
            override fun getCount(): Int = panels.size
            override fun getItem(position: Int): Fragment = BaseFragment.newInstance(PanelFragment::class.java, Intent().putExtra("panelId", panels.get(position).id))
        }
        this.tabbarPanel.visibility = if (panels.size > 1 && cfg.getBoolean(HassConfig.Ui_ShowTopbar, true)) View.VISIBLE else View.GONE
        this.allGroups.visibility = if (panels.size > 1 && cfg.getBoolean(HassConfig.Ui_ShowSidebar)) View.VISIBLE else View.GONE
        this.viewPager.setCanScroll(true)
        this.viewPager.adapter = adapter
        this.tabbar.setupWithViewPager(this.viewPager)
        this.tabbar.setSelectedTabIndicatorHeight(dip(3F))
        this.tabbar.setOnTabSelectedListener(object: TabLayout.OnTabSelectedListener {
            override fun onTabSelected(p0: TabLayout.Tab?) {
                if (p0 == null) return
                viewPager.setCurrentItem(p0.position, false)
                sliderAdapter.notifyDataSetChanged()
                panels[p0.position].apply { RxBus2.getDefault().post(CurrentPanelChanged(id, name)) }
            }
            override fun onTabReselected(p0: TabLayout.Tab?) = Unit
            override fun onTabUnselected(p0: TabLayout.Tab?) = Unit
        })
    }

    private lateinit var sliderAdapter: RecyclerAdapter<Panel>
    private fun slider() {
        this.sliderAdapter = RecyclerAdapter(R.layout.listitem_hass_panel_thumb, panels) {
            view, index, item ->
            if (item.icon.isNullOrBlank()) {
                view.item.text = item.name
                view.item.textSize = 14F
            } else {
                MDIFont.get().setIcon(view.item, item.icon)
                view.item.textSize = 28F
            }
            view.item.isSelected = index == viewPager.currentItem
            view.onClick {
                viewPager.setCurrentItem(index, false)
                sliderAdapter.notifyDataSetChanged()
            }
        }
        allGroups.adapter = sliderAdapter
        allGroups.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.VERTICAL, true)
    }

    private lateinit var groupAdapter: RecyclerAdapter<Panel>
    private var focusGroupIndex = -1
    private fun group() {
        groupAdapter = RecyclerAdapter(R.layout.listitem_main_panel_item, panels) {
            view, index, item ->
            view.name.text = item.name
            view.icon.visibility = View.GONE
            view.isActivated = index == focusGroupIndex
            view.onClick {
                showPanel(false, index)
            }
        }
        groups.adapter = groupAdapter
        val layoutManager = GridLayoutManager(ctx, 3)
        layoutManager.reverseLayout = false
        groups.layoutManager = layoutManager
        groupPanel.onClick { showPanel(false) }
    }
    private fun choiceGroup(event: MotionEvent) {
        if (groupPanel.visibility != View.VISIBLE) return
        val location = IntArray(2)
        groups.getLocationInWindow(location)
        val view = groups.findChildViewUnder(event.getRawX() - location[0], event.getRawY() - location[1])
        val index = if (view != null) groups.getChildAdapterPosition(view) else focusGroupIndex
        if (index != focusGroupIndex) {
            focusGroupIndex = index
            groupAdapter.notifyDataSetChanged()
            ctx.vibrator.vibrate(20)
        }
        if (event.action == MotionEvent.ACTION_UP && focusGroupIndex >= 0) {
            showPanel(false, focusGroupIndex)
        }
    }
    private fun showPanel(show: Boolean, switchTo: Int? = null, bottomMargin: Int = 0) {
        if (show && groupPanel.visibility == View.GONE) {
            (panelCard.layoutParams as ViewGroup.MarginLayoutParams?)?.let {
                it.bottomMargin = bottomMargin
                panelCard.requestLayout()
            }
            groupPanel.visibility = View.VISIBLE
            focusGroupIndex = -1
            groupAdapter.notifyDataSetChanged()
            Animations.ScaleAnimationY(groupPanel, 0F, 1F, 1F)
                    .duration(300)
                    .start()
        } else if (!show && groupPanel.visibility == View.VISIBLE) {
            Animations.ScaleAnimationY(groupPanel, 1F, 0F, 1F)
                    .duration(300)
                    .animationListener {
                        groupPanel.visibility = View.GONE
                        if (switchTo != null && switchTo >= 0 && switchTo < panels.size) viewPager.setCurrentItem(switchTo, false)
                    }
                    .start()
        }
    }

    private fun ui() {
        this.swipeRefresh.onRefresh { data() }
        this.swipeRefresh.setSwipeableChildren(this.viewPager)
        this.swipeRefresh.isEnabled = cfg.getBoolean(HassConfig.Ui_PullRefresh)
        allGroups.visibility = if (panels.size > 1 && cfg.getBoolean(HassConfig.Ui_ShowSidebar)) View.VISIBLE else View.GONE
        tabbarPanel.visibility = if (panels.size > 1 && cfg.getBoolean(HassConfig.Ui_ShowTopbar, true)) View.VISIBLE else View.GONE
        if (!panels.isEmpty()) panels[0].apply { RxBus2.getDefault().post(CurrentPanelChanged(id, name)) }
        this.refresh.onClick { data() }
    }
    private fun data() {
        Animations.RotateAnimation(this.refresh, 0f, 360f, Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5f)
                .duration(1000)
                .repeatCount(1000)
                .start()
        dataDisposable?.dispose()
        this.swipeRefresh.isRefreshing = false
        this.networkBusy.visibility = View.VISIBLE
        this.swipeRefresh.isEnabled = false
        app.refreshState().withComplete {
            fragment?.apply {
                swipeRefresh.isEnabled = cfg.getBoolean(HassConfig.Ui_PullRefresh)
                refresh.clearAnimation()
                networkBusy.visibility = View.GONE
            }
        }.error {
            fragment?.apply {
                swipeRefresh.isEnabled = cfg.getBoolean(HassConfig.Ui_PullRefresh)
                refresh.clearAnimation()
                networkBusy.visibility = View.GONE
            }
            showError(it.getMessage() ?: "刷新状态失败", "重试") { data() }
        }.subscribeOnMain {
            dataDisposable = it
        }
    }
}