package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.support.v4.content.res.ResourcesCompat
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import com.dylan.common.rx.RxBus2
import kotlinx.android.synthetic.main.control_fan.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.textColor

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class FanFragment : ControlFragment(), AdapterView.OnItemSelectedListener {

    private var fragment: View? = null
    private lateinit var speeds: List<String>
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        fragment = activity?.layoutInflater?.inflate(R.layout.control_fan, null)
        builder.setView(fragment)
        builder.setTitle(entity?.friendlyName)
        return builder.create()
    }
    override fun onResume() {
        super.onResume()
        ui()
        refreshUi()
    }
    fun getNameTitleCase(name: String?): String? {
        var name = name
        val ACTIONABLE_DELIMITERS = " '-/"
        var sb = StringBuilder()
        if (name != null && !name.isEmpty()) {
            var capitaliseNext = true
            for (c in name.toCharArray()) {
                var c = if (capitaliseNext) Character.toUpperCase(c) else Character.toLowerCase(c)
                sb.append(c)
                capitaliseNext = ACTIONABLE_DELIMITERS.indexOf(c) >= 0
            }
            name = sb.toString()
            if (name.startsWith("Mc") && name.length > 2) {
                val c = name[2]
                if (ACTIONABLE_DELIMITERS.indexOf(c) < 0) {
                    sb = StringBuilder()
                    sb.append(name.substring(0, 2))
                    sb.append(name.substring(2, 3).toUpperCase())
                    sb.append(name.substring(3))
                    name = sb.toString()
                }
            } else if (name.startsWith("Mac") && name.length > 3) {
                val c = name[3]
                if (ACTIONABLE_DELIMITERS.indexOf(c) < 0) {
                    sb = StringBuilder()
                    sb.append(name.substring(0, 3))
                    sb.append(name.substring(3, 4).toUpperCase())
                    sb.append(name.substring(4))
                    name = sb.toString()
                }
            }
        }
        return name
    }
    private fun ui() {
        speeds = entity?.attributes?.speedList?.map { getNameTitleCase(it) ?: "" } ?: listOf()
        fragment?.apply {
            button_close.onClick { dismiss() }
            text_off.onClick { RxBus2.getDefault().post(ServiceRequest("homeassistant", "turn_off", entity?.entityId)) }
            text_on.onClick { RxBus2.getDefault().post(ServiceRequest("homeassistant", "turn_on", entity?.entityId)) }
            val adapter = ArrayAdapter(getActivity(), R.layout.spinner_edittext_lookalike, speeds)
            spinner_speed.adapter = adapter
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner_speed.setOnItemSelectedListener(this@FanFragment)
        }
    }
    private fun refreshUi() {
        fragment?.apply {
            if (entity?.isCurrentStateActive ?: false) {
                text_on.textColor = ResourcesCompat.getColor(getResources(), R.color.primary, null)
                text_off.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_500, null)
            } else {
                text_off.textColor = ResourcesCompat.getColor(getResources(), R.color.primary, null)
                text_on.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_500, null)
            }
            spinner_speed.setOnItemSelectedListener(null)
            spinner_speed.setSelection(speeds.indexOf(entity?.state))
            spinner_speed.setOnItemSelectedListener(this@FanFragment)
        }
    }
    override fun onChange()  = refreshUi()

    override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        RxBus2.getDefault().post(ServiceRequest(entity?.domain, "turn_on", entity?.entityId, speed = speeds.get(position).toLowerCase()))
    }
    override fun onNothingSelected(adapterView: AdapterView<*>) { }
}
