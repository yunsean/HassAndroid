package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.MDIFont
import com.dylan.common.rx.RxBus2
import kotlinx.android.synthetic.main.control_light.view.*
import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar
import org.jetbrains.anko.sdk25.coroutines.onClick

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class LightFragment : ControlFragment() {

    private var defaultColor: Int = 0
    private var fragment: View? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        fragment = activity?.layoutInflater?.inflate(R.layout.control_light, null)
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
            button_close.onClick { dismiss() }
            button_reset.onClick { color_picker_view.setColor(defaultColor, true) }
            text_light.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, entity?.nextState, entity?.entityId)) }
            seekbar_brightness.setOnProgressChangeListener(object : DiscreteSeekBar.OnProgressChangeListener {
                override fun onProgressChanged(seekBar: DiscreteSeekBar, value: Int, fromUser: Boolean) {
                    if (seekBar.progress == 0) RxBus2.getDefault().post(ServiceRequest("light", "turn_off", entity?.entityId))
                    else RxBus2.getDefault().post(ServiceRequest("light", "turn_on", entity?.entityId, brightness = seekBar.progress))
                }
                override fun onStartTrackingTouch(seekBar: DiscreteSeekBar) { }
                override fun onStopTrackingTouch(seekBar: DiscreteSeekBar) { }
            })
            seekbar_temperature.setOnProgressChangeListener(object : DiscreteSeekBar.OnProgressChangeListener {
                override fun onProgressChanged(seekBar: DiscreteSeekBar, value: Int, fromUser: Boolean) {
                    RxBus2.getDefault().post(ServiceRequest("light", "turn_on", entity?.entityId, colorTemp = seekBar.progress))
                }
                override fun onStartTrackingTouch(seekBar: DiscreteSeekBar) { }
                override fun onStopTrackingTouch(seekBar: DiscreteSeekBar) { }
            })
        }
    }
    private fun refreshUi() {
        fragment?.apply {
            MDIFont.get().setIcon(text_light,  if (entity?.isActivated ?: false) "mdi:lightbulb" else "mdi:lightbulb-outline")
            if (entity?.attributes?.brightness != null) {
                layout_brightness.visibility = View.VISIBLE
                seekbar_brightness.progress = entity?.attributes?.brightness?.toInt() ?: 0
            } else {
                layout_brightness.visibility = View.GONE
            }
            if (entity?.attributes?.colorTemp != null) {
                layout_temperature.visibility = View.VISIBLE
                seekbar_temperature.progress = entity?.attributes?.colorTemp?.toInt() ?: 0
            } else {
                layout_temperature.visibility = View.GONE
            }
            if ((entity?.attributes?.rgbColors?.size ?: 0) >= 3) {
                entity?.attributes?.rgbColors?.let {
                    defaultColor = Color.rgb(it.get(0).toInt(), it.get(1).toInt(), it.get(2).toInt())
                    color_picker_view.setColor(defaultColor, true)
                }
            } else {
                button_reset.visibility = View.GONE
            }
        }
    }
    override fun onChange() = refreshUi()
}
