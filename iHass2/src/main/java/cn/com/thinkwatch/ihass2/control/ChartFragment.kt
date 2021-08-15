package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.model.Period
import cn.com.thinkwatch.ihass2.ui.ChartViewActivity
import com.dylan.common.sketch.Sketch
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.renderer.LineChartRenderer
import com.github.mikephil.charting.utils.MPPointF
import com.yunsean.dynkotlins.extensions.kdateTime
import com.yunsean.dynkotlins.extensions.ktime
import com.yunsean.dynkotlins.extensions.nextOnMain
import kotlinx.android.synthetic.main.control_chart.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.dip
import org.jetbrains.anko.support.v4.sp
import java.util.*
import kotlin.math.max
import kotlin.math.min

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
        builder.setTitle(if (entity?.showName.isNullOrEmpty()) entity?.friendlyName else entity?.showName)
        return builder.create()
    }
    override fun onResume() {
        super.onResume()
        refreshUi()
        fragment?.apply {
            btnClose.onClick { dismiss() }
            btnFull.onClick {  startActivity(Intent(ctx, ChartViewActivity::class.java).putExtra("entityId", entity?.entityId).putExtra("entityName", if (entity?.showName.isNullOrEmpty()) entity?.friendlyName else entity?.showName)) }
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
        val startTime = if (datas.isEmpty()) 0 else datas[0].date.time
        val minRate = if (datas.isEmpty()) 0F else (datas.minBy { it.yValue }?.yValue ?: 0F)
        val maxRate = if (datas.isEmpty()) 0F else (datas.maxBy { it.yValue }?.yValue ?: 0F)
        val avgRate = if (datas.isEmpty()) null else (datas.sumByDouble { it.yValue.toDouble() } / datas.size).toFloat()
        val values = datas.mapIndexed { index, it -> Entry((it.date.time - startTime).toFloat(), it.yValue, it) }
        val maxItem = values.maxBy { it.y }
        val minItem = values.minBy { it.y }
        val mltiple = 100F / (maxRate - minRate)
        val textFmt ="%.${max(0, Math.log10(mltiple.toDouble()).toInt())}f"

        val bubble = ContextCompat.getDrawable(ctx, R.drawable.bubble)
        val bitmap = (bubble as BitmapDrawable).bitmap
        val bitmapSize = Rect(0, 0, bitmap.width, bitmap.height)
        fragment?.chart?.apply {

            setNoDataText("暂无数据")
            isScaleYEnabled = false
            isScaleXEnabled = true
            animateX(500)
            isAutoScaleMinMaxEnabled = false
            renderer = object : LineChartRenderer(this, this.animator, this.viewPortHandler) {
                override fun drawValues(c: Canvas?) {
                    if (c == null) return
                    if (maxItem != null) drawValue(c, maxItem, Color.RED, 0x77d81e06, false)
                    if (minItem != null) drawValue(c, minItem, Color.BLUE, 0x770799ff, true)
                }
                private fun drawValue(c: Canvas, entry: Entry, color: Int, fillColor: Int, showBelow: Boolean) {
                    val dataSet = mChart.lineData.getDataSetByIndex(0) as LineDataSet
                    val point = mChart.getTransformer(dataSet.axisDependency).getPixelForValues(entry.x, entry.y)
                    val bounds = Rect()
                    val text = String.format(textFmt, entry.y)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    val fontSize = min(12, 48 / text.length)
                    paint.textSize = sp(fontSize).toFloat()
                    paint.color = Color.WHITE
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.getTextBounds(text, 0, text.length, bounds)
                    val bubbleSize = dip(13).toFloat()
                    val rectF = RectF(point.x.toFloat() - bubbleSize, point.y.toFloat() - bubbleSize - bubbleSize, point.x.toFloat() + bubbleSize, point.y.toFloat())
                    c.drawBitmap(bitmap, bitmapSize, rectF, paint)
                    c.drawText(text, rectF.left + (rectF.width() - bounds.width()) / 2, rectF.top + bounds.height() + bubbleSize / 2, paint)
                }
            }

            marker = MyMarkView(context, R.layout.chart_marker)
            description.apply {
                text = ""
                setPosition(sp(20).toFloat(), sp(18).toFloat())
                textAlign = Paint.Align.LEFT
                textSize = 1F
            }
            legend.apply {
                form = Legend.LegendForm.NONE
            }
            axisLeft.apply {
                setDrawAxisLine(true)
                setDrawGridLines(true)
                setDrawTopYLabelEntry(false)
                axisMinimum = minRate - (maxRate - minRate) * .1F
                axisMaximum = maxRate + (maxRate - minRate) * .1F
                labelCount = 6
                removeAllLimitLines()
                if (avgRate != null) addLimitLine(LimitLine(avgRate, String.format(textFmt, avgRate)).apply {
                    lineColor = Color.RED
                    lineWidth = 1f
                    textColor = Color.RED
                    textSize = 12f
                    enableDashedLine(dip(5).toFloat(), dip(5).toFloat(), 0f)
                })
            }
            axisRight.apply {
                setDrawAxisLine(false)
                setDrawGridLines(false)
                setDrawLabels(false)
                setDrawTopYLabelEntry(false)
                setLabelCount(0, false)
            }

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawAxisLine(true)
                setDrawGridLines(false)
                setLabelCount(6, true)
                setAvoidFirstLastClipping(true)
                labelRotationAngle = -45F
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return (value + startTime).toLong().kdateTime(timeFmt) ?: ""
                    }
                }
            }

            val dataSets = listOf(LineDataSet(values, ""))
            dataSets.forEach { dataSet ->
                dataSet.lineWidth = dip(1).toFloat()
                dataSet.highLightColor = Color.RED
                dataSet.setDrawValues(true)
                dataSet.valueTextColor = Color.RED
                dataSet.valueTextSize = 12f
                dataSet.setDrawCircleHole(false)
                dataSet.setDrawCircles(false)
                dataSet.circleColors = listOf(0xffff5555.toInt())
                dataSet.circleRadius = dip(1).toFloat() / 2
                dataSet.mode = LineDataSet.Mode.HORIZONTAL_BEZIER
                dataSet.color = Color.RED
                dataSet.setDrawFilled(true)
                dataSet.fillAlpha = 30
                dataSet.fillColor = Color.RED
                dataSet.setDrawHighlightIndicators(true)
            }
            isHighlightPerTapEnabled = true
            data = LineData(dataSets)
            fitScreen()
            notifyDataSetChanged()
            invalidate()
        }
        fragment?.apply {
            chart.visibility = View.VISIBLE
            progressbar.visibility = View.GONE
            dataEmpty.visibility = View.GONE
            netError.visibility = View.GONE
            chart.animate()
        }
    }
    private inner class MyMarkView(context: Context?, layoutResource: Int) : MarkerView(context, layoutResource) {
        override fun refreshContent(e: Entry?, highlight: Highlight?) {
            e?.let {
                val data = e.data as DataItem
                Sketch.set_tv(this, R.id.rate, data.yValue.toString())
                Sketch.set_tv(this, R.id.time, data.date.ktime())
            }
            super.refreshContent(e, highlight)
        }
        override fun getOffsetForDrawingAtPoint(posX: Float, posY: Float): MPPointF {
            val offset = this.offset
            val chart = fragment?.chart ?: return MPPointF(0F, 0F)
            if (posY > height) offset.y = -height.toFloat()
            else offset.y = 0F
            if (posX > chart.width - width) offset.x = -width.toFloat()
            else offset.x = 0F
            return offset
        }
    }

    override fun onChange() = Unit
}
