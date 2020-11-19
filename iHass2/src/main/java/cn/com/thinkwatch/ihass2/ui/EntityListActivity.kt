package cn.com.thinkwatch.ihass2.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import android.view.inputmethod.EditorInfo
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.utils.AddableRecylerAdapter
import cn.com.thinkwatch.ihass2.utils.SimpleItemTouchHelperCallback
import com.dylan.common.utils.Utility
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.dylan.uiparts.recyclerview.SwipeItemLayout
import com.yunsean.dynkotlins.extensions.*
import kotlinx.android.synthetic.main.activity_hass_entity_list.*
import kotlinx.android.synthetic.main.listitem_entity_picker_item.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx


class EntityListActivity : BaseActivity() {

    private var position = -1
    private var singleOnly = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_entity_list)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)
        position = intent.getIntExtra("position", -1)
        singleOnly = intent.getBooleanExtra("singleOnly", false)
        if (singleOnly) setTitle("元素列表", true)
        else setTitle("元素列表", true, "完成")

        val filter = intent.getStringExtra("filter")
        act.keyword.setText(filter)
        ui()
        data()
    }
    override fun doRight() {
        if (checkedEntities.size < 1) return showError("请选择要添加的元素！")
        setResult(Activity.RESULT_OK, Intent()
                .putExtra("entityIds", checkedEntities.map { it.entityId }.toTypedArray())
                .putExtra("position", position))
        finish()
    }

    private var showEntities = mutableListOf<JsonEntity>()
    private var checkedEntities = mutableListOf<JsonEntity>()
    private var allEntities: List<JsonEntity>? = null
    private lateinit var adapter: AddableRecylerAdapter<JsonEntity>
    private lateinit var touchHelper: ItemTouchHelper
    private var filterRunnable: Runnable = object: Runnable {
        override fun run() {
            filter()
        }
    }
    private fun ui() {
        this.adapter = AddableRecylerAdapter(R.layout.listitem_entity_picker_item, showEntities) {
            view, index, item, viewHolder ->
            view.name.text = item.friendlyName
            MDIFont.get().setIcon(view.icon, item.mdiIcon)
            view.value.text = item.friendlyState
            view.entityId.text = item.entityId
            view.checked.visibility = if (checkedEntities.contains(item)) View.VISIBLE else View.GONE
            view.contentView.onClick {
                if (singleOnly) {
                    checkedEntities.clear()
                    checkedEntities.add(item)
                    adapter.notifyDataSetChanged()
                    doRight()
                } else {
                    if (checkedEntities.contains(item)) checkedEntities.remove(item)
                    else checkedEntities.add(item)
                    adapter.notifyDataSetChanged()
                }
            }
            view.edit.onClick {
                startActivity(Intent(ctx, EntityConfigActivity::class.java)
                        .putExtra("entityId", item.entityId))
            }
        }
        this.recyclerView.adapter = adapter
        this.recyclerView.layoutManager = LinearLayoutManager(this)
        this.recyclerView.addOnItemTouchListener(SwipeItemLayout.OnSwipeItemTouchListener(ctx))
        this.recyclerView.addItemDecoration(RecyclerViewDivider()
                .setColor(0xffeeeeee.toInt())
                .setSize(dip2px(1f)))
        val callback = SimpleItemTouchHelperCallback(adapter, false)
        this.touchHelper = ItemTouchHelper(callback)
        this.touchHelper.attachToRecyclerView(recyclerView)
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
        showEntities.clear()
        showEntities.addAll(checkedEntities)
        allEntities?.filter {
            (keyword.isBlank() or it.entityId.contains(keyword, true) or it.friendlyName.contains(keyword, true) or (it.state?.contains(keyword) ?: false)) and !checkedEntities.contains(it)
        }?.let {
            showEntities.addAll(it)
        }
        adapter.notifyDataSetChanged()
    }

    private fun data() {
        db.async {
            db.listEntity()
        }.nextOnMain {
            allEntities = it
            loading?.visibility = View.GONE
            filter()
        }.error {
            it.printStackTrace()
            toastex(it.message ?: "未知错误")
            finish()
        }
    }
}

