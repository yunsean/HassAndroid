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
import kotlinx.android.synthetic.main.control_fan.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.util.*

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class FanFragment : ControlFragment() {

    private val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
    init {
        calendar.add(Calendar.DATE, -2)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }
    private var fragment: View? = null
    private lateinit var speeds: List<String>
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        fragment = activity?.layoutInflater?.inflate(R.layout.control_fan, null)
        builder.setView(fragment)
        builder.setTitle(if (entity?.showName.isNullOrBlank()) entity?.friendlyName else entity?.showName)
        return builder.create()
    }
    override fun onResume() {
        super.onResume()
        ui()
        refreshUi()
    }
    fun getNameTitleCase(name: String?): String? {
        var name = name
        val ACTIONABLE_DELIMITERS = " '-/"
        var sb = StringBuilder()
        if (name != null && !name.isEmpty()) {
            var capitaliseNext = true
            for (c in name.toCharArray()) {
                var c = if (capitaliseNext) Character.toUpperCase(c) else Character.toLowerCase(c)
                sb.append(c)
                capitaliseNext = ACTIONABLE_DELIMITERS.indexOf(c) >= 0
            }
            name = sb.toString()
            if (name.startsWith("Mc") && name.length > 2) {
                val c = name[2]
                if (ACTIONABLE_DELIMITERS.indexOf(c) < 0) {
                    sb = StringBuilder()
                    sb.append(name.substring(0, 2))
                    sb.append(name.substring(2, 3).toUpperCase())
                    sb.append(name.substring(3))
                    name = sb.toString()
                }
            } else if (name.startsWith("Mac") && name.length > 3) {
                val c = name[3]
                if (ACTIONABLE_DELIMITERS.indexOf(c) < 0) {
                    sb = StringBuilder()
                    sb.append(name.substring(0, 3))
                    sb.append(name.substring(3, 4).toUpperCase())
                    sb.append(name.substring(4))
                    name = sb.toString()
                }
            }
        }
        return name
    }
    private fun ui() {
        speeds = entity?.attributes?.speedList?.map { getNameTitleCase(it) ?: "" } ?: listOf()
        fragment?.apply {
            useRatio.colorMap = mapOf("off" to R.color.switchOff, "on" to R.color.switchOn)
            useRatio.textMap = mapOf("off" to "关闭", "on" to "打开")
            useRatio.visibility = View.GONE
            useRatioDate.visibility = View.GONE
            button_close.onClick { dismiss() }
            text_off.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "turn_off", entity?.entityId)) }
            text_on.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "turn_on", entity?.entityId)) }
            oscillate.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "oscillate", entity?.entityId, oscillating = oscillate.isChecked)) }
            forward.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_direction", entity?.entityId, direction = "forward")) }
            reversal.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_direction", entity?.entityId, direction = "reverse")) }
            val adapter = ArrayAdapter(getActivity(), R.layout.spinner_edittext_lookalike, speeds)
            spinner_speed.adapter = adapter
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner_speed.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                    RxBus2.getDefault().post(ServiceRequest(entity?.domain, "turn_on", entity?.entityId, speed = speeds.get(position).toLowerCase()))
                }
                override fun onNothingSelected(p0: AdapterView<*>?) { }
            }
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
    }
    private val SUPPORT_SET_SPEED = 1
    private val SUPPORT_OSCILLATE = 2
    private val SUPPORT_DIRECTION = 4
    private fun refreshUi() {
        fragment?.apply {
            speed_panel.visibility = if ((entity?.attributes?.supportedFeatures ?: 0) and SUPPORT_SET_SPEED != 0) View.VISIBLE else View.GONE
            oscillate_panel.visibility = if ((entity?.attributes?.supportedFeatures ?: 0) and SUPPORT_OSCILLATE != 0) View.VISIBLE else View.GONE
            direction_panel.visibility = if ((entity?.attributes?.supportedFeatures ?: 0) and SUPPORT_DIRECTION != 0) View.VISIBLE else View.GONE
            text_on.isActivated = entity?.isActivated ?: false
            text_off.isActivated = !(entity?.isActivated ?: false)
            oscillate.isChecked = entity?.attributes?.oscillating ?: false
            forward.isActivated = entity?.attributes?.direction == "forward"
            reversal.isActivated = entity?.attributes?.direction == "reverse"
            spinner_speed.setSelection(speeds.indexOf(entity?.state))
        }
    }
    override fun onChange()  = refreshUi()
}
