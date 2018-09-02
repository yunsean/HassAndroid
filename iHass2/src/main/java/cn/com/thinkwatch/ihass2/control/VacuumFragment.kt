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
import cn.com.thinkwatch.ihass2.api.hassApi
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.Period
import cn.com.thinkwatch.ihass2.view.UseRatioView
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.kdate
import com.yunsean.dynkotlins.extensions.kdateTime
import com.yunsean.dynkotlins.extensions.nextOnMain
import kotlinx.android.synthetic.main.control_vacuum.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import java.util.*

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class VacuumFragment : ControlFragment() {

    private var fragment: View? = null
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
        fragment = act.layoutInflater.inflate(R.layout.control_vacuum, null)
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
            useRatio.colorMap = mapOf("off" to R.color.vacuumOff, "unavailable" to R.color.vacuumUnvaliable, "on" to R.color.vacuumOn)
            useRatio.textMap = mapOf("off" to "关闭", "unavailable" to "未知", "vacuum" to "清扫")
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
        fragment?.apply {
            switch_toggle?.isChecked = if ("OFF" == entity?.friendlyState?.toUpperCase()) false else true
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
                            val raws = it.get(0).sortedBy { it.lastChanged }
                            raws.forEachIndexed { index, period ->
                                if (datas.size > 0 && period.state == datas.get(datas.size - 1).state) return@forEachIndexed
                                if (index < raws.size - 1 && isSimilarDate(period.lastChanged, raws[index + 1].lastChanged)) return@forEachIndexed
                                datas.add(period)
                            }
                            datas.forEach {
                                if (it.lastChanged == null) return@forEach
                                val offset = it.lastChanged!!.time - dayOfBegin
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
    }
    override fun onChange() {
        refreshUi()
    }
}
