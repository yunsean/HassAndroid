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
import java.lang.StringBuilder
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
        builder.setTitle(if (entity?.showName.isNullOrEmpty()) entity?.friendlyName else entity?.showName)
        return builder.create()
    }
    override fun onResume() {
        super.onResume()
        ui()
    }
    private fun ui() {
        fragment?.apply {
            useRatio.colorMap = mapOf("off" to R.color.vacuumOff, "unavailable" to R.color.vacuumUnvaliable, "on" to R.color.vacuumOn)
            useRatio.textMap = entity?.attributes?.ihassState ?: mapOf("docked" to "停靠", "cleaning" to "清扫", "idle" to "暂停", "unavailable" to "离线", "off" to "关机", "returning" to "回充")
            button_close.onClick { dismiss() }
            text_start.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "start", entity?.entityId)) }
            text_stop.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "stop", entity?.entityId)) }
            text_clean_spot.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "clean_spot", entity?.entityId)) }
            text_locate.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "locate", entity?.entityId)) }
            text_home.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "return_to_base", entity?.entityId)) }
            val speeds: List<String> = entity?.attributes?.fanSpeedList ?: listOf()
            val adapter = ArrayAdapter(getActivity(), R.layout.spinner_edittext_lookalike, speeds.map {
                if (SpinnerModeMap.containsKey(it.toLowerCase())) {
                    SpinnerModeMap.get(it.toLowerCase())
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
            spinner_speed.adapter = adapter
            spinner_speed.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                    RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_fan_speed", entity?.entityId, fanSpeed = speeds?.get(position)))
                }
                override fun onNothingSelected(p0: AdapterView<*>?) { }
            }
            spinner_speed.setSelection(speeds.indexOf(entity?.attributes?.fanSpeed ?: ""))
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
        refreshUi()
    }
    private fun refreshUi() {
        fragment?.apply {
            state?.setText(entity?.friendlyState)
        }
    }
    override fun onChange() {
        refreshUi()
    }

    companion object {
        private val SpinnerModeMap = mapOf("silent" to "安静", "standard" to "标准", "medium" to "中速", "turbo" to "加强", "gentle" to "温和")
    }
}
