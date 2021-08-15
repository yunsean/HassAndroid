package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.bus.broadcast.FavoriteChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.broadcast.Favorite
import cn.com.thinkwatch.ihass2.ui.QtfmBroadcastActivity
import cn.com.thinkwatch.ihass2.ui.XmlyBroadcastActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.start
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.control_broadcast.view.*
import kotlinx.android.synthetic.main.griditem_radio_channel.view.*
import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx


@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class BroadcastFragment : ControlFragment() {

    private var fragment: View? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        fragment = activity?.layoutInflater?.inflate(R.layout.control_broadcast, null)
        builder.setView(fragment)
        builder.setTitle(if (entity?.showName.isNullOrEmpty()) entity?.friendlyName else entity?.showName)
        disposable = RxBus2.getDefault().register(FavoriteChanged::class.java, {
            adapter?.items = db.getXmlyFavorite(entity?.entityId ?: "")
        }, disposable)
        return builder.create()
    }
    private var adapter: RecyclerAdapter<Favorite>? = null
    override fun onResume() {
        super.onResume()
        fragment?.apply {
            button_close.onClick {
                dismiss()
            }
            button_list.onClick {
                Intent(ctx, if ("broadcast.qtfm" == entity?.entityId) QtfmBroadcastActivity::class.java else XmlyBroadcastActivity::class.java)
                        .putExtra("entityId", entity?.entityId)
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
            adapter = RecyclerAdapter(R.layout.griditem_radio_channel, db.getXmlyFavorite(entity?.entityId ?: "")) {
                view, index, channel ->
                Glide.with(context)
                        .load(channel.coverSmall)
                        .apply(RequestOptions().placeholder(R.drawable.radio_channel_icon))
                        .into(view.image)
                view.onClick {
                    RxBus2.getDefault().post(ServiceRequest("broadcast", "play", entity?.entityId, url = channel.playUrl))
                }
            }
            favorities.adapter = adapter
            favorities.layoutManager = GridLayoutManager(context, 6)
        }
        refreshUi()
    }
    private fun refreshUi() {
        fragment?.apply {
            play.imageResource = if (entity?.isActivated ?: false) R.drawable.ic_stop_blue_24dp else R.drawable.ic_play_arrow_blue_24dp
            volume.progress = entity?.attributes?.volume?.toInt() ?: 50
            volume_value.text = volume.progress.toString()
            if (entity?.isActivated ?: false) state.text = db.getXmlyCached(entity?.attributes?.url ?: "")?.name ?: "未知电台"
            else state.text = entity?.getFriendlyState("off")
        }
    }
    override fun onChange() = refreshUi()
}
