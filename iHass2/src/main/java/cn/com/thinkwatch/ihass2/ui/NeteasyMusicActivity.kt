package cn.com.thinkwatch.ihass2.ui

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.inputmethod.EditorInfo
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.EntityChanged
import cn.com.thinkwatch.ihass2.bus.EntityUpdated
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.dto.neteasy.Mv
import cn.com.thinkwatch.ihass2.dto.neteasy.Song
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.network.external.neteasyMusicApi
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Dialogs
import com.dylan.common.utils.Utility
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_hass_neteasy_music.*
import kotlinx.android.synthetic.main.listitem_neteasy_music.view.*
import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar
import org.jetbrains.anko.act
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.math.BigDecimal


class NeteasyMusicActivity : BaseActivity() {

    private lateinit var entityId: String
    private var disposable2: Disposable? = null
    private var disposable3: Disposable? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_neteasy_music)
        setTitle(intent.getStringExtra("title") ?: "在线音乐", true)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)
        val entityId = intent.getStringExtra("entityId")
        if (entityId.isNullOrBlank()) return finish()
        this.entityId = entityId
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)
        ui()

        disposable = RxBus2.getDefault().register(EntityChanged::class.java, {
            if (it.entityId.equals(entityId)) {
                db.getEntity(entityId)?.let { onChange(it) }
            }
        }, RxBus2.getDefault().register(EntityUpdated::class.java, {
            db.getEntity(entityId)?.let { onChange(it) }
        }, disposable))
        db.getEntity(entityId)?.let { onChange(it) }
    }
    override fun onDestroy() {
        disposable2?.dispose()
        disposable3?.dispose()
        super.onDestroy()
    }
    private fun onChange(entity: JsonEntity) {
        val duration = entity.attributes?.mediaDuration
        val position = entity.attributes?.mediaPosition
        val volume = entity.attributes?.volumeLevel?.toFloat() ?: 0f
        val features = entity.attributes?.supportedFeatures ?: 0
        MDIFont.get().setIcon(this.play, if (entity.state?.toUpperCase() == "PLAYING") "mdi:pause" else "mdi:play")
        this.volume.visibility = if (features and SUPPORT_VOLUME_SET != 0) View.VISIBLE else View.GONE
        this.volume.progress = (volume * 100).toInt()
        this.current.text = entity.attributes?.mediaTitle ?: if (entity.isActivated) "无标题" else entity.friendlyState
        this.progress.visibility = if (duration != null && position != null) View.VISIBLE else View.GONE
        this.progress.isEnabled = features and SUPPORT_SEEK != 0
        this.progress.progress = (((position ?: 1.0) / (duration ?: 1.0)) * 100).toInt()
    }

    private val songs = mutableListOf<Any>()
    private lateinit var adapter: RecyclerAdapter<Any>
    private var filterRunnable: Runnable = object: Runnable {
        override fun run() {
            filter()
        }
    }
    private fun ui() {
        this.adapter = RecyclerAdapter(R.layout.listitem_neteasy_music, songs) {
            view, index, item ->
            var type = ""
            var type2 = ""
            val id = if (item is Song) {
                view.name.text = item.name
                (if (item.alias.isEmpty()) "" else item.alias.get(0)).let {
                    view.alias.text = it
                    view.alias.visibility = if (it.isBlank()) View.GONE else View.VISIBLE
                }
                item.album?.name?.let {
                    view.album.text = it
                    view.album.visibility = if (it.isBlank()) View.GONE else View.VISIBLE
                }
                view.artist.text = if (item.artists.isEmpty()) "未知艺术家" else item.artists.map { it.name }.reduce { acc, s -> acc + " " + s }
                if (!item.album?.picUrl.isNullOrBlank()) {
                    Glide.with(act)
                            .setDefaultRequestOptions(RequestOptions().placeholder(R.drawable.ic_music_note_blue_24dp))
                            .load(item.album?.picUrl ?: "")
                            .into(view.icon)
                }
                type = "music"
                type2 = "song"
                item.id
            } else if (item is Mv) {
                view.name.text = item.name
                view.alias.visibility = View.GONE
                view.album.text = item.artistName
                view.album.visibility = if (item.artistName.isBlank()) View.GONE else View.VISIBLE
                view.artist.text = if (item.artistName.isEmpty()) "未知艺术家" else item.artistName
                if (!item.cover.isBlank()) {
                    Glide.with(act)
                            .setDefaultRequestOptions(RequestOptions().placeholder(R.drawable.ic_music_note_blue_24dp))
                            .load(item.cover)
                            .into(view.icon)
                }
                type = "video"
                type2 = "mv"
                item.id
            } else {
                ""
            }
            view.onClick {
                val waiting = Dialogs.showWait(act, "请稍候...")
                disposable2?.dispose()
                neteasyMusicApi.getUrl(type2, id)
                        .withNext {
                            waiting.dismiss()
                            if (!it.data.isEmpty() && !it.data.get(0).url.isNullOrBlank()) {
                                RxBus2.getDefault().post(ServiceRequest("media_player", "play_media", entityId, mediaContentId = it.data.get(0).url, mediaContentType = type))
                            } else {
                                toastex("获取播放地址失败！")
                            }
                        }
                        .error {
                            waiting.dismiss()
                            it.toastex()
                        }
                        .subscribeOnMain {
                            disposable2 = it
                        }
            }
        }
        this.recyclerView.adapter = adapter
        this.recyclerView.layoutManager = LinearLayoutManager(this)
        this.recyclerView.addItemDecoration(RecyclerViewDivider()
                .setColor(0xffeeeeee.toInt())
                .setSize(dip2px(1f)))
        this.pullable.isRefreshEnabled = false
        this.pullable.setOnLoadMoreListener {
            loadMore()
        }
        this.keyword.onChanged {
            act.keyword.removeCallbacks(filterRunnable)
            act.keyword.postDelayed(filterRunnable, 1000)
        }
        this.keyword.setOnEditorActionListener() { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                act.keyword.removeCallbacks(filterRunnable)
                Utility.hideSoftKeyboard(act)
                filter()
            }
            false
        }
        this.play.onClick {
            db.getEntity(entityId)?.let {
                RxBus2.getDefault().post(ServiceRequest("media_player", if (it.state?.toUpperCase() == "PLAYING") "media_pause" else "media_play", entityId))
            }
        }
        this.volume.setOnProgressChangeListener(object: DiscreteSeekBar.OnProgressChangeListener {
            override fun onProgressChanged(seekBar: DiscreteSeekBar?, value: Int, fromUser: Boolean) {
            }
            override fun onStartTrackingTouch(seekBar: DiscreteSeekBar?) { }
            override fun onStopTrackingTouch(seekBar: DiscreteSeekBar?) {
                RxBus2.getDefault().post(ServiceRequest("media_player", "volume_set", entityId, volumeLevel = BigDecimal(volume.progress / 100.0)))
            }
        })
        this.songs_type.setOnCheckedChangeListener { group, checkedId ->
            filter()
        }
        this.progress.setOnProgressChangeListener(object: DiscreteSeekBar.OnProgressChangeListener {
            override fun onProgressChanged(seekBar: DiscreteSeekBar?, value: Int, fromUser: Boolean) {
            }
            override fun onStartTrackingTouch(seekBar: DiscreteSeekBar?) { }
            override fun onStopTrackingTouch(seekBar: DiscreteSeekBar?) {
                db.getEntity(entityId)?.let {
                    val position = progress.progress * (it.attributes?.mediaDuration ?: 1.0) / 100
                    RxBus2.getDefault().post(ServiceRequest("media_player", "media_seek", null, volumeLevel = BigDecimal(position)))
                }
            }
        })
        readPref("neteasy.music.keyword", "姚贝娜", "neteasy")?.let {
            if (!it.isBlank()) keyword.setText(it)
        }
    }
    private var filterKeyword = ""
    private var searchMv = false
    private fun filter() {
        val keyword = act.keyword.text()
        savePref("neteasy.music.keyword", keyword, "neteasy")
        if (keyword.isBlank()) return
        disposable3?.dispose()
        filterKeyword = keyword
        searchMv = video.isChecked
        songs.clear()
        pullable.isRefreshing = true
        loadMore()
    }
    private fun loadMore() {
        if (searchMv) {
            neteasyMusicApi.mvs(filterKeyword, songs.count())
                    .withNext {
                        it.result?.mvs?.let {
                            this.songs.addAll(it)
                        }
                        this.adapter?.notifyDataSetChanged()
                        this.pullable?.isRefreshing = false
                        this.pullable?.isLoadingMore = false
                        this.pullable?.isLoadMoreEnabled = (it.result?.mvs?.size ?: 0) > 0
                    }
                    .error {
                        it.toastex()
                    }
                    .subscribeOnMain {
                        disposable3 = it
                    }
        } else {
            neteasyMusicApi.songs(filterKeyword, songs.count())
                    .withNext {
                        it.result?.songs?.let {
                            this.songs.addAll(it)
                        }
                        this.adapter.notifyDataSetChanged()
                        this.pullable?.isRefreshing = false
                        this.pullable?.isLoadingMore = false
                        this.pullable?.isLoadMoreEnabled = (it.result?.songs?.size ?: 0) > 0
                    }
                    .error {
                        it.toastex()
                    }
                    .subscribeOnMain {
                        disposable3 = it
                    }
        }
    }
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

