package cn.com.thinkwatch.ihass2.ui

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.inputmethod.EditorInfo
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.EntityChanged
import cn.com.thinkwatch.ihass2.bus.EntityUpdated
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.network.BaseApi
import cn.com.thinkwatch.ihass2.network.http.HttpRestApi
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.common.rx.RxBus2
import com.dylan.common.utils.Utility
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_hass_music_list.*
import kotlinx.android.synthetic.main.listitem_music_item.view.*
import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar
import org.jetbrains.anko.act
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.sdk25.coroutines.onClick


class MusicListActivity : BaseActivity() {

    private lateinit var entityId: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithLoadable(R.layout.activity_hass_music_list)
        setTitle(intent.getStringExtra("title") ?: "音频列表", true, "刷新")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)
        val entityId = intent.getStringExtra("entityId")
        if (entityId.isNullOrBlank()) return finish()
        this.entityId = entityId

        ui()
        data()

        disposable = RxBus2.getDefault().register(EntityChanged::class.java, {
            if (it.entityId.equals(entityId)) {
                db.getEntity(entityId)?.let { onChange(it) }
            }
        }, RxBus2.getDefault().register(EntityUpdated::class.java, {
            db.getEntity(entityId)?.let { onChange(it) }
        }, disposable))
        db.getEntity(entityId)?.let { onChange(it) }
    }
    override fun doRight() {
        RxBus2.getDefault().post(ServiceRequest("broadcast", "reload", entityId))
        loadable?.showLoading()
        loadable?.postDelayed({ data() }, 1000)
    }
    private fun onChange(entity: JsonEntity) {
        this.play.imageResource = if (entity.isActivated) R.drawable.ic_stop_blue_24dp else R.drawable.ic_play_arrow_blue_24dp
        this.volume.progress = entity.attributes?.volume?.toInt() ?: 50
        this.volume_value.text = volume.progress.toString()
        var url = entity.attributes?.url?.let {
            var index = it.lastIndexOfAny(charArrayOf('/', '\\'))
            var name = it
            if (index >= 0) name = name.substring(index + 1)
            index = name.lastIndexOf('.')
            if (index > 0) name = name.substring(0, index)
            name
        }
        if (url.isNullOrBlank()) url = "未知音频"
        if (entity.isActivated) this.current.setText(url)
        else this.current.setText(entity.getFriendlyState("off"))
    }

    private var allFiles: List<String>? = null
    private lateinit var adapter: RecyclerAdapter<String>
    private var filterRunnable: Runnable = object: Runnable {
        override fun run() {
            filter()
        }
    }
    private fun ui() {
        this.adapter = RecyclerAdapter(R.layout.listitem_music_item, allFiles) {
            view, index, item ->
            var index = item.lastIndexOfAny(charArrayOf('/', '\\'))
            var name = item
            if (index >= 0) name = name.substring(index + 1)
            index = name.lastIndexOf('.')
            if (index > 0) name = name.substring(0, index)
            view.name.text = name
            view.onClick {
                RxBus2.getDefault().post(ServiceRequest("broadcast", "play", entityId, url = item, volume = volume.progress))
            }
        }
        this.recyclerView.adapter = adapter
        this.recyclerView.layoutManager = LinearLayoutManager(this)
        this.recyclerView.addItemDecoration(RecyclerViewDivider()
                .setColor(0xffeeeeee.toInt())
                .setSize(dip2px(1f)))
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
            RxBus2.getDefault().post(ServiceRequest("broadcast", "toggle", null))
        }
        this.volume.setOnProgressChangeListener(object: DiscreteSeekBar.OnProgressChangeListener {
            override fun onProgressChanged(seekBar: DiscreteSeekBar?, value: Int, fromUser: Boolean) {
            }
            override fun onStartTrackingTouch(seekBar: DiscreteSeekBar?) { }
            override fun onStopTrackingTouch(seekBar: DiscreteSeekBar?) {
                volume_value.setText(volume.progress.toString())
                RxBus2.getDefault().post(ServiceRequest("broadcast", "set_volume", null, volume = volume.progress))
            }
        })
    }
    private fun filter() {
        val keyword = act.keyword.text()
        adapter.items = allFiles?.filter { it.contains(keyword) }
    }

    private fun data() {
        BaseApi.jsonApi(cfg.haHostUrl, HttpRestApi::class.java)
                .getBroadcastFiles(cfg.haPassword, cfg.haToken, this.entityId)
                .withNext {
                    this.allFiles = it
                    this.adapter.items = it
                    this.loadable?.dismissLoading()
                }
                .error {
                    it.toastex()
                    finish()
                }
                .subscribeOnMain {
                    if (disposable == null) disposable = CompositeDisposable(it)
                    else disposable?.add(it)
                }
    }
}

