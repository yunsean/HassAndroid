package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v7.widget.AppCompatSpinner
import android.support.v7.widget.GridLayoutManager
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.model.broadcast.Favorite
import cn.com.thinkwatch.ihass2.ui.NeteasyMusicActivity
import cn.com.thinkwatch.ihass2.ui.QtfmBroadcastActivity
import cn.com.thinkwatch.ihass2.ui.XmlyBroadcastActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.dylan.common.rx.RxBus2
import com.yunsean.dynkotlins.extensions.start
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.control_player.view.*
import kotlinx.android.synthetic.main.griditem_radio_channel.view.*
import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx
import java.math.BigDecimal


@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class PlayerFragment : ControlFragment() {

    private var fragment: View? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        fragment = activity?.layoutInflater?.inflate(R.layout.control_player, null)
        builder.setView(fragment)
        builder.setTitle(if (entity?.showName.isNullOrEmpty()) entity?.friendlyName else entity?.showName)
        return builder.create()
    }
    private fun spinner(spinner: AppCompatSpinner, list: List<String>, selected: String?, changed: (value: String)->Unit) {
        val adapter = ArrayAdapter(getActivity(), R.layout.spinner_edittext_lookalike, list)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) { changed(list.get(position)) }
            override fun onNothingSelected(p0: AdapterView<*>?) { }
        }
        spinner.setSelection(list.indexOf(selected ?: ""))
    }
    private var adapter: RecyclerAdapter<Favorite>? = null
    override fun onResume() {
        super.onResume()
        fragment?.apply {
            button_close.onClick { dismiss() }
            search.onClick {
                Intent(ctx, NeteasyMusicActivity::class.java)
                        .putExtra("entityId", entity?.entityId)
                        .putExtra("title", entity?.friendlyName)
                        .start(ctx)
            }
            xmly.onClick {
                Intent(ctx, XmlyBroadcastActivity::class.java)
                        .putExtra("entityId", entity?.entityId)
                        .putExtra("title", entity?.friendlyName)
                        .start(ctx)
            }
            qtfm.onClick {
                Intent(ctx, QtfmBroadcastActivity::class.java)
                        .putExtra("entityId", entity?.entityId)
                        .putExtra("title", entity?.friendlyName)
                        .start(ctx)
            }
            switch_toggle.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "turn_" + if (switch_toggle.isChecked) "on" else "off", entity?.entityId)) }
            prev.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "media_previous_track", entity?.entityId)) }
            next.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "media_next_track", entity?.entityId)) }
            play.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, if (entity?.state?.toUpperCase() == "PLAYING") "media_pause" else "media_play", entity?.entityId)) }
            stop.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "media_stop", entity?.entityId)) }
            volumeSub.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "volume_down", entity?.entityId)) }
            volumeAdd.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "volume_up", entity?.entityId)) }
            shuffle.onClick { RxBus2.getDefault().post(ServiceRequest(entity?.domain, "shuffle_set", entity?.entityId, shuffle = !(entity?.attributes?.shuffle ?: false))) }
            volume.setOnProgressChangeListener(object: DiscreteSeekBar.OnProgressChangeListener {
                override fun onProgressChanged(seekBar: DiscreteSeekBar?, value: Int, fromUser: Boolean) {
                }
                override fun onStartTrackingTouch(seekBar: DiscreteSeekBar?) { }
                override fun onStopTrackingTouch(seekBar: DiscreteSeekBar?) {
                    RxBus2.getDefault().post(ServiceRequest(entity?.domain, "volume_set", null, volumeLevel = BigDecimal(volume.progress / 100.0)))
                }
            })
            progress.setOnProgressChangeListener(object: DiscreteSeekBar.OnProgressChangeListener {
                override fun onProgressChanged(seekBar: DiscreteSeekBar?, value: Int, fromUser: Boolean) {
                }
                override fun onStartTrackingTouch(seekBar: DiscreteSeekBar?) { }
                override fun onStopTrackingTouch(seekBar: DiscreteSeekBar?) {
                    val position = progress.progress * (entity?.attributes?.mediaDuration ?: 1.0) / 100
                    RxBus2.getDefault().post(ServiceRequest(entity?.domain, "media_seek", null, volumeLevel = BigDecimal(position)))
                }
            })
            spinner(source, entity?.attributes?.sourceList ?: listOf(), entity?.attributes?.source) {
                if (it != entity?.attributes?.source) RxBus2.getDefault().post(ServiceRequest(entity?.domain, "select_source", entity?.entityId, source = it))
            }
            spinner(sound_mode, entity?.attributes?.soundModeList ?: listOf(), entity?.attributes?.soundMode) {
                if (it != entity?.attributes?.soundMode) RxBus2.getDefault().post(ServiceRequest(entity?.domain, "select_sound_mode", entity?.entityId, soundMode = it))
            }
            adapter = RecyclerAdapter(R.layout.griditem_radio_channel, db.getXmlyFavorite(entity?.entityId ?: "")) {
                view, index, channel ->
                Glide.with(context)
                        .load(channel.coverSmall)
                        .apply(RequestOptions().placeholder(R.drawable.radio_channel_icon))
                        .into(view.image)
                view.onClick {
                    RxBus2.getDefault().post(ServiceRequest("media_player", "play_media", entity?.entityId, mediaContentId = channel.playUrl, mediaContentType = "music"))
                }
            }
            favorities.adapter = adapter
            favorities.layoutManager = GridLayoutManager(context, 6)
        }
        refreshUi()
    }
    private fun refreshUi() {
        fragment?.apply {
            val title = entity?.attributes?.mediaTitle ?: if (entity?.isActivated ?: false) "无标题" else entity?.friendlyState
            val artist = entity?.attributes?.mediaArtist
            var album = entity?.attributes?.mediaAlbumName ?: entity?.attributes?.mediaSeriesTitle ?: ""
            val duration = entity?.attributes?.mediaDuration
            val position = entity?.attributes?.mediaPosition
            val mute = entity?.attributes?.isVolumeMuted ?: false
            val shuffle = entity?.attributes?.shuffle ?: false
            val volume = entity?.attributes?.volumeLevel?.toFloat() ?: 1f
            val features = entity?.attributes?.supportedFeatures ?: 0
            if (entity?.attributes?.mediaSeason != null || entity?.attributes?.mediaEpisode != null) {
                album += " - "
                entity?.attributes?.mediaSeason?.let { album += "S$it" }
                entity?.attributes?.mediaEpisode?.let { album += "E$it" }
            }
            this.shuffle.visibility = if (features and SUPPORT_SHUFFLE_SET != 0) View.VISIBLE else View.GONE
            MDIFont.get().setIcon(this.shuffle, if (shuffle) "mdi:shuffle" else "mdi:shuffle-disabled")
            this.progress.visibility = if (duration != null && position != null) View.VISIBLE else View.GONE
            this.progress.isEnabled = features and SUPPORT_SEEK != 0
            this.progress.progress = (((position ?: 1.0) / (duration ?: 1.0)) * 100).toInt()
            this.title.setText(title)
            this.artist.visibility = if (artist.isNullOrBlank()) View.GONE else View.VISIBLE
            this.artist.setText(artist)
            this.album.visibility = if (album.isNullOrBlank()) View.GONE else View.VISIBLE
            this.album.setText(album)
            this.mute.isActivated = mute
            this.volume.progress = (volume * 100).toInt()
            this.switch_toggle?.isChecked = entity?.isActivated ?: false
            this.prev.isEnabled = entity?.isActivated ?: false
            this.next.isEnabled = entity?.isActivated ?: false
            this.play.isEnabled = entity?.isActivated ?: false
            MDIFont.get().setIcon(this.icon, if (entity?.showIcon.isNullOrBlank()) entity?.iconState else entity?.showIcon)
            MDIFont.get().setIcon(this.play, if (entity?.state?.toUpperCase() == "PLAYING") "mdi:pause" else "mdi:play")
            this.play.visibility = if (features and SUPPORT_PAUSE != 0 && (entity?.state?.toUpperCase() == "PLAYING" || entity?.state?.toUpperCase() == "PAUSED")) View.VISIBLE else View.GONE
            this.stop.visibility = if (features and SUPPORT_STOP != 0 && entity?.state?.toUpperCase() == "PLAYING") View.VISIBLE else View.GONE
            this.prev.visibility = if (features and SUPPORT_PREVIOUS_TRACK != 0) View.VISIBLE else View.GONE
            this.next.visibility = if (features and SUPPORT_NEXT_TRACK != 0) View.VISIBLE else View.GONE
            this.mute.visibility = if (features and SUPPORT_VOLUME_MUTE != 0) View.VISIBLE else View.GONE
            this.volumeSub.visibility = if (features and SUPPORT_VOLUME_STEP != 0) View.VISIBLE else View.GONE
            this.volumeAdd.visibility = if (features and SUPPORT_VOLUME_STEP != 0) View.VISIBLE else View.GONE
            this.volume.visibility = if (features and SUPPORT_VOLUME_SET != 0) View.VISIBLE else View.GONE
            this.volume_control.visibility = if (entity?.state?.toUpperCase() == "PLAYING") View.VISIBLE else View.GONE
            this.switch_toggle.visibility = if (features and (SUPPORT_TURN_ON or SUPPORT_TURN_OFF) == (SUPPORT_TURN_ON or SUPPORT_TURN_OFF)) View.VISIBLE else View.GONE
            this.playing_control.visibility = if (entity?.state ?: "" != "OFF") View.VISIBLE else View.GONE
            this.source_panel.visibility = if ((features and SUPPORT_SELECT_SOURCE != 0) && !(entity?.attributes?.sourceList?.isEmpty() ?: true)) View.VISIBLE else View.GONE
            this.sound_mode_panel.visibility = if ((features and SUPPORT_SELECT_SOUND_MODE != 0) && !(entity?.attributes?.soundModeList?.isEmpty() ?: true)) View.VISIBLE else View.GONE
            this.source.setSelection(entity?.attributes?.sourceList?.indexOf(entity?.attributes?.source) ?: 0)
            this.sound_mode.setSelection(entity?.attributes?.soundModeList?.indexOf(entity?.attributes?.soundMode) ?: 0)
            this.search.visibility = if (features and SUPPORT_PLAY_MEDIA == 0) View.GONE else View.VISIBLE
            this.xmly.visibility = if (features and SUPPORT_PLAY_MEDIA == 0) View.GONE else View.VISIBLE
            this.qtfm.visibility = if (features and SUPPORT_PLAY_MEDIA == 0) View.GONE else View.VISIBLE
            this.favorities.visibility = if (features and SUPPORT_PLAY_MEDIA == 0) View.GONE else View.VISIBLE
        }
    }
    override fun onChange() = refreshUi()
    companion object {
        private val SUPPORT_PAUSE = 1
        private val SUPPORT_SEEK = 2
        private val SUPPORT_VOLUME_SET = 4
        private val SUPPORT_VOLUME_MUTE = 8
        private val SUPPORT_PREVIOUS_TRACK = 16
        private val SUPPORT_NEXT_TRACK = 32

        private val SUPPORT_TURN_ON = 128
        private val SUPPORT_TURN_OFF = 256
        private val SUPPORT_PLAY_MEDIA = 512
        private val SUPPORT_VOLUME_STEP = 1024
        private val SUPPORT_SELECT_SOURCE = 2048
        private val SUPPORT_STOP = 4096
        private val SUPPORT_CLEAR_PLAYLIST = 8192
        private val SUPPORT_PLAY = 16384
        private val SUPPORT_SHUFFLE_SET = 32768
        private val SUPPORT_SELECT_SOUND_MODE = 65536
    }
}
