package cn.com.thinkwatch.ihass2.fragment

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentStatePagerAdapter
import android.view.View
import android.view.animation.Animation
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.api.BaseApi
import cn.com.thinkwatch.ihass2.api.hassRawApi
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.bus.*
import cn.com.thinkwatch.ihass2.control.*
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.Panel
import cn.com.thinkwatch.ihass2.service.DataSyncService
import cn.com.thinkwatch.ihass2.ui.CameraViewActivity
import cn.com.thinkwatch.ihass2.view.FloatWindow
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Animations
import com.dylan.uiparts.activity.ActivityResult
import com.google.gson.Gson
import com.yunsean.dynkotlins.extensions.nextOnMain
import com.yunsean.dynkotlins.extensions.readPref
import com.yunsean.dynkotlins.extensions.start
import com.yunsean.dynkotlins.extensions.toastex
import io.reactivex.Observable
import kotlinx.android.synthetic.main.fragment_hass_main.*
import kotlinx.android.synthetic.main.fragment_hass_main.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.dip
import org.jetbrains.anko.support.v4.onRefresh
import org.json.JSONArray


class HassFragment : BaseFragment() {

    private var panelsFragment: ControlFragment? = null
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
        }, RxBus2.getDefault().register(ServiceRequest::class.java, {
            callService(it)
        }, RxBus2.getDefault().register(HassConfiged::class.java, {
            panels = db.listPanel()
            adapter.notifyDataSetChanged()
        }, RxBus2.getDefault().register(EntityClicked::class.java, {
            onClicked(it.entity)
        }, RxBus2.getDefault().register(ConfigChanged::class.java, {
            swipeRefresh.isEnabled = ctx.readPref("pullRefresh")?.toBoolean() ?: false
        }, RxBus2.getDefault().register(EntityLongClicked::class.java, {
            onLongClicked(it.entity)
        }, RxBus2.getDefault().register(RefreshEvent::class.java, {
            data()
        }, RxBus2.getDefault().register(ChoosePanel::class.java, { event->
            if (event.panel == null) {
                if (panelsFragment == null) panelsFragment = ControlFragment.newInstance(JsonEntity(), PanelsFragment::class.java)
                if (!(panelsFragment?.isAdded ?: false)) panelsFragment?.show(childFragmentManager)
            } else {
                val index = panels.indexOfFirst { it.id == event.panel.id }
                if (index >= 0) viewPager.setCurrentItem(index, true)
            }
        }, disposable))))))))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(ctx, DataSyncService::class.java)
        ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    override fun onPause() {
        floatWindow?.hideFloatWindow()
        super.onPause()
    }
    override fun onDestroy() {
        if (serviceBound) ctx.unbindService(serviceConnection)
        serviceBound = false
        super.onDestroy()
    }
    private fun onLongClicked(entity: JsonEntity) {
        val fragmentManager = childFragmentManager
        if (entity.isSwitch) {
            ControlFragment.newInstance(entity, SwitchFragment::class.java).show(fragmentManager)
        } else if (entity.isVacuum) {
            ControlFragment.newInstance(entity, VacuumFragment::class.java).show(fragmentManager)
        } else if (entity.isAutomation) {
            ControlFragment.newInstance(entity, AutomationFragment::class.java).show(fragmentManager)
        } else if (entity.isClimate) {
            ControlFragment.newInstance(entity, ClimateFragment::class.java).show(fragmentManager)
        } else if (entity.isScript) {
            callService(ServiceRequest("homeassistant", "turn_on", entity.entityId))
        } else if (entity.isCamera) {
            showCameraOverlay(entity)
        } else if (entity.isLight) {
            ControlFragment.newInstance(entity, LightFragment::class.java).show(fragmentManager)
        }
    }
    private fun onClicked(entity: JsonEntity) {
        val fragmentManager = childFragmentManager
        if (entity.isSwitch) {
            ControlFragment.newInstance(entity, SwitchFragment::class.java).show(fragmentManager)
        } else if (entity.isVacuum) {
            ControlFragment.newInstance(entity, VacuumFragment::class.java).show(fragmentManager)
        } else if (entity.isAutomation) {
            ControlFragment.newInstance(entity, AutomationFragment::class.java).show(fragmentManager)
        } else if (entity.isClimate) {
            ControlFragment.newInstance(entity, ClimateFragment::class.java).show(fragmentManager)
        } else if (entity.isDeviceTracker) {
            ControlFragment.newInstance(entity, TrackerFragment::class.java).show(fragmentManager)
        } else if (entity.isBinarySensor) {
            ControlFragment.newInstance(entity, BinaryFragment::class.java).show(fragmentManager)
        } else if (entity.isSensor && entity.attributes?.unitOfMeasurement != null) {
            ControlFragment.newInstance(entity, ChartFragment::class.java).show(fragmentManager)
        } else if (entity.isSensor || entity.isSun) {
            ControlFragment.newInstance(entity, DetailFragment::class.java).show(fragmentManager)
        } else if (entity.isScript) {
            callService(ServiceRequest("homeassistant", "turn_on", entity.entityId))
        } else if (entity.isInputSelect) {
            ControlFragment.newInstance(entity, InputSelectFragment::class.java).show(fragmentManager)
        } else if (entity.isInputText) {
            ControlFragment.newInstance(entity, InputTextFragment::class.java).show(fragmentManager)
        } else if (entity.isInputSlider) {
            ControlFragment.newInstance(entity, InputSliderFragment::class.java).show(fragmentManager)
        } else if (entity.isCover) {
            ControlFragment.newInstance(entity, CoverFragment::class.java).show(fragmentManager)
        } else if (entity.isLight) {
            callService(ServiceRequest("homeassistant", entity.nextState, entity.entityId))
        } else if (entity.isCamera) {
            Intent(ctx, CameraViewActivity::class.java)
                    .putExtra("entity", Gson().toJson(entity))
                    .start(ctx)
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
    private fun callService(request: ServiceRequest) {
        syncService?.let {
            if (it.isWebSocketRunning && it.callService(request.domain, request.service, request))
                return
        }
        this.networkBusy.visibility = View.VISIBLE
        BaseApi.api(app.haHostUrl).callService(app.haPassword, request.domain, request.service, request)
                .flatMap {
                    it.forEach { db.saveEntity(it) }
                    Observable.just(it)
                }
                .nextOnMain {
                    networkBusy.visibility = View.GONE
                    if (request.domain in arrayOf("script", "automation", "scene", "trigger")) showError("已执行")
                    it.forEach { RxBus2.getDefault().post(it) }
                }
                .error {
                    networkBusy.visibility = View.GONE
                    showError(it.message ?: "访问HA出现错误")
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
        this.swipeRefresh.isEnabled = ctx.readPref("pullRefresh")?.toBoolean() ?: false
        this.refresh.onClick { data()  }
    }
    private fun data() {
        if (app.haHostUrl.isNullOrBlank()) return
        this.swipeRefresh.isRefreshing = false
        this.networkBusy.visibility = View.VISIBLE
        this.swipeRefresh.isEnabled = false
        Animations.RotateAnimation(this.refresh, 0f, 360f, Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5f)
                .duration(1000)
                .repeatCount(1000)
                .start()
        hassRawApi.rawStates(app.haPassword)
                .flatMap {
                    val entities = JSONArray(it)
                    db.saveEntities(entities)
                    Observable.just(it)
                }
                .nextOnMain {
                    this.swipeRefresh.isEnabled = ctx.readPref("pullRefresh")?.toBoolean() ?: false
                    fragment.refresh.clearAnimation()
                    fragment.networkBusy.visibility = View.GONE
                    RxBus2.getDefault().post(EntityUpdated())
                }
                .error {
                    it.printStackTrace()
                    this.swipeRefresh.isEnabled = ctx.readPref("pullRefresh")?.toBoolean() ?: false
                    fragment.refresh.clearAnimation()
                    fragment.networkBusy.visibility = View.GONE
                    showError(it.message ?: "未知错误", "重试") { data() }
                }
    }


    private var syncService: DataSyncService? = null
    private var serviceBound: Boolean = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as DataSyncService.LocalBinder
            syncService = binder.service
            serviceBound = true
            syncService?.startWebSocket()
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            serviceBound = false
        }
    }
}