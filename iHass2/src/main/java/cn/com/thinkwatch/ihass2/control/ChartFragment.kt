package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.api.hassApi
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.model.Period
import com.yunsean.dynkotlins.extensions.kdateTime
import com.yunsean.dynkotlins.extensions.nextOnMain
import kotlinx.android.synthetic.main.control_chart.view.*
import lecho.lib.hellocharts.model.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.text.SimpleDateFormat
import java.util.*

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class ChartFragment : ControlFragment() {

    private var fragment: View? = null
    private val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
    init {
        calendar.add(Calendar.DATE, -1)
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        fragment = activity?.layoutInflater?.inflate(R.layout.control_chart, null)
        builder.setView(fragment)
        builder.setTitle(entity?.friendlyName)
        return builder.create()
    }
    override fun onResume() {
        super.onResume()
        refreshUi()
        fragment?.apply {
            btnClose.onClick { dismiss() }
        }
    }
    fun refreshUi() {
        fragment?.apply {
            progressbar.visibility = View.VISIBLE
            dataEmpty.visibility = View.GONE
            netError.visibility = View.GONE
            chart.visibility = View.GONE
            context.hassApi.getHistory(context.app.haPassword, calendar.kdateTime("yyyy-MM-dd'T'HH:mm:ssZZZZZ"), entity?.entityId)
                    .nextOnMain {
                        if (it.size > 0 && it.get(0).size > 0) {
                            val periods = mutableListOf<Period>()
                            var prev: Period? = null
                            it.get(0).forEach {
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

    private data class DataItem(var date: Date,
                                var yValue: Float) {
        var df = SimpleDateFormat("MMM-dd HH:mm", Locale.ENGLISH)
        val label: String
            get() = df.format(date)
        val xValue: Long
            get() = date.time
    }

    private fun setupChart(histories: List<Period>) {
        val datas = histories.map { DataItem(it.lastChanged!!, it.state?.toFloatOrNull() ?: 0F) }
        val values = datas.map { PointValue(it.xValue.toFloat(), it.yValue) }
        val line = Line(values).setColor(Color.parseColor("#3366cc")).setCubic(false).setHasLabels(true).setHasPoints(false).setCubic(false)
        val lines = ArrayList<Line>()
        lines.add(line)
        val data = LineChartData()
        data.setLines(lines)
        data.setLines(lines)
        val axisY = Axis().setHasLines(true)
        axisY.setName(entity?.attributes?.unitOfMeasurement)
        data.setAxisYLeft(axisY)
        val axisX: Axis
        if (datas.size != 0) {
            val startTime = datas[0].xValue
            val endTime = datas[datas.size - 1].xValue
            val step = (endTime - startTime) / 10
            val xValues = ArrayList<AxisValue>()
            var i = startTime
            while (i < endTime) {
                xValues.add(AxisValue(i.toFloat()).setLabel(Date(i).kdateTime("HH:mm")))
                i += step
            }
            xValues.add(AxisValue(endTime.toFloat()).setLabel(Date(endTime).kdateTime("HH:mm")))
            axisX = Axis(xValues)
            axisX.setName("Time (Last 24 Hours)")
        } else {
            axisX = Axis().setName("Time (Last 24 Hours)")
        }
        data.setAxisXBottom(axisX)
        fragment?.apply {
            chart.setLineChartData(data)
            chart.visibility = View.VISIBLE
            progressbar.visibility = View.GONE
            dataEmpty.visibility = View.GONE
            netError.visibility = View.GONE
        }
    }

    override fun onChange() = refreshUi()
}
