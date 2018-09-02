package cn.com.thinkwatch.ihass2.control

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.bus.ChoosePanel
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.Panel
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.nextOnMain
import com.yunsean.dynkotlins.extensions.toastex
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.control_panels.view.*
import kotlinx.android.synthetic.main.listitem_panel_item.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx

class PanelsFragment : ControlFragment() {
    private var fragment: View? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        fragment = activity?.layoutInflater?.inflate(R.layout.control_panels, null)
        builder.setView(fragment)
        builder.setTitle(if (entity?.showName.isNullOrBlank()) entity?.friendlyName else entity?.showName)
        return builder.create()
    }
    override fun onResume() {
        super.onResume()
        ui()
        data()
    }
    override fun onChange() { }

    private lateinit var adapter: RecyclerAdapter<Panel>
    private fun ui() {
        this.adapter = RecyclerAdapter(R.layout.listitem_panel_item, null) {
            view, index, item ->
            view.name.text = item.name
            view.icon.visibility = View.GONE
            view.onClick {
                RxBus2.getDefault().post(ChoosePanel(item))
                dismiss()
            }
        }
        fragment?.apply {
            recyclerView.adapter = adapter
            recyclerView.layoutManager = GridLayoutManager(ctx, 4)
        }
    }
    private fun data() {
        db.async {
            db.listPanel()
        }
                .nextOnMain {
                    adapter.items = it
                }
                .error {
                    it.printStackTrace()
                    ctx.toastex(it.message ?: "未知错误")
                    dismiss()
                }
    }
}