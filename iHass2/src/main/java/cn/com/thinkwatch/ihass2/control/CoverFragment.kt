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
import kotlinx.android.synthetic.main.control_cover.view.*
import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.textColor
import java.util.*

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class CoverFragment : ControlFragment() {

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
        fragment = act.getLayoutInflater().inflate(R.layout.control_cover, null)
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
            useRatio.colorMap = mapOf("closed" to R.color.switchOff, "open" to R.color.switchOn, "unknown" to R.color.gray)
            useRatio.textMap = mapOf("closed" to "关闭", "open" to "打开", "unknown" to "未知")
            useRatio.visibility = View.GONE
            useRatioDate.visibility = View.GONE
            positionStop.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "stop_cover", entity?.entityId)) }
            positionDown.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "close_cover", entity?.entityId)) }
            positionUp.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "open_cover", entity?.entityId)) }
            position.setOnProgressChangeListener(object : DiscreteSeekBar.OnProgressChangeListener {
                override fun onProgressChanged(seekBar: DiscreteSeekBar, value: Int, fromUser: Boolean) = Unit
                override fun onStartTrackingTouch(seekBar: DiscreteSeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: DiscreteSeekBar) {
                    RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_cover_position", entity?.entityId, position = seekBar.progress.toString()))
                }
            })
            tileStop.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "stop_cover_tilt", entity?.entityId)) }
            tileClose.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "close_cover_tilt", entity?.entityId)) }
            tileOpen.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "open_cover_tilt", entity?.entityId)) }
            tile.setOnProgressChangeListener(object : DiscreteSeekBar.OnProgressChangeListener {
                override fun onProgressChanged(seekBar: DiscreteSeekBar, value: Int, fromUser: Boolean) = Unit
                override fun onStartTrackingTouch(seekBar: DiscreteSeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: DiscreteSeekBar) {
                    RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_cover_tilt_position", entity?.entityId, tiltPosition = seekBar.progress.toString()))
                }
            })
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
    private val SUPPORT_CLOSE = 2
    private val SUPPORT_SET_POSITION = 4
    private val SUPPORT_STOP = 8
    private val SUPPORT_OPEN_TILT = 16
    private val SUPPORT_CLOSE_TILT = 32
    private val SUPPORT_STOP_TILT = 64
    private val SUPPORT_SET_TILT_POSITION = 128
    private val SUPPORT_POSITION = (SUPPORT_OPEN or SUPPORT_CLOSE or SUPPORT_STOP)
    private val SUPPORT_TILT = (SUPPORT_OPEN_TILT or SUPPORT_CLOSE_TILT or SUPPORT_SET_TILT_POSITION)
    private fun refreshUi() {
        fragment?.apply {
            positionStop.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_800, null)
            position.progress = entity?.attributes?.currentPosition?.toInt() ?: 0
            tile.progress = entity?.attributes?.currentTiltPosition?.toInt() ?: 0
            positionUp.visibility = if ((entity?.attributes?.supportedFeatures ?: 0) and SUPPORT_OPEN != 0) View.VISIBLE else View.GONE
            positionDown.visibility = if ((entity?.attributes?.supportedFeatures ?: 0) and SUPPORT_CLOSE != 0) View.VISIBLE else View.GONE
            positionStop.visibility = if ((entity?.attributes?.supportedFeatures ?: 0) and SUPPORT_STOP != 0) View.VISIBLE else View.GONE
            position.visibility = if ((entity?.attributes?.supportedFeatures ?: 0) and SUPPORT_SET_POSITION != 0) View.VISIBLE else View.GONE
            tileOpen.visibility = if ((entity?.attributes?.supportedFeatures ?: 0) and SUPPORT_OPEN_TILT != 0) View.VISIBLE else View.GONE
            tileClose.visibility = if ((entity?.attributes?.supportedFeatures ?: 0) and SUPPORT_CLOSE_TILT != 0) View.VISIBLE else View.GONE
            tileStop.visibility = if ((entity?.attributes?.supportedFeatures ?: 0) and SUPPORT_STOP_TILT != 0) View.VISIBLE else View.GONE
            tile.visibility = if ((entity?.attributes?.supportedFeatures ?: 0) and SUPPORT_SET_TILT_POSITION != 0) View.VISIBLE else View.GONE
            positionPanel.visibility = if ((entity?.attributes?.supportedFeatures ?: 0) and SUPPORT_POSITION != 0) View.VISIBLE else View.GONE
            tilePanel.visibility = if ((entity?.attributes?.supportedFeatures ?: 0) and SUPPORT_TILT != 0) View.VISIBLE else View.GONE
            if (entity?.attributes?.currentPosition == null) {
                if ("open" == entity?.state) {
                    positionUp.isEnabled = false
                    positionDown.isEnabled = true
                    positionUp.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_500, null)
                    positionDown.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_800, null)
                } else if ("closed" == entity?.state) {
                    positionUp.isEnabled = true
                    positionDown.isEnabled = false
                    positionUp.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_800, null)
                    positionDown.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_500, null)
                } else {
                    positionUp.isEnabled = true
                    positionDown.isEnabled = true
                    positionUp.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_800, null)
                    positionDown.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_800, null)
                }
            } else {
                if (entity?.attributes?.currentPosition?.toInt() ?: 0 > 0) {
                    positionDown.isEnabled = true
                    positionDown.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_800, null)
                } else {
                    positionDown.isEnabled = false
                    positionDown.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_500, null)
                }
                if (entity?.attributes?.currentPosition?.toInt() ?: 0 < 100) {
                    positionUp.isEnabled = true
                    positionUp.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_800, null)
                } else {
                    positionUp.isEnabled = false
                    positionUp.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_500, null)
                }
            }
            if (entity?.attributes?.currentTiltPosition?.toInt() ?: 0 > 0) {
                tileClose.isEnabled = true
                tileClose.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_800, null)
            } else {
                tileClose.isEnabled = false
                tileClose.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_500, null)
            }
            if (entity?.attributes?.currentTiltPosition?.toInt() ?: 0 < 100) {
                tileOpen.isEnabled = true
                tileOpen.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_800, null)
            } else {
                tileOpen.isEnabled = false
                tileOpen.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_500, null)
            }
        }
    }
    override fun onChange() = refreshUi()
}
