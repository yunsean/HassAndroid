package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.control_radio.view.*
import kotlinx.android.synthetic.main.griditem_radio_channel.view.*
import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar
import org.jetbrains.anko.sdk25.coroutines.onClick


@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class RadioFragment : ControlFragment() {

    private var fragment: View? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        fragment = activity?.layoutInflater?.inflate(R.layout.control_radio, null)
        builder.setView(fragment)
        builder.setTitle(if (entity?.showName.isNullOrEmpty()) entity?.friendlyName else entity?.showName)
        return builder.create()
    }
    override fun onResume() {
        super.onResume()
        fragment?.apply {
            button_close.onClick {
                dismiss()
            }
            volume.setOnProgressChangeListener(object: DiscreteSeekBar.OnProgressChangeListener {
                override fun onProgressChanged(seekBar: DiscreteSeekBar?, value: Int, fromUser: Boolean) {
                }
                override fun onStartTrackingTouch(seekBar: DiscreteSeekBar?) { }
                override fun onStopTrackingTouch(seekBar: DiscreteSeekBar?) {
                    RxBus2.getDefault().post(ServiceRequest(entity?.domain, "set_volume", entity?.entityId, volume = volume.progress))
                }
            })
            prev.onClick {
                RxBus2.getDefault().post(ServiceRequest(entity?.domain, "prev_radio", entity?.entityId))
            }
            play.onClick {
                RxBus2.getDefault().post(ServiceRequest(entity?.domain, "turn_on", entity?.entityId))
            }
            stop.onClick {
                RxBus2.getDefault().post(ServiceRequest(entity?.domain, "turn_off", entity?.entityId))
            }
            next.onClick {
                RxBus2.getDefault().post(ServiceRequest(entity?.domain, "next_radio", entity?.entityId))
            }
            val channels = entity?.attributes?.channels?.map { app.xmlyChannels.get(it) }
            favorities.visibility = if (channels?.size ?: 0 > 0) View.VISIBLE else View.GONE
            val adapter = RecyclerAdapter(R.layout.griditem_radio_channel, channels) {
                view, index, channel ->
                Glide.with(context)
                        .load(channel?.conver)
                        .apply(RequestOptions().placeholder(R.drawable.radio_channel_icon))
                        .into(view.image)
                view.onClick {
                    RxBus2.getDefault().post(ServiceRequest(entity?.domain, "play_radio", entity?.entityId, programId = channel?.id))
                }
            }
            favorities.adapter = adapter
            favorities.layoutManager = GridLayoutManager(context, 6)
        }
        refreshUi()
    }
    private fun refreshUi() {
        fragment?.apply {
            volume.progress = entity?.attributes?.volume?.toInt() ?: 50
            volume_value.text = volume.progress.toString()
            state.text = if (entity?.state == "on") (app.xmlyChannels.get(entity?.attributes?.channel?.toInt() ?: 0)?.name ?: entity?.attributes?.channel?.toString() ?: "off") else "off"
        }
    }
    override fun onChange() = refreshUi()
}
