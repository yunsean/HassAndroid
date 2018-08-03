package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import com.dylan.common.rx.RxBus2
import kotlinx.android.synthetic.main.control_input_slider.view.*
import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class InputSliderFragment : ControlFragment() {

    private var originalValue: Int = 0
    private var fragment: View? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(getActivity())
        fragment = act.getLayoutInflater().inflate(R.layout.control_input_slider, null)
        builder.setView(fragment)
        builder.setTitle(entity?.friendlyName)
        return builder.create()
    }
    override fun onResume() {
        super.onResume()
        ui()
        refreshUi()
    }
    private fun getNumberOfDecimalPlaces(step: BigDecimal?): Int {
        if (step == null) return 0
        val string = step.stripTrailingZeros().toPlainString()
        val index = string.indexOf(".")
        return if (index < 0) 0 else string.length - index - 1
    }
    private fun ui() {
        fragment?.apply {
            button_close.onClick { dismiss() }
            button_reset.onClick { discrete_data.progress = originalValue }
            val multiplyTransformer = object : DiscreteSeekBar.NumericTransformer() {
                override fun transform(value: Int): Int = value
                override fun transformToString(value: Int): String {
                    val finalValue = BigDecimal(value).multiply(entity?.attributes?.step).add(entity?.attributes?.min)
                    return String.format(Locale.ENGLISH, "%." + getNumberOfDecimalPlaces(entity?.attributes?.step) + "f", finalValue)
                }
                override fun useStringTransform(): Boolean = true
            }
            discrete_data.setNumericTransformer(multiplyTransformer)
            discrete_data.setOnProgressChangeListener(object : DiscreteSeekBar.OnProgressChangeListener {
                override fun onProgressChanged(seekBar: DiscreteSeekBar, value: Int, fromUser: Boolean) {
                    val finalValue = BigDecimal(seekBar.progress).multiply(entity?.attributes?.step).add(entity?.attributes?.min)
                    text_data.setText(String.format(Locale.ENGLISH, "%." + getNumberOfDecimalPlaces(entity?.attributes?.step) + "f", finalValue))
                    if ("input_number" == entity?.domain) RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_value", entity?.entityId, value = String.format(Locale.ENGLISH, "%." + getNumberOfDecimalPlaces(entity?.attributes?.step) + "f", finalValue)))
                }
                override fun onStartTrackingTouch(seekBar: DiscreteSeekBar) { }
                override fun onStopTrackingTouch(seekBar: DiscreteSeekBar) { }
            })
        }
        refreshUi()
    }
    private fun refreshUi() {
        fragment?.apply {
            discrete_data.min = 0
            discrete_data.max = entity?.attributes?.max?.subtract(entity?.attributes?.min)?.divide(entity?.attributes?.step, RoundingMode.HALF_UP)?.toInt() ?: 100
            originalValue = BigDecimal(entity?.state).subtract(entity?.attributes?.min).divide(entity?.attributes?.step, RoundingMode.HALF_UP).toInt()
            discrete_data.progress = originalValue
        }
    }
    override fun onChange() = refreshUi()
}
