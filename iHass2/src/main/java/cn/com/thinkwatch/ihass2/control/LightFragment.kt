package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.support.v7.widget.AppCompatSpinner
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.MDIFont
import com.dylan.common.rx.RxBus2
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.OnSettingDialogListener
import com.yunsean.dynkotlins.extensions.showDialog
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import kotlinx.android.synthetic.main.control_light.view.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import kotlinx.android.synthetic.main.listitem_textview.view.*
import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar
import org.jetbrains.anko.act
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class LightFragment : ControlFragment() {

    private var defaultColor: Int = 0
    private var fragment: View? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        fragment = activity?.layoutInflater?.inflate(R.layout.control_light, null)
        builder.setView(fragment)
        builder.setTitle(if (entity?.showName.isNullOrEmpty()) entity?.friendlyName else entity?.showName)
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
            text_light.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, entity?.nextState, entity?.entityId)) }
            seekbar_brightness.setOnProgressChangeListener(object : DiscreteSeekBar.OnProgressChangeListener {
                override fun onProgressChanged(seekBar: DiscreteSeekBar, value: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(seekBar: DiscreteSeekBar) { }
                override fun onStopTrackingTouch(seekBar: DiscreteSeekBar) {
                    if (seekBar.progress == 0) RxBus2.getDefault().post(ServiceRequest("light", "turn_off", entity?.entityId))
                    else RxBus2.getDefault().post(ServiceRequest("light", "turn_on", entity?.entityId, brightness = seekBar.progress))
                }
            })
            seekbar_temperature.setOnProgressChangeListener(object : DiscreteSeekBar.OnProgressChangeListener {
                override fun onProgressChanged(seekBar: DiscreteSeekBar, value: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(seekBar: DiscreteSeekBar) { }
                override fun onStopTrackingTouch(seekBar: DiscreteSeekBar) {
                    RxBus2.getDefault().post(ServiceRequest("light", "turn_on", entity?.entityId, colorTemp = seekBar.progress))
                }
            })
            seekbar_whitevalue.setOnProgressChangeListener(object : DiscreteSeekBar.OnProgressChangeListener {
                override fun onProgressChanged(seekBar: DiscreteSeekBar, value: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(seekBar: DiscreteSeekBar) { }
                override fun onStopTrackingTouch(seekBar: DiscreteSeekBar) {
                    RxBus2.getDefault().post(ServiceRequest("light", "turn_on", entity?.entityId, whiteValue = seekBar.progress))
                }
            })
            color_picker_view.addOnColorChangedListener {
                val red = (it shr 16) and 0xff
                val green = (it shr 8) and 0xff
                val blue = it and 0x000000ff
                RxBus2.getDefault().post(ServiceRequest("light", "turn_on", entity?.entityId, rgbColor = intArrayOf(red, green, blue)))
            }
            effect_list.visibility = View.GONE
            entity?.attributes?.effectList?.let { list->
                effect_list.visibility = View.VISIBLE
                effect_list.onClick {
                    ctx.showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
                        override fun onSettingDialog(dialog: Dialog, contentView: View) {
                            contentView.recyclerView.layoutManager = LinearLayoutManager(ctx)
                            val adaptar = RecyclerAdapter(R.layout.listitem_textview_small, list) {
                                view, index, item ->
                                view.text.text = item
                                view.onClick {
                                    effect.text = item
                                    dialog.dismiss()
                                    RxBus2.getDefault().post(ServiceRequest("light", "turn_on", entity?.entityId, effect = item))
                                }
                            }
                            val footer = layoutInflater.inflate(R.layout.layout_cancel, contentView.recyclerView, false)
                            footer.onClick { dialog.dismiss() }
                            contentView.recyclerView.adapter = RecyclerAdapterWrapper(adaptar)
                                    .addFootView(footer)
                            contentView.recyclerView.addItemDecoration(RecyclerViewDivider()
                                    .setColor(0xffeeeeee.toInt())
                                    .setSize(1))
                        }
                    }, null, null)
                }
            }
        }
    }
    private fun refreshUi() {
        fragment?.apply {
            MDIFont.get().setIcon(text_light,  if (entity?.isActivated ?: false) "mdi:lightbulb" else "mdi:lightbulb-outline")
            if (entity?.attributes?.brightness != null) {
                seekbar_brightness.progress = entity?.attributes?.brightness?.toInt() ?: 0
            }
            if (entity?.attributes?.colorTemp != null) {
                layout_temperature.visibility = View.VISIBLE
                seekbar_temperature.progress = entity?.attributes?.colorTemp?.toInt() ?: 0
            } else {
                layout_temperature.visibility = View.GONE
            }
            if (entity?.attributes?.whiteValue != null) {
                layout_whitevalue.visibility = View.VISIBLE
                seekbar_whitevalue.progress = entity?.attributes?.whiteValue?.toInt() ?: 0
            } else {
                layout_whitevalue.visibility = View.GONE
            }
            if ((entity?.attributes?.rgbColors?.size ?: 0) >= 3) {
                color_picker_view.visibility = View.VISIBLE
                entity?.attributes?.rgbColors?.let {
                    defaultColor = Color.rgb(it.get(0).toInt(), it.get(1).toInt(), it.get(2).toInt())
                    color_picker_view.setColor(defaultColor, true)
                }
            } else {
                color_picker_view.visibility = View.GONE
            }
            entity?.attributes?.effectList?.let {
                val value = entity?.attributes?.effect ?: ""
                if (it.contains(value)) effect.text = value
                else effect.text = "效果"
            }
        }
    }
    override fun onChange() = refreshUi()
}
