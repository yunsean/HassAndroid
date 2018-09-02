package cn.com.thinkwatch.ihass2.ui

import android.content.Intent
import android.os.Bundle
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.ControlDismissed
import cn.com.thinkwatch.ihass2.control.*
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import com.dylan.common.rx.RxBus2
import com.google.gson.Gson
import com.yunsean.dynkotlins.extensions.start
import org.jetbrains.anko.ctx

class EmptyActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_empty)

        val entity = try { if (intent.hasExtra("entity")) Gson().fromJson<JsonEntity>(intent.getStringExtra("entity"), JsonEntity::class.java)
            else db.getEntity(intent.getStringExtra("entityId") ?: return finish()) } catch (_: Exception) { null } ?: return finish()
        val event = intent.getStringExtra("event")
        when (event) {
            "widgetClicked"-> onWidgetClicked(entity)
            else-> return finish()
        }
        disposable = RxBus2.getDefault().register(ControlDismissed::class.java, {
            finish()
        }, disposable)
    }
    override fun onPause() {
        super.onPause()
        finish()
    }

    private fun onWidgetClicked(entity: JsonEntity) {
        val fragmentManager = supportFragmentManager
        if (entity.isSwitch) {
            if (entity.isStateful) RxBus2.getDefault().post(ServiceRequest(entity.domain, "toggle", entity.entityId))
            else return ControlFragment.newInstance(entity, SwitchFragment::class.java).show(fragmentManager)
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
        finish()
    }
}

