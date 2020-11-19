package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.ui.MusicListActivity
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.start
import kotlinx.android.synthetic.main.control_music.view.*
import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx


@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class MusicFragment : ControlFragment() {

    private var fragment: View? = null
    private val modes = listOf("random", "order", "single")
    private val modeIcons = listOf("\uf49f", "\uf456", "\uf458")
    private var currentMode = 0
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        fragment = activity?.layoutInflater?.inflate(R.layout.control_music, null)
        builder.setView(fragment)
        builder.setTitle(if (entity?.showName.isNullOrBlank()) entity?.friendlyName else entity?.showName)
        return builder.create()
    }
    override fun onResume() {
        super.onResume()
        fragment?.apply {
            button_close.onClick {
                dismiss()
            }
            file_list.onClick {
                Intent(ctx, MusicListActivity::class.java)
                        .putExtra("entityId", entity?.entityId)
                        .putExtra("title", entity?.friendlyName)
                        .start(ctx)
            }
            volume.setOnProgressChangeListener(object: DiscreteSeekBar.OnProgressChangeListener {
                override fun onProgressChanged(seekBar: DiscreteSeekBar?, value: Int, fromUser: Boolean) {
                }
                override fun onStartTrackingTouch(seekBar: DiscreteSeekBar?) { }
                override fun onStopTrackingTouch(seekBar: DiscreteSeekBar?) {
                    RxBus2.getDefault().post(ServiceRequest("broadcast", "set_volume", null, volume = volume.progress))
                }
            })
            play.onClick {
                if (entity?.isActivated ?: false) RxBus2.getDefault().post(ServiceRequest("broadcast", "stop", null))
                else RxBus2.getDefault().post(ServiceRequest("broadcast", "play", entity?.entityId))
            }
            next.onClick {
                RxBus2.getDefault().post(ServiceRequest("broadcast", "play", entity?.entityId))
            }
            mode.onClick {
                currentMode++
                if (currentMode >= modes.size) currentMode = 0
                RxBus2.getDefault().post(ServiceRequest("broadcast", "set_mode", entity?.entityId, mode = modes[currentMode]))
            }
        }
        refreshUi()
    }
    private fun refreshUi() {
        fragment?.apply {
            play.imageResource = if (entity?.isActivated ?: false) R.drawable.ic_stop_blue_24dp else R.drawable.ic_play_arrow_blue_24dp
            volume.progress = entity?.attributes?.volume?.toInt() ?: 50
            volume_value.text = volume.progress.toString()
            var url = entity?.attributes?.url?.let {
                var index = it.lastIndexOfAny(charArrayOf('/', '\\'))
                var name = it
                if (index >= 0) name = name.substring(index + 1)
                index = name.lastIndexOf('.')
                if (index > 0) name = name.substring(0, index)
                name
            }
            if (url.isNullOrBlank()) url = "未知音频"
            if (entity?.isActivated ?: false) state.text = url
            else state.text = entity?.getFriendlyState("off")
            currentMode = modes.indexOf(entity?.attributes?.mode ?: "")
            if (currentMode < 0) currentMode = 0
            mode.setText(modeIcons[currentMode])
        }
    }
    override fun onChange() = refreshUi()
}
