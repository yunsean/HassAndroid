package cn.com.thinkwatch.ihass2.fragment

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.loges
import kotlinx.android.synthetic.main.pager_hass_camera_ptz.*


class CameraPtzFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.pager_hass_camera_ptz
    private var entityId: String? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        entityId = arguments?.getString("entityId", "")

        ui()
    }
    private fun callService(pan: String? = null,
                            tilt: String? = null) {
        loges("callService($pan, $tilt)")
        RxBus2.getDefault().post(ServiceRequest("camera", "onvif_ptz", entityId = entityId, pan = pan, tilt = tilt))
    }
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