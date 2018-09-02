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
import com.yunsean.dynkotlins.extensions.onChanged
import com.yunsean.dynkotlins.extensions.text
import kotlinx.android.synthetic.main.control_input_text.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class InputTextFragment : ControlFragment() {
    private var fragment: View? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        fragment = activity?.layoutInflater?.inflate(R.layout.control_input_text, null)
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
            button_cancel.onClick { dismiss() }
            button_set.onClick {
                val text = edit_item.text()
                RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_value", entity?.entityId, value = text))
                dismiss()
            }
            edit_item.onChanged {
                val inputString = it.toString()
                var isValid = true
                if (entity?.attributes?.pattern != null) isValid = inputString.matches(entity?.attributes?.pattern?.toRegex()!!)
                if (isValid) isValid = inputString.length >= (entity?.attributes?.min?.toInt() ?: 1) && inputString.length <= (entity?.attributes?.max?.toInt() ?: 9999)
                button_set.isEnabled = isValid
            }
        }
    }
    private fun refreshUi() {
        fragment?.apply {
            edit_item.setText(entity?.state)
        }
    }
    override fun onChange() = refreshUi()
}
