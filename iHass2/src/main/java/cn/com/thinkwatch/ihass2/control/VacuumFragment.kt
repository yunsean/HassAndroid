package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.R.id.*
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import com.dylan.common.rx.RxBus2
import kotlinx.android.synthetic.main.control_vacuum.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class VacuumFragment : ControlFragment() {

    private var fragment: View? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(getActivity())
        fragment = act.layoutInflater.inflate(R.layout.control_vacuum, null)
        builder.setView(fragment)
        builder.setTitle(entity?.friendlyName)
        return builder.create()
    }
    override fun onResume() {
        super.onResume()
        ui()
    }
    private fun ui() {
        fragment?.apply {
            button_close.onClick { dismiss() }
            text_start_pause.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "start_pause", entity?.entityId)) }
            text_stop.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "stop", entity?.entityId)) }
            text_locate.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "clean_spot", entity?.entityId)) }
            text_home.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "return_to_base", entity?.entityId)) }
            val speeds: List<String> = entity?.attributes?.fanSpeedList ?: listOf()
            val adapter = ArrayAdapter(getActivity(), R.layout.spinner_edittext_lookalike, speeds)
            spinner_speed.adapter = adapter
            spinner_speed.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                    RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_fan_speed", entity?.entityId, fanSpeed = speeds?.get(position)))
                }
                override fun onNothingSelected(p0: AdapterView<*>?) { }
            }
            spinner_speed.setSelection(speeds.indexOf(entity?.attributes?.fanSpeed ?: ""))
            switch_toggle.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "turn_" + if (switch_toggle.isChecked) "on" else "off", entity?.entityId)) }
        }
        refreshUi()
    }
    private fun refreshUi() {
        fragment?.switch_toggle?.isChecked = if ("OFF" == entity?.friendlyState?.toUpperCase()) false else true
    }
    override fun onChange() {
        refreshUi()
    }
}
