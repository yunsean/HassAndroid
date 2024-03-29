package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.model.Period
import cn.com.thinkwatch.ihass2.ui.ImageViewActivity
import cn.com.thinkwatch.ihass2.view.UseRatioView
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.control_entity.view.*
import kotlinx.android.synthetic.main.listitem_entity_period.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx
import java.util.*

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class EntityFragment : ControlFragment() {

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
        fragment = activity?.layoutInflater?.inflate(R.layout.control_entity, null)
        builder.setView(fragment)
        builder.setTitle(if (entity?.showName.isNullOrEmpty()) entity?.friendlyName else entity?.showName)
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
            useRatio.textMap = entity?.attributes?.ihassState?.let { it } ?: mapOf("home" to "在家", "not_home" to "外出")
            adatper = RecyclerAdapter(R.layout.listitem_entity_period, null) {
                view, index, item ->
                view.time.text = item.lastChanged.ktime()
                view.state.text = entity?.getFriendlyState(item.state)
            }
            recyclerView.adapter = adatper
            recyclerView.layoutManager = LinearLayoutManager(context)
            prevDay.onClick {
                calendar.add(Calendar.DATE, -1)
                updateHistory()
            }
            nextDay.onClick {
                calendar.add(Calendar.DATE, 1)
                updateHistory()
            }
            btnImage.onClick {
                startActivity(Intent(ctx, ImageViewActivity::class.java).putExtra("url", entity?.attributes?.entityPicture))
            }
            btnClose.onClick {
                dismiss()
            }
            updateHistory()
        }
    }
    private fun refreshUi() {
    }
    private fun updateHistory(){
        fragment?.apply {
            day.setText(calendar.kdate())
            loading.visibility = View.VISIBLE
            content.visibility = View.GONE
            totalView.visibility = View.GONE
            btnImage.visibility = if (entity?.attributes?.entityPicture.isNullOrBlank()) View.GONE else View.VISIBLE
            app.getHistory(calendar.kdateTime("yyyy-MM-dd'T'HH:mm:ssZZZZZ"), entity?.entityId)
                    .nextOnMain {
                        val totals = entity?.attributes?.ihassTotal?.split("|")
                        val state = if (totals.isNullOrEmpty()) null else if (totals.get(0).isBlank()) null else totals.get(0)
                        val extra = (entity?.attributes?.ihassTotalExtra?.toIntOrNull() ?: 0) * 1000
                        loading.visibility = View.GONE
                        content.visibility = View.VISIBLE
                        if (it.size > 0 && it.get(0).size > 0) {
                            val segments = mutableListOf<UseRatioView.Segment>()
                            val dayOfBegin = calendar.timeInMillis
                            val maxMicroSec = 24 * 3600 * 1000
                            val datas = mutableListOf<Period>()
                            val raws = it.get(0).sortedBy { it.lastChanged }
                            var total = 0L
                            raws.forEachIndexed { index, period ->
                                if (state != null && index > 0 && raws.get(index - 1).state == state) total += (period.lastChanged?.time ?: 0) - (raws.get(index - 1).lastChanged?.time ?: 0) + extra
                                if (datas.size > 0 && period.state == datas.get(datas.size - 1).state) return@forEachIndexed
                                if (index < raws.size - 1 && isSimilarDate(period.lastChanged, raws[index + 1].lastChanged)) return@forEachIndexed
                                datas.add(period)
                            }
                            if (state != null) {
                                raws.lastOrNull()?.let {
                                    if (it.state == state) total += System.currentTimeMillis() - (it.lastChanged?.time ?: 0)
                                }
                                total /= 1000
                                val hour = total / 3600
                                total %= 3600
                                val minute = total / 60
                                total %= 60
                                totalView.text = "${entity?.getFriendlyState(state)}时长：" + String.format("%02d:%02d:%02d", hour, minute, total)
                                totalView.visibility = View.VISIBLE
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
                            adatper.items = datas
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
