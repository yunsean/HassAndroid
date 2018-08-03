package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.R.id.*
import cn.com.thinkwatch.ihass2.api.hassApi
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.model.Period
import cn.com.thinkwatch.ihass2.view.UseRatioView
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.control_tracker.view.*
import kotlinx.android.synthetic.main.listitem_entity_period.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.util.*

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class BinaryFragment : ControlFragment() {

    private var fragment: View? = null
    private val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
    init {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        fragment = activity?.layoutInflater?.inflate(R.layout.control_tracker, null)
        builder.setView(fragment)
        builder.setTitle(entity?.friendlyName)
        return builder.create()
    }
    private lateinit var adatper: RecyclerAdapter<Period>
    override fun onResume() {
        super.onResume()
        ui()
        refreshUi()
    }
    private fun ui() {
        fragment?.apply {
            useRatio.usedColor = 0xFF06AE5A.toInt()
            useRatio.freeColor = 0xFFFF4081.toInt()
            useRatio.usedText = "在线"
            useRatio.freeText = "离线"
            adatper = RecyclerAdapter(R.layout.listitem_entity_period, null) {
                view, index, item ->
                view.time.text = item.lastChanged.ktime()
                view.state.text = if ("on".equals(item.state)) "在线" else "离线"
            }
            recyclerView.adapter = adatper
            recyclerView.layoutManager = LinearLayoutManager(context)
            prevDay.onClick {
                calendar.add(Calendar.DATE, -1)
                refreshUi()
            }
            nextDay.onClick {
                calendar.add(Calendar.DATE, 1)
                refreshUi()
            }
            btnClose.onClick {
                dismiss()
            }
            refreshUi()
        }
    }
    private fun refreshUi() {
        fragment?.apply {
            loading.visibility = View.VISIBLE
            content.visibility = View.GONE
            day.setText(calendar.kdate())
            context.hassApi.getHistory(context.app.haPassword, calendar.kdateTime("yyyy-MM-dd'T'HH:mm:ssZZZZZ"), entity?.entityId)
                    .nextOnMain {
                        loading.visibility = View.GONE
                        content.visibility = View.VISIBLE
                        if (it.size > 0 && it.get(0).size > 0) {
                            val segments = mutableListOf<UseRatioView.Segment>()
                            val dayOfBegin = calendar.timeInMillis
                            val maxMicroSec = 24 * 3600 * 1000
                            it.get(0).forEach {
                                if (it.lastChanged == null) return@forEach
                                val offset = it.lastChanged!!.time - dayOfBegin
                                if (offset > maxMicroSec || offset < 0) return@forEach
                                segments.add(UseRatioView.Segment((offset * 100 / maxMicroSec).toInt(), it.state == "on"))
                            }
                            it.get(0).lastOrNull()?.let {
                                var end = dayOfBegin + maxMicroSec
                                if (Calendar.getInstance().timeInMillis < end) end = Calendar.getInstance().timeInMillis
                                val offset = end - dayOfBegin
                                segments.add(UseRatioView.Segment((offset * 100 / maxMicroSec).toInt(), it.state == "on"))
                            }
                            useRatio.segments = segments
                            adatper.items = it.get(0)

                        } else {
                            useRatio.segments = listOf()
                            adatper.items = listOf()
                        }
                    }
                    .error {
                        it.toastex()
                        loading.visibility = View.GONE
                        useRatio.segments = listOf()
                        adatper.items = listOf()
                    }
        }
    }
    override fun onChange() = refreshUi()
}
