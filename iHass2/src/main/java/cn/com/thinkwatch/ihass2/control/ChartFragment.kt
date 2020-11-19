package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.model.Period
import com.yunsean.dynkotlins.extensions.kdateTime
import com.yunsean.dynkotlins.extensions.nextOnMain
import kotlinx.android.synthetic.main.control_chart.view.*
import lecho.lib.hellocharts.gesture.ZoomType
import lecho.lib.hellocharts.model.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.text.SimpleDateFormat
import java.util.*

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class ChartFragment : ControlFragment() {

    private var fragment: View? = null
    private var calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
    private var timeFmt = "HH:mm"
    private var timeCount = 10
    private var timeName = "时间"
    init { calendar.add(Calendar.DATE, -1) }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        fragment = activity?.layoutInflater?.inflate(R.layout.control_chart, null)
        builder.setView(fragment)
        builder.setTitle(if (entity?.showName.isNullOrBlank()) entity?.friendlyName else entity?.showName)
        return builder.create()
    }
    override fun onResume() {
        super.onResume()
        refreshUi()
        fragment?.apply {
            btnClose.onClick { dismiss() }
            var endTime: String? = null
            entity?.attributes?.ihassDays?.let {
                val days = Math.abs(it)
                if (days > 5) {
                    calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
                    endTime = calendar.kdateTime("yyyy-MM-dd'T'HH:mm:ssZZZZZ")
                    timeCount = days
                    calendar.add(Calendar.DATE, -1 * days)
                    timeFmt = "MM-dd"
                    timeName = "月-日"
                } else if (days > 1) {
                    calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
                    endTime = calendar.kdateTime("yyyy-MM-dd'T'HH:mm:ssZZZZZ")
                    timeCount = Math.max(10, days * 3)
                    calendar.add(Calendar.DATE, -1 * days)
                    timeFmt = "dd HH"
                    timeName = "日 时"
                } else {
                    timeFmt = "HH:mm"
                }
            }
            app.getHistory(calendar.kdateTime("yyyy-MM-dd'T'HH:mm:ssZZZZZ"), entity?.entityId, endTime)
                    .nextOnMain {
                        if (it.size > 0 && it.get(0).size > 0) {
                            val periods = mutableListOf<Period>()
                            var prev: Period? = null
                            it.get(0).forEach {
                                if (it.state?.toFloatOrNull() == null) return@forEach
                                if (prev != null && it.state.equals(prev?.state)) return@forEach
                                periods.add(it)
                                prev = it
                            }
                            setupChart(periods)
                        } else {
                            progressbar.visibility = View.GONE
                            dataEmpty.visibility = View.VISIBLE
                            netError.visibility = View.GONE
                            chart.visibility = View.GONE
                        }
                    }
                    .error {
                        progressbar.visibility = View.GONE
                        dataEmpty.visibility = View.GONE
                        netError.visibility = View.VISIBLE
                        chart.visibility = View.GONE
                    }
        }
    }
    fun refreshUi() {
        fragment?.apply {
            progressbar.visibility = View.VISIBLE
            dataEmpty.visibility = View.GONE
            netError.visibility = View.GONE
            chart.visibility = View.GONE
        }
    }

    private data class DataItem(var date: Date,
                                var yValue: Float)

    private fun setupChart(histories: List<Period>) {
        val datas = histories.map { DataItem(it.lastChanged!!, it.state?.toFloatOrNull() ?: 0F) }
        val values = datas.map { PointValue(it.date.time.toFloat(), it.yValue) }
        val line = Line(values).setColor(Color.parseColor("#3366cc")).setCubic(false).setHasLabels(true).setHasPoints(false).setCubic(false)
        val lines = ArrayList<Line>()
        lines.add(line)
        val data = LineChartData()
        data.setLines(lines)
        val axisY = Axis().setHasLines(true)
        axisY.setName(entity?.attributes?.unitOfMeasurement)
        data.setAxisYLeft(axisY)
        val axisX: Axis
        if (datas.size > 0) {
            val startTime = datas[0].date.time
            val endTime = datas[datas.size - 1].date.time
            val step = (endTime - startTime) / 10
            val xValues = ArrayList<AxisValue>()
            var i = startTime
            while (i < endTime) {
                xValues.add(AxisValue(i.toFloat()).setLabel(Date(i).kdateTime(timeFmt)))
                i += step
            }
            xValues.add(AxisValue(endTime.toFloat()).setLabel(Date(endTime).kdateTime(timeFmt)))
            axisX = Axis(xValues)
            axisX.setName(timeName)
        } else if (datas.size != 0) {
            val startTime = datas[0].date.time
            val endTime = datas[datas.size - 1].date.time
            val step = (endTime - startTime) / timeCount
            val xValues = ArrayList<AxisValue>()
            var i = startTime
            while (i < endTime) {
                xValues.add(AxisValue(i.toFloat()).setLabel(Date(i).kdateTime(timeFmt)))
                i += step
            }
            xValues.add(AxisValue(endTime.toFloat()).setLabel(Date(endTime).kdateTime(timeFmt)))
            axisX = Axis(xValues)
            axisX.setName(timeName)
        } else {
            axisX = Axis().setName(timeName)
        }
        data.setAxisXBottom(axisX)
        fragment?.apply {
            chart.setLineChartData(data)
            chart.isInteractive = true
            chart.zoomType = ZoomType.HORIZONTAL
            chart.isValueSelectionEnabled = true
            chart.visibility = View.VISIBLE
            progressbar.visibility = View.GONE
            dataEmpty.visibility = View.GONE
            netError.visibility = View.GONE
        }
    }

    override fun onChange() = Unit
}
