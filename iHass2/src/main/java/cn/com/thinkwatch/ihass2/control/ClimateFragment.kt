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
import cn.com.thinkwatch.ihass2.api.hassApi
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.Period
import cn.com.thinkwatch.ihass2.view.UseRatioView
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.kdate
import com.yunsean.dynkotlins.extensions.kdateTime
import com.yunsean.dynkotlins.extensions.nextOnMain
import kotlinx.android.synthetic.main.control_climate.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import java.math.BigDecimal
import java.util.*

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class ClimateFragment : ControlFragment() {

    private var fragment: View? = null
    private var temperature: Int = 26
    private val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
    init {
        calendar.add(Calendar.DATE, -2)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(getActivity())
        fragment = act.layoutInflater.inflate(R.layout.control_climate, null, false)
        builder.setView(fragment)
        builder.setTitle(if (entity?.showName.isNullOrBlank()) entity?.friendlyName else entity?.showName)
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
            useRatio.colorMap = mapOf("off" to R.color.climateOff, "unavailable" to R.color.climateUnvaliable, "Cool" to R.color.climateCool,
                    "Heat" to R.color.climateHeat, "Dehumidify" to R.color.climateDehumidify, "Ventilate" to R.color.climateVentilate)
            useRatio.textMap = mapOf("off" to "关闭", "unavailable" to "未知", "Cool" to "制冷",
                    "Heat" to "制热", "Dehumidify" to "除湿", "Ventilate" to "通风")
            button_close.onClick { dismiss() }
            spinner(fan_speed, entity?.attributes?.fanList ?: listOf(), entity?.attributes?.fanSpeed) {
                if (it != entity?.attributes?.fanMode) RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_fan_mode", entity?.entityId, fanMode = it))
            }
            spinner(work_mode, entity?.attributes?.operationList ?: listOf(), entity?.attributes?.operationMode) {
                if (it != entity?.attributes?.operationMode) RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_operation_mode", entity?.entityId, operationMode = it))
            }
            spinner(swing_mode, entity?.attributes?.swingList ?: listOf(), entity?.attributes?.swingMode) {
                if (it != entity?.attributes?.swingMode) RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_swing_mode", entity?.entityId, swingMode = it))
            }
            text_minus.onClick {
                if (temperature < 16) return@onClick
                temperature = temperature - 1
                fragment?.text_target_state?.text = "${temperature}°C"
                RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_temperature", entity?.entityId, temperature = BigDecimal.valueOf(temperature.toLong())))
            }
            text_plus.onClick {
                if (temperature > 30) return@onClick
                temperature = temperature + 1
                fragment?.text_target_state?.text = "${temperature}°C"
                RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_temperature", entity?.entityId, temperature = BigDecimal.valueOf(temperature.toLong())))
            }
            switch_toggle.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "turn_" + if (switch_toggle.isChecked) "on" else "off", entity?.entityId)) }
            context.hassApi.getHistory(context.app.haPassword, calendar.kdateTime("yyyy-MM-dd'T'HH:mm:ssZZZZZ"), entity?.entityId, Calendar.getInstance().kdateTime("yyyy-MM-dd'T'HH:mm:ssZZZZZ"))
                    .nextOnMain {
                        useRatio.visibility = View.VISIBLE
                        useRatioDate.visibility = View.VISIBLE
                        beginDate.text = calendar.kdate()
                        endDate.text = Calendar.getInstance().kdate()
                        if (it.size > 0 && it.get(0).size > 0) {
                            val segments = mutableListOf<UseRatioView.Segment>()
                            val dayOfBegin = calendar.timeInMillis
                            val maxMicroSec = 3 * 24 * 3600 * 1000
                            val datas = mutableListOf<Period>()
                            val raws = it.get(0).sortedBy { it.lastUpdated }
                            raws.forEachIndexed { index, period ->
                                if (datas.size > 0 && period.state == datas.get(datas.size - 1).state) return@forEachIndexed
                                if (index < raws.size - 1 && isSimilarDate(period.lastChanged, raws[index + 1].lastChanged)) return@forEachIndexed
                                datas.add(period)
                            }
                            datas.forEach {
                                if (it.lastUpdated == null) return@forEach
                                val offset = it.lastUpdated!!.time - dayOfBegin
                                if (offset > maxMicroSec || offset < 0) return@forEach
                                segments.add(UseRatioView.Segment((offset * 100 / maxMicroSec).toInt(), it.state))
                            }
                            datas.lastOrNull()?.let {
                                var end = dayOfBegin + maxMicroSec
                                if (Calendar.getInstance().timeInMillis < end) end = Calendar.getInstance().timeInMillis
                                val offset = end - dayOfBegin
                                segments.add(UseRatioView.Segment((offset * 100 / maxMicroSec).toInt(), it.state))
                            }
                            useRatio.segments = segments
                        } else {
                            useRatio.segments = listOf()
                        }
                    }
                    .error {
                        useRatio.visibility = View.GONE
                        useRatioDate.visibility = View.GONE
                    }
        }
        refreshUi()
    }
    private fun refreshUi() {
        fragment?.apply {
            temperature = entity?.attributes?.temperature?.toIntOrNull() ?: 26
            switch_toggle?.isChecked = entity?.isActivated ?: false
            text_current_temperature?.text = "${entity?.attributes?.currentTemperature}°C"
            text_target_state?.text = "${entity?.attributes?.temperature}°C"
            state?.text = entity?.friendlyState
            fan_speed.setSelection(entity?.attributes?.fanList?.indexOf(entity?.attributes?.fanSpeed) ?: 0)
            work_mode.setSelection(entity?.attributes?.operationList?.indexOf(entity?.attributes?.operationMode) ?: 0)
            swing_mode.setSelection(entity?.attributes?.swingList?.indexOf(entity?.attributes?.swingMode) ?: 0)
        }
    }
    override fun onChange() {
        refreshUi()
    }
}
