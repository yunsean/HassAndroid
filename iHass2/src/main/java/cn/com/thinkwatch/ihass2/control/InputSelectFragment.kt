package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import com.dylan.common.rx.RxBus2

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class InputSelectFragment : ControlFragment() {

    private var fragment: View? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(getActivity())
        val adapter = ArrayAdapter(getActivity(), R.layout.listitem_control, entity?.attributes?.options)
        builder.setSingleChoiceItems(adapter, -1, DialogInterface.OnClickListener { dialog, which ->
            dialog.dismiss()
            val result = entity?.attributes?.options?.get(which)
            RxBus2.getDefault().post(ServiceRequest(entity?.domain, "select_option", entity?.entityId, option = result))
        })
        builder.setTitle(entity?.friendlyName)
        return builder.create()
    }
    override fun onChange() {

    }
}
