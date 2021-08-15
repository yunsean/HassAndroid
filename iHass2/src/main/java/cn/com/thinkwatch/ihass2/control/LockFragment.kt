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
import kotlinx.android.synthetic.main.control_lock.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.textColor
import java.util.*

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class LockFragment : ControlFragment() {

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
        val builder = AlertDialog.Builder(getActivity())
        fragment = act.getLayoutInflater().inflate(R.layout.control_lock, null)
        builder.setView(fragment)
        builder.setTitle(if (entity?.showName.isNullOrEmpty()) entity?.friendlyName else entity?.showName)
        return builder.create()
    }
    override fun onResume() {
        super.onResume()
        ui()
        refreshUi()
    }
    private fun ui() {
        fragment?.apply {
            useRatio.colorMap = mapOf("locked" to R.color.switchOff, "unlocked" to R.color.switchOn, "unknown" to R.color.gray)
            useRatio.textMap = mapOf("locked" to "上锁", "unlocked" to "开锁", "unknown" to "未知")
            useRatio.visibility = View.GONE
            useRatioDate.visibility = View.GONE
            lock.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "lock", entity?.entityId)) }
            unlock.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "unlock", entity?.entityId)) }
            open.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "open", entity?.entityId)) }
            button_close.onClick { dismiss() }
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
    private val SUPPORT_OPEN = 1
    private fun refreshUi() {
        fragment?.apply {
            openPanel.visibility = if ((entity?.attributes?.supportedFeatures ?: 0) and SUPPORT_OPEN != 0) View.VISIBLE else View.GONE
            if ("locked" == entity?.state) {
                lock.isEnabled = false
                unlock.isEnabled = true
                lock.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_500, null)
                unlock.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_800, null)
            } else if ("unlocked" == entity?.state) {
                lock.isEnabled = true
                unlock.isEnabled = false
                lock.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_800, null)
                unlock.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_500, null)
            } else {
                lock.isEnabled = true
                unlock.isEnabled = true
                lock.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_800, null)
                unlock.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_800, null)
            }
        }
    }
    override fun onChange() = refreshUi()
}
