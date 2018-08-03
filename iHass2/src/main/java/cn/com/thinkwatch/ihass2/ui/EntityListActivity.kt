package cn.com.thinkwatch.ihass2.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.inputmethod.EditorInfo
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.JsonEntity
import com.dylan.common.utils.Utility
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.activity_hass_entity_list.*
import kotlinx.android.synthetic.main.listitem_entity_item.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.sdk25.coroutines.onClick


class EntityListActivity : BaseActivity() {

    private var position = -1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_entity_list)
        setTitle("元素列表", true, "完成")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)
        position = intent.getIntExtra("position", -1)

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
    private lateinit var adapter: RecyclerAdapter<JsonEntity>
    private var filterRunnable: Runnable = object: Runnable {
        override fun run() {
            filter()
        }
    }
    private fun ui() {
        this.adapter = RecyclerAdapter(R.layout.listitem_entity_item, showEntities) {
            view, index, item ->
            view.name.text = item.friendlyName
            view.icon.text = item.mdiIcon
            view.value.text = item.friendlyState
            view.checked.visibility = if (checkedEntities.contains(item)) View.VISIBLE else View.GONE
            view.onClick {
                if (checkedEntities.contains(item)) checkedEntities.remove(item)
                else checkedEntities.add(item)
                adapter.notifyDataSetChanged()
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
    }
    private fun filter() {
        val keyword = act.keyword.text()
        showEntities.clear()
        showEntities.addAll(checkedEntities)
        allEntities?.filter {
            (keyword.isBlank() or it.friendlyName.contains(keyword) or (it.state?.contains(keyword) ?: false)) and !checkedEntities.contains(it)
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

