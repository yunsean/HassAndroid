package cn.com.thinkwatch.ihass2.ui

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.View
import android.view.animation.Animation
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.Period
import com.dylan.common.sketch.Animations
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
import com.yunsean.dynkotlins.extensions.kdate
import com.yunsean.dynkotlins.extensions.kdateTime
import com.yunsean.dynkotlins.extensions.nextOnMain
import kotlinx.android.synthetic.main.activity_hass_chart.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.dip
import org.jetbrains.anko.sp
import java.util.*
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

class ChartViewActivity : BaseActivity() {

    private lateinit var entityId: String
    private var entityName: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewNoTitlebar(R.layout.activity_hass_chart)
        entityId = intent.getStringExtra("entityId") ?: return finish()
        entityName = intent.getStringExtra("entityName")

        ui()
    }
    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.let {
            it.putLong("beginTime", beginTime.timeInMillis)
            it.putLong("endTime", endTime?.timeInMillis ?: 0L)
        }
        super.onSaveInstanceState(outState)
    }
    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        savedInstanceState?.getLong("beginTime", 0L)?.let {
            if (it != 0L) beginTime = Calendar.getInstance(TimeZone.getTimeZone("GMT+8")).apply { timeInMillis = it }
        }
        savedInstanceState?.getLong("endTime", 0L)?.let {
            if (it != 0L) endTime = Calendar.getInstance(TimeZone.getTimeZone("GMT+8")).apply { timeInMillis = it }
        }
        act.beginDate.tag = beginTime
        act.beginDate.text = beginTime.kdate()
        act.endDate.tag = endTime
        act.endDate.text = endTime?.kdate() ?: "现在"
        data()
    }

    private var unitOfMeasurement: String? = null
    private var beginTime = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
    private var endTime : Calendar? = null
    init { beginTime.add(Calendar.DATE, - 1) }
    private fun ui() {
        db.getEntity(entityId)?.attributes?.let {
            it.ihassDays?.let {
                val days = abs(it)
                if (days > 1) beginTime = Calendar.getInstance(TimeZone.getTimeZone("GMT+8")).apply { add(Calendar.DATE, -1 * days) }
            }
            unitOfMeasurement = it.unitOfMeasurement ?: ""
            act.entityName.text = if (entityName.isNullOrBlank()) it.friendlyName else entityName
        }

        act.back.setOnClickListener { finish() }
        act.config.setOnClickListener {
            if (act.filterPanel.visibility == View.GONE) {
                act.filterPanel.visibility = View.VISIBLE
                Animations.ScaleAnimation(act.filterPanel, 1f, 1f, 0f, 1f, 0f, 0f)
                        .duration(500)
                        .fillAfter(true)
                        .start()
                Animations.RotateAnimation(act.config, 0F, 180F, Animation.RELATIVE_TO_SELF, .5F, Animation.RELATIVE_TO_SELF, .5F)
                        .duration(500)
                        .fillAfter(true)
                        .start()
            } else {
                Animations.ScaleAnimation(act.filterPanel, 1f, 1f, 1f, 0f, 0f, 0f)
                        .duration(500)
                        .animationListener { act.filterPanel.visibility = View.GONE }
                        .fillAfter(true)
                        .start()
                Animations.RotateAnimation(act.config, 180F, 0F, Animation.RELATIVE_TO_SELF, .5F, Animation.RELATIVE_TO_SELF, .5F)
                        .duration(500)
                        .fillAfter(true)
                        .start()
            }
        }

        act.beginDate.tag = beginTime
        act.beginDate.text = beginTime.kdate()
        act.endDate.tag = endTime
        act.endDate.text = endTime?.kdate() ?: "现在"
        act.last1Day.setOnClickListener {
            setup(Calendar.getInstance(TimeZone.getTimeZone("GMT+8")).apply { add(Calendar.DATE, -1) }, null)
        }
        act.last3Days.setOnClickListener {
            setup(Calendar.getInstance(TimeZone.getTimeZone("GMT+8")).apply { add(Calendar.DATE, -3) }, null)
        }
        act.last1Week.setOnClickListener {
            setup(Calendar.getInstance(TimeZone.getTimeZone("GMT+8")).apply { add(Calendar.DATE, -7) }, null)
        }
        act.last2Weeks.setOnClickListener {
            setup(Calendar.getInstance(TimeZone.getTimeZone("GMT+8")).apply { add(Calendar.DATE, -14) }, null)
        }
        act.last1Month.setOnClickListener {
            setup(Calendar.getInstance(TimeZone.getTimeZone("GMT+8")).apply { add(Calendar.MONTH, -1) }, null)
        }
        act.beginDatePanel.setOnClickListener {
            val calendar = act.beginDate.tag as Calendar
            DatePickerDialog(ctx, DatePickerDialog.THEME_HOLO_LIGHT, { view, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                act.beginDate.text = calendar.kdate()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
                setTitle("开始日期")
                datePicker.maxDate = Calendar.getInstance(TimeZone.getTimeZone("GMT+8")).apply { add(Calendar.DATE, -1) }.timeInMillis
                show()
            }
        }
        act.endDatePanel.setOnClickListener {
            val calendar = act.endDate.tag as Calendar? ?: Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
            DatePickerDialog(ctx, DatePickerDialog.THEME_HOLO_LIGHT, { view, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                act.endDate.tag = calendar
                act.endDate.text = calendar.kdate()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
                setTitle("结束日期")
                datePicker.maxDate = System.currentTimeMillis()
                show()
            }
        }
        act.search.setOnClickListener {
            beginTime = act.beginDate.tag as Calendar
            endTime = act.endDate.tag as Calendar?
            data()
            Animations.ScaleAnimation(act.filterPanel, 1f, 1f, 1f, 0f, 0f, 0f)
                    .duration(500)
                    .animationListener { act.filterPanel.visibility = View.GONE }
                    .fillAfter(true)
                    .start()
            Animations.RotateAnimation(act.config, 180F, 0F, Animation.RELATIVE_TO_SELF, .5F, Animation.RELATIVE_TO_SELF, .5F)
                    .duration(500)
                    .fillAfter(true)
                    .start()
        }
        act.units.text = unitOfMeasurement
        data()
    }
    private fun setup(begin: Calendar, end: Calendar?) {
        act.beginDate.tag = begin
        act.beginDate.text = begin.kdate()
        act.endDate.tag = end
        act.endDate.text = end.kdate() ?: "现在"
    }

    private fun data() {
        val endDate = this.endTime ?: Calendar.getInstance()
        act.progressbar.visibility = View.VISIBLE
        app.getHistory(beginTime.kdateTime("yyyy-MM-dd'T'HH:mm:ssZZZZZ"), entityId, endDate?.kdateTime("yyyy-MM-dd'T'HH:mm:ssZZZZZ"))
                .nextOnMain {
                    val diff = endDate.timeInMillis - beginTime.timeInMillis
                    val timeFmt = if (diff > 518_400_000) "MM-dd" else if (diff > 88_000_000) "dd HH" else "HH:MM"
                    val timeName = if (diff > 518_400_000) "月-日" else if (diff > 88_000_000) "日 时" else "时:分"
                    if (it.size > 0 && it.get(0).size > 0) {
                        val periods = mutableListOf<Period>()
                        var prev: Period? = null
                        it.get(0).forEach {
                            if (it.state?.toFloatOrNull() == null) return@forEach
                            if (prev != null && it.state.equals(prev?.state)) return@forEach
                            periods.add(it)
                            prev = it
                        }
                        setupChart(periods, timeFmt, timeName)
                    } else {
                        progressbar.visibility = View.GONE
                        dataEmpty.visibility = View.VISIBLE
                        netError.visibility = View.GONE
                        chart.visibility = View.GONE
                        dateType.visibility = View.GONE
                        units.visibility = View.GONE
                    }
                }
                .error {
                    progressbar.visibility = View.GONE
                    dataEmpty.visibility = View.GONE
                    netError.visibility = View.VISIBLE
                    chart.visibility = View.GONE
                    dateType.visibility = View.GONE
                    units.visibility = View.GONE
                }
    }
    private data class DataItem(var date: Date,
                                var yValue: Float)
    private fun setupChart(histories: List<Period>, timeFmt: String, timeName: String) {
        val datas = histories.map { DataItem(it.lastChanged!!, it.state?.toFloatOrNull() ?: 0F) }
        val startTime = if (datas.isEmpty()) 0 else datas[0].date.time
        val minRate = if (datas.isEmpty()) 0F else (datas.minBy { it.yValue }?.yValue ?: 0F)
        val maxRate = if (datas.isEmpty()) 0F else (datas.maxBy { it.yValue }?.yValue ?: 0F)
        val avgRate = if (datas.isEmpty()) null else (datas.sumByDouble { it.yValue.toDouble() } / datas.size).toFloat()
        val values = datas.map { Entry((it.date.time - startTime).toFloat(), it.yValue, it) }
        val maxItem = values.maxBy { it.y }
        val minItem = values.minBy { it.y }
        val multiple = 100F / (maxRate - minRate)
        val textFmt = "%.${max(0, log10(multiple.toDouble()).toInt())}f"

        val bubble = ContextCompat.getDrawable(ctx, R.drawable.bubble)
        val bitmap = (bubble as BitmapDrawable).bitmap
        val bitmapSize = Rect(0, 0, bitmap.width, bitmap.height)
        act.chart?.apply {

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
                setLabelCount(10, true)
                setAvoidFirstLastClipping(true)
                labelRotationAngle = 0F
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
        act.apply {
            chart.visibility = View.VISIBLE
            dateType.visibility = View.VISIBLE
            units.visibility = if (unitOfMeasurement.isNullOrBlank()) View.GONE else View.VISIBLE
            progressbar.visibility = View.GONE
            dataEmpty.visibility = View.GONE
            netError.visibility = View.GONE
            dateType.text = timeName
            chart.animate()
        }
    }
    private inner class MyMarkView(context: Context?, layoutResource: Int) : MarkerView(context, layoutResource) {
        override fun refreshContent(e: Entry?, highlight: Highlight?) {
            e?.let {
                val data = e.data as DataItem
                Sketch.set_tv(this, R.id.rate, "${data.yValue.toString()}${unitOfMeasurement}")
                Sketch.set_tv(this, R.id.time, data.date.kdateTime("MM-dd HH:mm:ss"))
            }
            super.refreshContent(e, highlight)
        }
        override fun getOffsetForDrawingAtPoint(posX: Float, posY: Float): MPPointF {
            val offset = this.offset
            val chart = act?.chart ?: return MPPointF(0F, 0F)
            if (posY > height) offset.y = -height.toFloat()
            else offset.y = 0F
            if (posX > chart.width - width) offset.x = -width.toFloat()
            else offset.x = 0F
            return offset
        }
    }
}

