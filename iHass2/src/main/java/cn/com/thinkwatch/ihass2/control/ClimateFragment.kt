package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.support.v7.widget.AppCompatSpinner
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.R.id.*
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import com.dylan.common.rx.RxBus2
import kotlinx.android.synthetic.main.control_climate.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import java.math.BigDecimal

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class ClimateFragment : ControlFragment() {

    private var fragment: View? = null
    private var temperature: Int = 26
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(getActivity())
        fragment = act.layoutInflater.inflate(R.layout.control_climate, null)
        builder.setView(fragment)
        builder.setTitle(entity?.friendlyName)
        return builder.create()
    }
    override fun onResume() {
        super.onResume()
        ui()
    }
    private fun spinner(spinner: AppCompatSpinner, list: List<String>, selected: String?, changed: (value: String)->Unit) {
        val adapter = ArrayAdapter(getActivity(), R.layout.spinner_edittext_lookalike, list)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) { changed(list.get(position)) }
            override fun onNothingSelected(p0: AdapterView<*>?) { }
        }
        spinner.setSelection(list.indexOf(selected ?: ""))
    }
    private fun ui() {
        fragment?.apply {
            button_close.onClick { dismiss() }
            spinner(fan_speed, entity?.attributes?.fanList ?: listOf(), entity?.attributes?.fanSpeed) {
                RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_fan_speed", entity?.entityId, fanSpeed = it))
            }
            spinner(work_mode, entity?.attributes?.operationList ?: listOf(), entity?.attributes?.operationMode) {
                RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_operation_mode", entity?.entityId, operationMode = it))
            }
            spinner(swing_mode, entity?.attributes?.swingList ?: listOf(), entity?.attributes?.swingMode) {
                RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_swing_mode", entity?.entityId, swingMode = it))
            }
            text_minus.onClick {
                temperature = temperature - 1
                fragment?.text_target_state?.text = "${temperature}°C"
                RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_temperature", entity?.entityId, temperature = BigDecimal.valueOf(temperature.toLong())))
            }
            text_plus.onClick {
                temperature = temperature + 1
                fragment?.text_target_state?.text = "${temperature}°C"
                RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_temperature", entity?.entityId, temperature = BigDecimal.valueOf(temperature.toLong())))
            }
            switch_toggle.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "turn_" + if (switch_toggle.isChecked) "on" else "off", entity?.entityId)) }
        }
        refreshUi()
    }
    private fun refreshUi() {
        temperature = entity?.attributes?.temperature?.toIntOrNull() ?: 26
        fragment?.text_current_temperature?.text = "${entity?.attributes?.currentTemperature}°C"
        fragment?.text_target_state?.text = "${entity?.attributes?.temperature}°C"
        fragment?.switch_toggle?.isChecked = if ("OFF" == entity?.friendlyState?.toUpperCase()) false else true
    }
    override fun onChange() {
        refreshUi()
    }
}
