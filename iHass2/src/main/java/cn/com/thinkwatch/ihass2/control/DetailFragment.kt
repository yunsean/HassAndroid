package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.Attribute
import cn.com.thinkwatch.ihass2.model.AttributeRender
import cn.com.thinkwatch.ihass2.model.Metadata
import cn.com.thinkwatch.ihass2.ui.ImageViewActivity
import cn.com.thinkwatch.ihass2.utils.ZonedDateAsTime
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.geocode.*
import com.yunsean.dynkotlins.extensions.kdateTime
import com.yunsean.dynkotlins.extensions.nextOnMain
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.control_detail.view.*
import kotlinx.android.synthetic.main.listitem_sensor_attribute.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.sp
import org.jetbrains.anko.support.v4.ctx
import org.json.JSONObject
import java.util.*

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class DetailFragment : ControlFragment() {

    private var fragment: View? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        fragment = activity?.layoutInflater?.inflate(R.layout.control_detail, null)
        builder.setView(fragment)
        builder.setTitle(if (entity?.showName.isNullOrEmpty()) entity?.friendlyName else entity?.showName)
        return builder.create()
    }
    private var adatper: RecyclerAdapter<AttributeItem>? = null
    private var totalItems: MutableMap<String, String>? = null
    private var address: String? = null
    override fun onResume() {
        super.onResume()
        fragment?.apply {
            adatper = RecyclerAdapter(R.layout.listitem_sensor_attribute, null) {
                view, index, item ->
                val spannableString = SpannableStringBuilder()
                spannableString.append(item.name);
                spannableString.append("：")
                spannableString.append(item.value)
                spannableString.setSpan(ForegroundColorSpan(Color.parseColor("#ff333333")), 0, item.name.length + 1, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
                spannableString.setSpan(ForegroundColorSpan(Color.parseColor("#ff777777")), item.name.length + 1, item.name.length + 1 + item.value.length, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
                spannableString.setSpan(AbsoluteSizeSpan(sp(14), false), 0, item.name.length + 1 + item.value.length, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
                view.value.setText(spannableString)
            }
            recyclerView.adapter = adatper
            recyclerView.layoutManager = LinearLayoutManager(context)
            btnClose.onClick {
                dismiss()
            }
            btnImage.onClick {
                startActivity(Intent(ctx, ImageViewActivity::class.java).putExtra("url", entity?.attributes?.entityPicture))
            }
            refreshUi()
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            entity?.attributes?.ihassTotal?.let {states->
                val totals = states.split("|").associate { Pair(it, 0L) }.toMutableMap()
                val extra = (entity?.attributes?.ihassTotalExtra?.toIntOrNull() ?: 0) * 1000
                app.getHistory(calendar.kdateTime("yyyy-MM-dd'T'HH:mm:ssZZZZZ"), entity?.entityId)
                        .nextOnMain {
                            if (it.size > 0 && it.get(0).size > 0) {
                                val raws = it.get(0).sortedBy { it.lastChanged }
                                raws.forEachIndexed { index, period ->
                                    if (index > 0) {
                                        totals.forEach { (t, u) -> if (raws.get(index - 1).state == t) totals.put(t, u + (period.lastChanged?.time ?: 0) - (raws.get(index - 1).lastChanged?.time ?: 0) + extra) }
                                    }
                                }
                                raws.lastOrNull()?.let {
                                    totals.forEach { (t, u) -> if (it.state == t) totals.put(t, u + System.currentTimeMillis() - (it.lastChanged?.time ?: 0)) }
                                }
                                totalItems = mutableMapOf()
                                totals.forEach { (t, u) ->
                                    var total = u
                                    total /= 1000
                                    val hour = total / 3600
                                    total %= 3600
                                    val minute = total / 60
                                    total %= 60
                                    totalItems?.put("${entity?.getFriendlyState(t)}时长", String.format("%02d:%02d:%02d", hour, minute, total))
                                }
                                refreshUi()
                            }
                        }
            }
            if (entity?.attributes?.longitude != null && entity?.attributes?.latitude != null) {
                val geocoder = GeoCoder.newInstance()
                val op = ReverseGeoCodeOption()
                op.location(LatLng(entity?.attributes?.latitude?.toDouble() ?: .0, entity?.attributes?.longitude?.toDouble() ?: .0))
                geocoder.setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                    override fun onGetGeoCodeResult(p0: GeoCodeResult?) { }
                    override fun onGetReverseGeoCodeResult(p0: ReverseGeoCodeResult?) {
                        p0?.apply {
                            this@DetailFragment.address = address
                            refreshUi()
                        }
                    }
                })
                geocoder.reverseGeoCode(op)
            }
        }
    }
    private data class AttributeItem(val name: String,
                                    val value: String)
    private fun refreshUi() {
        val attributes = entity?.attributes
        val items = mutableListOf(AttributeItem("最后更新", ZonedDateAsTime().render(entity?.lastUpdated, "yyyy-MM-dd HH:mm:ss")),
                AttributeItem("最后变更", ZonedDateAsTime().render(entity?.lastChanged, "yyyy-MM-dd HH:mm:ss")))
        totalItems?.forEach { (t, u) -> items.add(AttributeItem(t, u)) }
        if (address != null) items.add(AttributeItem("当前位置", address!!))
        fragment?.btnImage?.visibility = if (entity?.attributes?.entityPicture.isNullOrBlank()) View.GONE else View.VISIBLE
        if (attributes?.ihassDetail != null && !attributes.ihassDetail.isNullOrBlank()) {
            db.getDbEntity(entity?.entityId!!)?.let {
                try {
                    JSONObject(it.rawJson).optJSONObject("attributes")?.let { attr->
                        val details = attributes.ihassDetail.toString().trim('"').split(',')
                        val showAll = details.find { it.trim() == "*" } != null
                        val attrValues = mutableMapOf<String, String>()
                        if (showAll) attr.keys().forEach { if (!it.startsWith("ihass")) attrValues.set(it, attr.optString(it) ?: "") }
                        details.forEach {
                            it.trim().let {
                                if (it.isBlank()) return@let
                                val parts = it.split('!')
                                val value = attr.optString(parts[0])
                                if (value.isNullOrBlank()) return@let
                                when (parts.size) {
                                    1-> items.add(AttributeItem(parts[0], value))
                                    2-> items.add(AttributeItem(parts[1], value))
                                    3-> items.add(AttributeItem(parts[1], value + parts[2]))
                                    else-> return@let
                                }
                                if (showAll) attrValues.remove(parts[0])
                            }
                        }
                        if (showAll) items.addAll(attrValues.map { AttributeItem(it.key, it.value) })
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        } else if (attributes != null) {
            Attribute::class.java.declaredFields.forEach {
                val metadata = it.getAnnotation(Metadata::class.java)
                try {
                    if (metadata != null) {
                        it.isAccessible = true
                        var value: Any? = it.get(attributes)
                        if (value != null) {
                            if (metadata.display.isNotBlank()) {
                                val clazz = Class.forName(metadata.display)
                                if (clazz != null) {
                                    val obj = clazz.newInstance()
                                    if (obj is AttributeRender) value = obj.render(value)
                                }
                            }
                            items.add(AttributeItem(metadata.name, value.toString() + metadata.unit))
                        }
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
        adatper?.items = items
        fragment?.state?.text = entity?.friendlyStateRow
    }
    override fun onChange() = refreshUi()
}
