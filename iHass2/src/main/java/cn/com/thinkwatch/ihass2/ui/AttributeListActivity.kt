package cn.com.thinkwatch.ihass2.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.activity_hass_attribute_list.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.json.JSONObject
import kotlinx.android.synthetic.main.listitem_hass_attribute_item.view.*


class AttributeListActivity : BaseActivity() {

    private var entityId: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_attribute_list)
        setTitle("属性选择", true)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val entityId = intent.getStringExtra("entityId")
        if (entityId.isNullOrBlank()) {
            startActivityForResult(Intent(ctx, EntityListActivity::class.java)
                    .putExtra("singleOnly", true), 105)
        } else {
            loadAttribute(entityId)
        }

        ui()
    }

    private data class Attribute(val name: String,
                                 val value: String?)
    private var selectedAttribute: Attribute? = null
    private lateinit var adapter: RecyclerAdapter<Attribute>
    private fun ui() {
        act.entity.onClick {
            startActivityForResult(Intent(ctx, EntityListActivity::class.java)
                    .putExtra("singleOnly", true), 105)
        }
        this.adapter = RecyclerAdapter(R.layout.listitem_hass_attribute_item, null) {
            view, index, item ->
            view.name.text = "${item.name}：${item.value ?: ""}"
            view.checked.visibility = if (item == selectedAttribute) View.VISIBLE else View.GONE
            view.onClick {
                selectedAttribute = item
                adapter.notifyDataSetChanged()
                setResult(Activity.RESULT_OK, Intent().putExtra("entityId", entityId)
                        .putExtra("attribute", item.name)
                        .putExtra("value", item.value))
                finish()
            }
        }
        act.attributes.layoutManager = LinearLayoutManager(ctx)
        act.attributes.adapter = adapter
        act.attributes.addItemDecoration(RecyclerViewDivider()
                .setColor(0xffeeeeee.toInt())
                .setSize(1))
    }

    @ActivityResult(requestCode = 105)
    private fun afterAddEntity(data: Intent?) {
        val entityIds = data?.getStringArrayExtra("entityIds")
        if (entityIds == null || entityIds.size < 1) return
        entityIds.get(0)?.let { loadAttribute(it) }
    }

    private fun loadAttribute(entityId: String) {
        this.entityId = entityId
        if (!entityId.isNullOrBlank()) {
            db.getEntity(entityId)?.let { act.entity.text = it.friendlyName }
            db.getDbEntity(entityId)?.let {
                JSONObject(it.rawJson).optJSONObject("attributes")?.let {
                    val iterator = it.keys()
                    val attributes = mutableListOf<Attribute>()
                    while (iterator.hasNext()) {
                        val key = iterator.next()
                        val value = it.optString(key)
                        attributes.add(Attribute(key, value))
                    }
                    adapter.items = attributes
                }
            }
        }
    }
}

