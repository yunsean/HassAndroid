package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.kdateTime
import kotlinx.android.synthetic.main.control_automation.view.*
import org.jetbrains.anko.sdk25.coroutines.onCheckedChange
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import java.text.SimpleDateFormat

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class AutomationFragment : ControlFragment() {

    private var fragment: View? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(getActivity())
        fragment = act.getLayoutInflater().inflate(R.layout.control_automation, null)
        builder.setView(fragment)
        builder.setTitle(if (entity?.showName.isNullOrBlank()) entity?.friendlyName else entity?.showName)
        return builder.create()
    }

    override fun onResume() {
        super.onResume()
        ui()
    }
    private fun ui() {
        fragment?.apply {
            button_trigger.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "trigger", entity?.entityId)) }
            button_close.onClick { dismiss() }
            switch_toggle.onCheckedChange { buttonView, isChecked -> RxBus2.getDefault().post(ServiceRequest(entity?.domain, "turn_" + if (isChecked) "on" else "off", entity?.entityId)) }
        }
        refreshUi()
    }

    private fun refreshUi() {
        fragment?.latest_trigger?.text = try { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZZZZZ").parse(entity?.attributes?.lastTriggered)?.kdateTime() } catch (_: Exception) { entity?.attributes?.lastTriggered } ?: "无"
        fragment?.switch_toggle?.isChecked = if ("ON" == entity?.friendlyState) true else false
    }

    override fun onChange() {
        refreshUi()
    }
}
