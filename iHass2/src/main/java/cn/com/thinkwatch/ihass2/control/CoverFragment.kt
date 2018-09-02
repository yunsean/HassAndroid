package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.support.v4.content.res.ResourcesCompat
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import com.dylan.common.rx.RxBus2
import kotlinx.android.synthetic.main.control_cover.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.textColor

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class CoverFragment : ControlFragment() {
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
            text_stop.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "stop_cover", entity?.entityId)) }
            text_down.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "close_cover", entity?.entityId)) }
            text_up.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "open_cover", entity?.entityId)) }
            button_close.onClick { dismiss() }
        }        
    }
    private fun refreshUi() {
        fragment?.apply {
            text_stop.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_800, null)
            if ("open" == entity?.state) {
                text_up.isEnabled = false
                text_down.isEnabled = true
                text_up.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_500, null)
                text_down.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_800, null)
            } else if ("closed" == entity?.state) {
                text_up.isEnabled = true
                text_down.isEnabled = false
                text_up.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_800, null)
                text_down.textColor = ResourcesCompat.getColor(getResources(), R.color.md_grey_500, null)
            }
        }
    }
    override fun onChange() = refreshUi()
}
