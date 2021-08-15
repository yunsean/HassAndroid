package cn.com.thinkwatch.ihass2.fragment

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
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
import cn.com.thinkwatch.ihass2.view.AutoCenterLinearLayoutManager
import cn.com.thinkwatch.ihass2.view.FloatWindow
import com.dylan.common.rx.RxBus2
import com.dylan.uiparts.activity.ActivityResult
import com.yunsean.dynkotlins.extensions.loges
import com.yunsean.dynkotlins.extensions.start
import com.yunsean.dynkotlins.extensions.toastex
import com.yunsean.dynkotlins.extensions.withComplete
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_hass_main.*
import kotlinx.android.synthetic.main.fragment_hass_main.view.*
import kotlinx.android.synthetic.main.listitem_hass_panel_thumb.view.*
import me.everything.android.ui.overscroll.OverScrollDecoratorHelper
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.onRefresh


class HassFragment : BaseFragment() {

    private var panelsFragment: ControlFragment? = null
    private var dataDisposable: Disposable? = null
    override val layoutResId: Int = R.layout.fragment_hass_main
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        this.panels = db.listPanel()

        ui()
        tabs()
        disposable = RxBus2.getDefault().register(PanelChanged::class.java, {
            if (it.isAdd) {
                panels = db.listPanel()
                adapter.notifyDataSetChanged()
                tabsAdapter.items = panels
                warpperAdapterWrapper.notifyDataSetChanged()
            }
        }, RxBus2.getDefault().register(HassConfiged::class.java, {
            panels = db.listPanel()
            adapter.notifyDataSetChanged()
            tabsAdapter.items = panels
            warpperAdapterWrapper.notifyDataSetChanged()
        }, RxBus2.getDefault().register(ConfigChanged::class.java, {
            swipeRefresh.isEnabled = cfg.getBoolean(HassConfig.Ui_PullRefresh)
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
        }, disposable)))))))))
    }
    override fun onDestroy() {
        dataDisposable?.dispose()
        super.onDestroy()
    }

    fun showPanel(panel: Panel?) {
        if (panel == null) {
            if (panelsFragment == null) panelsFragment = ControlFragment.newInstance(JsonEntity(), PanelsFragment::class.java)
            if (panelsFragment?.isAdded != true) panelsFragment?.show(childFragmentManager)
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
    private lateinit var adapter: FragmentPagerAdapter
    private lateinit var tabsAdapter: RecyclerAdapter<Panel>
    private lateinit var warpperAdapterWrapper: RecyclerAdapterWrapper<*>
    private lateinit var tabsLayoutManager: AutoCenterLinearLayoutManager
    private var lastClickedView: View? = null
    private var lastClickedTime = 0L
    private fun tabs() {
        this.tabsAdapter = RecyclerAdapter(R.layout.listitem_hass_panel_thumb, panels) {
                view, index, item ->
            if (item.icon.isNullOrBlank()) {
                view.item.text = item.name
                view.item.textSize = if (index == viewPager.currentItem) 22F else 15F
            } else {
                MDIFont.get().setIcon(view.item, item.icon)
                view.item.textSize = if (index == viewPager.currentItem) 36F else 24F
            }
            view.item.isSelected = index == viewPager.currentItem
            view.setOnClickListener {
                lastClickedView = it
                if (System.currentTimeMillis() - lastClickedTime < 1000) {
                    RxBus2.getDefault().post(RefreshEvent())
                    lastClickedTime = 0L
                } else {
                    lastClickedTime = System.currentTimeMillis()
                }
                viewPager.setCurrentItem(index, true)
            }
        }
        tabsLayoutManager = AutoCenterLinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        tabs.layoutManager = tabsLayoutManager
        warpperAdapterWrapper = RecyclerAdapterWrapper(tabsAdapter)
            .addFootView(layoutInflater.inflate(R.layout.layout_hass_home_right, tabs, false))
        tabs.adapter = warpperAdapterWrapper
        //OverScrollDecoratorHelper.setUpOverScroll(tabs, OverScrollDecoratorHelper.ORIENTATION_HORIZONTAL)

        this.adapter = object : FragmentPagerAdapter(childFragmentManager) {
            override fun getPageTitle(position: Int): CharSequence = panels.get(position).name
            override fun getCount(): Int = panels.size
            override fun getItem(position: Int): Fragment {
                val panel = panels[position]
                return if (panel.stubbornClass.isNullOrBlank()) newInstance(PanelFragment::class.java, Intent().putExtra("panelId", panel.id))
                else Class.forName(panel.stubbornClass!!).newInstance() as Fragment
            }
        }
        this.viewPager.setCanScroll(true)
        this.viewPager.adapter = this.adapter
        this.viewPager.offscreenPageLimit = panels.size
        this.viewPager.addOnPageChangeListener(object: ViewPager.OnPageChangeListener {
            override fun onPageScrolled(p0: Int, p1: Float, p2: Int) = Unit
            override fun onPageScrollStateChanged(p0: Int) = Unit
            override fun onPageSelected(index: Int) {
                warpperAdapterWrapper.notifyDataSetChanged()
                tabsLayoutManager.smoothScrollToPosition(tabs, RecyclerView.State(), index)
            }
        })
    }

    private fun ui() {
        this.swipeRefresh.onRefresh { data() }
        this.swipeRefresh.setSwipeableChildren(this.viewPager)
        this.swipeRefresh.isEnabled = cfg.getBoolean(HassConfig.Ui_PullRefresh)
        if (panels.isNotEmpty()) panels[0].apply { RxBus2.getDefault().post(CurrentPanelChanged(id, name)) }
    }
    private fun data() {
        dataDisposable?.dispose()
        this.swipeRefresh.isRefreshing = false
        this.networkBusy.visibility = View.VISIBLE
        this.swipeRefresh.isEnabled = false
        app.refreshState().withComplete {
            fragment?.apply {
                swipeRefresh.isEnabled = cfg.getBoolean(HassConfig.Ui_PullRefresh)
                networkBusy.visibility = View.GONE
            }
        }.error {
            fragment?.apply {
                swipeRefresh.isEnabled = cfg.getBoolean(HassConfig.Ui_PullRefresh)
                networkBusy.visibility = View.GONE
            }
            showError(it.getMessage() ?: "刷新状态失败", "重试") { data() }
        }.subscribeOnMain {
            dataDisposable = it
        }
    }
}