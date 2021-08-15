package cn.com.thinkwatch.ihass2.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.loges
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.pager_hass_camera_ptz.*
import java.util.concurrent.TimeUnit


class CameraPtzFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.pager_hass_camera_ptz
    private var entityId: String? = null
    private var cameraType: String? = null
    private var ptzSpeed: Double? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        entityId = arguments?.getString("entityId", "")
        cameraType = arguments?.getString("cameraType", "")
        ptzSpeed = arguments?.getDouble("speed")

        ui()
    }
    override fun onDestroyView() {
        intervalDisposable?.dispose()
        super.onDestroyView()
    }
    private var intervalDisposable: Disposable? = null
    private fun callService(pan: String? = null,
                            tilt: String? = null) {
        if (cameraType == "hass_old") {
            RxBus2.getDefault().post(ServiceRequest("camera", "onvif_ptz", entityId = entityId, pan = pan, tilt = tilt))
        } else if (cameraType == "hass") {
            RxBus2.getDefault().post(ServiceRequest("onvif", "ptz", entityId = entityId, pan = pan, tilt = tilt, moveMode = "ContinuousMove", speed = ptzSpeed?.toString()))
        } else {
            if (pan == null && tilt == null) intervalDisposable?.dispose()
            else intervalDisposable = Observable.interval(0, 500, TimeUnit.MILLISECONDS).doOnNext {
                RxBus2.getDefault().post(ServiceRequest("onvif", "ptz", entityId = entityId, pan = pan, tilt = tilt, moveMode = "ContinuousMove", continuousDuration = 0.5f, speed = ptzSpeed?.toString()))
            }.subscribe()
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun ui() {
        this.left.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) callService("LEFT")
            else if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP) callService()
            true
        }
        this.right.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) callService("RIGHT")
            else if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP) callService()
            true
        }
        this.up.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) callService(null, "UP")
            else if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP) callService()
            true
        }
        this.down.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) callService(null, "DOWN")
            else if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP) callService()
            true
        }
    }
}