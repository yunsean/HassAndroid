package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.support.v4.content.res.ResourcesCompat
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.Period
import cn.com.thinkwatch.ihass2.view.UseRatioView
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.kdate
import com.yunsean.dynkotlins.extensions.kdateTime
import com.yunsean.dynkotlins.extensions.nextOnMain
import kotlinx.android.synthetic.main.control_switch.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.textColor
import java.util.*

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class SwitchFragment : ControlFragment() {

    private val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
    init {
        calendar.add(Calendar.DATE, -2)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }
    private var fragment: View? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        fragment = activity?.layoutInflater?.inflate(R.layout.control_switch, null)
        builder.setView(fragment)
        builder.setTitle(if (entity?.showName.isNullOrBlank()) entity?.friendlyName else entity?.showName)
        return builder.create()
    }
    override fun onResume() {
        super.onResume()
        ui()
        refreshUi()
    }
    private fun ui() {
        fragment?.apply {
            useRatio.colorMap = mapOf("off" to R.color.switchOff, "on" to R.color.switchOn)
            useRatio.textMap = mapOf("off" to "关闭", "on" to "打开")
            useRatio.visibility = View.GONE
            useRatioDate.visibility = View.GONE
            textOn.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "turn_on", entity?.entityId)) }
            textOff.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "turn_off", entity?.entityId)) }
            btnClose.onClick { dismiss() }
            if (entity?.attributes?.isStateful ?: false) {
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
    }
    private fun refreshUi() {
        fragment?.apply {
            val isActive = entity?.isCurrentStateActive ?: false
            textOn.textColor = ResourcesCompat.getColor(resources, if (isActive) R.color.primary else R.color.md_grey_500, null)
            textOff.textColor = ResourcesCompat.getColor(resources, if (isActive) R.color.md_grey_500 else R.color.primary, null)
        }
    }
    override fun onChange() = refreshUi()
}
