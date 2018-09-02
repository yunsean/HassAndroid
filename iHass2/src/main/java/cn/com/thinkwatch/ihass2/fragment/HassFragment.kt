package cn.com.thinkwatch.ihass2.fragment

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentStatePagerAdapter
import android.view.View
import android.view.animation.Animation
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.bus.*
import cn.com.thinkwatch.ihass2.control.*
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.Panel
import cn.com.thinkwatch.ihass2.ui.CameraViewActivity
import cn.com.thinkwatch.ihass2.ui.MapActivity
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import cn.com.thinkwatch.ihass2.view.FloatWindow
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Animations
import com.dylan.uiparts.activity.ActivityResult
import com.google.gson.Gson
import com.yunsean.dynkotlins.extensions.start
import com.yunsean.dynkotlins.extensions.toastex
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_hass_main.*
import kotlinx.android.synthetic.main.fragment_hass_main.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.dip
import org.jetbrains.anko.support.v4.onRefresh


class HassFragment : BaseFragment() {

    private var panelsFragment: ControlFragment? = null
    private var dataDisposable: Disposable? = null
    override val layoutResId: Int = R.layout.fragment_hass_main
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        this.panels = db.listPanel()

        ui()
        tabbar()
        fragment.postDelayed({ data() }, 500)
        disposable = RxBus2.getDefault().register(PanelChanged::class.java, {
            if (it.panelId == 0L) {
                panels = db.listPanel()
                adapter.notifyDataSetChanged()
            }
        }, RxBus2.getDefault().register(HassConfiged::class.java, {
            panels = db.listPanel()
            adapter.notifyDataSetChanged()
        }, RxBus2.getDefault().register(ConfigChanged::class.java, {
            swipeRefresh.isEnabled = cfg.getBoolean(HassConfig.Ui_PullRefresh)
        }, RxBus2.getDefault().register(EntityLongClicked::class.java, {
            onLongClicked(it.entity)
        }, RxBus2.getDefault().register(EntityClicked::class.java, {
            onClicked(it.entity)
        }, RxBus2.getDefault().register(RefreshEvent::class.java, {
            data()
        }, RxBus2.getDefault().register(NetBusyEvent::class.java, {
            this.networkBusy.visibility = if (it.busy) View.VISIBLE else View.GONE
        }, RxBus2.getDefault().register(HassErrorEvent::class.java, {
            showError(it.message)
        }, RxBus2.getDefault().register(ChoosePanel::class.java, { event->
            if (event.panel == null) {
                if (panelsFragment == null) panelsFragment = ControlFragment.newInstance(JsonEntity(), PanelsFragment::class.java)
                if (!(panelsFragment?.isAdded ?: false)) panelsFragment?.show(childFragmentManager)
            } else {
                val index = panels.indexOfFirst { it.id == event.panel.id }
                if (index >= 0) viewPager.setCurrentItem(index, true)
            }
        }, disposable)))))))))
    }
    override fun onResume() {
        super.onResume()
        data()
    }
    override fun onPause() {
        floatWindow?.hideFloatWindow()
        super.onPause()
    }
    override fun onDestroy() {
        dataDisposable?.dispose()
        super.onDestroy()
    }

    private fun onClicked(entity: JsonEntity) {
        val fragmentManager = childFragmentManager
        if (entity.isSwitch) {
            return ControlFragment.newInstance(entity, SwitchFragment::class.java).show(fragmentManager)
        } else if (entity.isVacuum) {
            return ControlFragment.newInstance(entity, VacuumFragment::class.java).show(fragmentManager)
        } else if (entity.isAutomation) {
            return ControlFragment.newInstance(entity, AutomationFragment::class.java).show(fragmentManager)
        } else if (entity.isClimate) {
            return ControlFragment.newInstance(entity, ClimateFragment::class.java).show(fragmentManager)
        } else if (entity.isDeviceTracker) {
            return ControlFragment.newInstance(entity, TrackerFragment::class.java).show(fragmentManager)
        } else if (entity.isBinarySensor) {
            return ControlFragment.newInstance(entity, BinaryFragment::class.java).show(fragmentManager)
        } else if (entity.isSensor && entity.attributes?.unitOfMeasurement != null) {
            return ControlFragment.newInstance(entity, ChartFragment::class.java).show(fragmentManager)
        } else if (entity.isSensor || entity.isSun) {
            return ControlFragment.newInstance(entity, DetailFragment::class.java).show(fragmentManager)
        } else if (entity.isScript) {
            app.callService(ServiceRequest("homeassistant", "turn_on", entity.entityId))
        } else if (entity.isInputSelect) {
            return ControlFragment.newInstance(entity, InputSelectFragment::class.java).show(fragmentManager)
        } else if (entity.isInputText) {
            return ControlFragment.newInstance(entity, InputTextFragment::class.java).show(fragmentManager)
        } else if (entity.isMiioGateway) {
            return ControlFragment.newInstance(entity, RadioFragment::class.java).show(fragmentManager)
        } else if (entity.isBroadcast) {
            return ControlFragment.newInstance(entity, BroadcastFragment::class.java).show(fragmentManager)
        } else if (entity.isInputSlider) {
            return ControlFragment.newInstance(entity, InputSliderFragment::class.java).show(fragmentManager)
        } else if (entity.isCover) {
            return ControlFragment.newInstance(entity, CoverFragment::class.java).show(fragmentManager)
        } else if (entity.isLight) {
            app.callService(ServiceRequest("homeassistant", entity.nextState, entity.entityId))
        } else if (entity.isCamera) {
            Intent(ctx, CameraViewActivity::class.java)
                    .putExtra("entity", Gson().toJson(entity))
                    .start(ctx)
        }
    }
    private fun onLongClicked(entity: JsonEntity) {
        val fragmentManager = childFragmentManager
        if (entity.isSwitch) {
            return ControlFragment.newInstance(entity, SwitchFragment::class.java).show(fragmentManager)
        } else if (entity.isVacuum) {
            return ControlFragment.newInstance(entity, DetailFragment::class.java).show(fragmentManager)
        } else if (entity.isAutomation) {
            return ControlFragment.newInstance(entity, AutomationFragment::class.java).show(fragmentManager)
        } else if (entity.isClimate || entity.isMiioGateway) {
            return ControlFragment.newInstance(entity, DetailFragment::class.java).show(fragmentManager)
        } else if (entity.isLight) {
            return ControlFragment.newInstance(entity, LightFragment::class.java).show(fragmentManager)
        } else if (entity.isDeviceTracker && entity.attributes?.latitude != null && entity.attributes?.longitude != null) {
            Intent(ctx, MapActivity::class.java)
                    .putExtra("entityId", entity?.entityId)
                    .start(ctx)
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
        if (floatWindow == null) floatWindow = FloatWindow(act)
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
        this.viewPager.setCanScroll(true)
        this.viewPager.adapter = adapter
        this.tabbar.setupWithViewPager(this.viewPager)
        this.tabbar.setSelectedTabIndicatorHeight(dip(3))
    }

    private fun ui() {
        this.swipeRefresh.onRefresh { data() }
        this.swipeRefresh.setSwipeableChildren(this.viewPager)
        this.swipeRefresh.isEnabled = cfg.getBoolean(HassConfig.Ui_PullRefresh)
        this.refresh.onClick { data()  }
    }
    private fun data() {
        this.swipeRefresh.isRefreshing = false
        this.networkBusy.visibility = View.VISIBLE
        this.swipeRefresh.isEnabled = false
        Animations.RotateAnimation(this.refresh, 0f, 360f, Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5f)
                .duration(1000)
                .repeatCount(1000)
                .start()
        app.refreshState {
            fragment?.apply {
                swipeRefresh?.isEnabled = cfg.getBoolean(HassConfig.Ui_PullRefresh)
                refresh?.clearAnimation()
                networkBusy?.visibility = View.GONE
                if (it != null) showError(it, "重试") { data() }
            }
        }
    }
}