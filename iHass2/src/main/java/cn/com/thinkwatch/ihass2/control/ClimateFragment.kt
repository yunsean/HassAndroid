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
import java.lang.StringBuilder
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
        builder.setTitle(if (entity?.showName.isNullOrEmpty()) entity?.friendlyName else entity?.showName)
        return builder.create()
    }
    override fun onResume() {
        super.onResume()
        ui()
    }
    private fun spinner(spinner: AppCompatSpinner, list: List<String>, nameMap: Map<String, String>?, selected: String?, changed: (value: String)->Unit) {
        val adapter = ArrayAdapter(getActivity(), R.layout.spinner_edittext_lookalike, list.map {
            if (nameMap != null && nameMap.containsKey(it.toLowerCase())) {
                nameMap.get(it.toLowerCase())
            } else {
                val text = StringBuilder()
                var newString = true
                for (i in 0 until it.length) {
                    val ch = it.get(i)
                    if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch >= '0' && ch <= '9') {
                        if (newString) text.append(ch.toUpperCase())
                        else text.append(ch)
                        newString = false
                    } else {
                        newString = true
                    }
                }
                text.toString()
            }
        })
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
            useRatio.textMap = WorkModeMap
            button_close.onClick { dismiss() }
            spinner(fan_speed, entity?.attributes?.fanModes ?: listOf(), FanModeMap, entity?.attributes?.fanMode) {
                if (it != entity?.attributes?.fanMode) RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_fan_mode", entity?.entityId, fanMode = it))
            }
            spinner(work_mode, entity?.attributes?.hvacModes ?: listOf(), WorkModeMap, entity?.attributes?.hvacMode) {
                if (it != entity?.attributes?.hvacMode) RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_hvac_mode", entity?.entityId, hvacMode = it))
            }
            spinner(swing_mode, entity?.attributes?.swingModes ?: listOf(), null, entity?.attributes?.swingMode) {
                if (it != entity?.attributes?.swingMode) RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_swing_mode", entity?.entityId, swingMode = it))
            }
            text_minus.onClick {
                if (temperature <= entity?.attributes?.minTemp?.toInt() ?: 16) return@onClick
                temperature = temperature - 1
                fragment?.text_target_state?.text = "${temperature}°C"
                RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_temperature", entity?.entityId, temperature = BigDecimal.valueOf(temperature.toLong())))
            }
            text_plus.onClick {
                if (temperature >= entity?.attributes?.maxTemp?.toInt() ?: 30) return@onClick
                temperature = temperature + 1
                fragment?.text_target_state?.text = "${temperature}°C"
                RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_temperature", entity?.entityId, temperature = BigDecimal.valueOf(temperature.toLong())))
            }
            aux_heat.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_aux_heat", entity?.entityId, auxHeat = aux_heat.isChecked)) }
            away_mode.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_away_mode", entity?.entityId, awayMode = away_mode.isChecked)) }
            switch_toggle.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "turn_" + if (switch_toggle.isChecked) "on" else "off", entity?.entityId)) }
            app.getHistory(calendar.kdateTime("yyyy-MM-dd'T'HH:mm:ssZZZZZ"), entity?.entityId, Calendar.getInstance().kdateTime("yyyy-MM-dd'T'HH:mm:ssZZZZZ"))
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
    private val SUPPORT_SWING_MODE = 512
    private val SUPPORT_AWAY_MODE = 1024
    private val SUPPORT_AUX_HEAT = 2048
    private fun refreshUi() {
        fragment?.apply {
            temperature = entity?.attributes?.temperature?.toIntOrNull() ?: 26
            switch_toggle?.isChecked = entity?.isActivated ?: false
            aux_heat.isChecked = entity?.attributes?.auxHeat ?: false
            away_mode.isChecked = entity?.attributes?.awayMode ?: false
            swing_mode_panel.visibility = if ((entity?.attributes?.supportedFeatures ?: 0) and SUPPORT_SWING_MODE != 0) View.VISIBLE else View.GONE
            aux_heat_panel.visibility = if ((entity?.attributes?.supportedFeatures ?: 0) and SUPPORT_AUX_HEAT != 0) View.VISIBLE else View.GONE
            away_mode_panel.visibility = if ((entity?.attributes?.supportedFeatures ?: 0) and SUPPORT_AWAY_MODE != 0) View.VISIBLE else View.GONE
            text_current_temperature?.text = "${entity?.attributes?.currentTemperature}°C"
            text_target_state?.text = "${entity?.attributes?.temperature}°C"
            state?.text = entity?.friendlyState
        }
    }
    override fun onChange() {
        refreshUi()
    }

    companion object {
        private val WorkModeMap = mapOf("off" to "关闭", "heat" to "制热", "cool" to "制冷", "heat_cool" to "冷热自动",
                "unavailable" to "未知", "auto" to "自动", "dry" to "除湿", "fan_only" to "送风",
                "dehumidify" to "除湿", "ventilate" to "通风", "heating" to "制热", "cooling" to "制冷",
                "drying" to "除湿", "idle" to "空闲", "fan" to "送风")
        private val FanModeMap = mapOf("auto" to "自动", "low" to "低速", "mediumlow" to "中低速", "medium" to "中速",
                "mediumhigh" to "中高速", "high" to "高速", "quiet" to "静音")

    }
}
