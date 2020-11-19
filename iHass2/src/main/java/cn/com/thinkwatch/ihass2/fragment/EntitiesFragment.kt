package cn.com.thinkwatch.ihass2.fragment

import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import android.view.inputmethod.EditorInfo
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.control.ControlFragment
import cn.com.thinkwatch.ihass2.control.Detail2Fragment
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.ui.EntityConfigActivity
import cn.com.thinkwatch.ihass2.utils.AddableRecylerAdapter
import cn.com.thinkwatch.ihass2.utils.SimpleItemTouchHelperCallback
import com.dylan.common.utils.Utility
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.dylan.uiparts.recyclerview.SwipeItemLayout
import com.yunsean.dynkotlins.extensions.*
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_hass_entity.*
import kotlinx.android.synthetic.main.fragment_hass_entity.loading
import kotlinx.android.synthetic.main.fragment_hass_entity.view.*
import kotlinx.android.synthetic.main.listitem_entity2_item.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx

class EntitiesFragment : BaseFragment() {

    var showBack: Boolean = false
    private var dataDisposable: Disposable? = null
    override val layoutResId: Int = R.layout.fragment_hass_entity
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment.back.visibility = if (showBack) View.VISIBLE else View.GONE
        ui()
        data()
    }
    override fun onDestroy() {
        dataDisposable?.dispose()
        super.onDestroy()
    }

    private var allEntities: MutableList<JsonEntity>? = null
    private lateinit var adapter: AddableRecylerAdapter<JsonEntity>
    private lateinit var touchHelper: ItemTouchHelper
    private var filterRunnable: Runnable = object: Runnable {
        override fun run() {
            filter()
        }
    }
    private fun ui() {
        this.back.onClick {
            act.finish()
        }
        this.adapter = AddableRecylerAdapter(R.layout.listitem_entity2_item, null) {
            view, index, item, viewHolder ->
            view.name.text = item.friendlyName
            MDIFont.get().setIcon(view.icon, item.mdiIcon)
            view.value.text = item.friendlyState
            view.entityId.text = item.entityId
            view.contentView.onClick {
                startActivity(Intent(ctx, EntityConfigActivity::class.java)
                        .putExtra("entityId", item.entityId))
            }
            view.detail.onClick {
                ControlFragment.newInstance(item, Detail2Fragment::class.java).show(childFragmentManager)
                view.root_view.close()
            }
        }
        this.recyclerView.layoutManager = LinearLayoutManager(act)
        this.recyclerView.addOnItemTouchListener(SwipeItemLayout.OnSwipeItemTouchListener(ctx))
        this.recyclerView.adapter = this.adapter
        this.recyclerView.addItemDecoration(RecyclerViewDivider()
                .setColor(0xffeeeeee.toInt())
                .setSize(act.dip2px(1f)))
        val callback = SimpleItemTouchHelperCallback(adapter, false)
        this.touchHelper = ItemTouchHelper(callback)
        this.touchHelper.attachToRecyclerView(recyclerView)
        this.pullable.setOnRefreshListener {
            data()
            pullable.isRefreshing = false
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
    }

    private fun filter() {
        val keyword = act.keyword.text()
        adapter.items = if (keyword.isNullOrBlank()) allEntities?.toMutableList()
        else allEntities?.filter { it.entityId.contains(keyword, true) or it.friendlyName.contains(keyword, true) or (it.state?.contains(keyword) ?: false) }?.toMutableList()
        adapter.notifyDataSetChanged()
    }
    private fun data() {
        this.loading.visibility = View.VISIBLE
        this.adapter.items = null
        adapter.notifyDataSetChanged()
        db.async {
            db.listEntity().sortedBy { it.friendlyName }
        }.withNext {
            allEntities = it.toMutableList()
            this.loading.visibility = View.GONE
            filter()
        }.subscribeOnMain {
            dataDisposable = it
        }
    }
}