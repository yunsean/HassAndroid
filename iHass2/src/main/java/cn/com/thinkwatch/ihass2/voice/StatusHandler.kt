package cn.com.thinkwatch.ihass2.voice

import cn.com.thinkwatch.ihass2.db.LocalStorage
import cn.com.thinkwatch.ihass2.enums.ItemType
import cn.com.thinkwatch.ihass2.model.*
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.geocode.*
import com.github.promeg.pinyinhelper.Pinyin
import com.yunsean.dynkotlins.extensions.nextOnMain
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class StatusHandler: VoiceHandler {
    private enum class WorkStage{First, Confirm, Choice}
    private data class MatchKey(val name: String,
                                val spell: String = Pinyin.toPinyin(name, "`").toLowerCase(),
                                val similar: String = LocalStorage.getSimilar(spell))
    private data class Candicate(val panel: Panel?,
                                 val entity: JsonEntity?,
                                 val dashboard: Dashboard)

    private lateinit var controller: VoiceController
    private val db by lazy { LocalStorage.instance }
    private val pattern4 by lazy { Pattern.compile(".*?(查询|报告)((.*)[的地得])?(.*)") }
    private val pattern2 by lazy { Pattern.compile("(.+?)(在哪|在家|怎么样|状态|关了|开着).*") }
    private val patternx by lazy { Pattern.compile(db.listPanel().foldIndexed(StringBuilder(".*?(查询|报告)("), {index, ss, it->
        if (index > 0) ss.append("|")
        ss.append(it.name)
    }).append(")[的地得]?(.*)").toString()) }
    private var workStage: WorkStage = WorkStage.First
    override fun setup(controller: VoiceController) {
        this.controller = controller
        this.controller.register(".*?(查询|报告)(.*)", this)
        this.controller.register("(.+?)(在哪|在家|怎么样|状态|关了|开着).*", this)
    }
    override fun handle(command: String, more: Boolean, blindly: Boolean): Boolean {
        this.blindly = blindly
        if (!more) this.workStage = WorkStage.First
        when (workStage) {
            WorkStage.First-> return matchAll(command)
            WorkStage.Confirm-> matchConfirm(command)
            WorkStage.Choice-> matchChoice(command)
        }
        return true
    }
    override fun detailClicked(item: DetailItem) {
        (item.data as Candicate?)?.let { doCommand(it) }
    }
    override fun reset() {
        this.workStage = WorkStage.First
    }
    private fun matchAll(command: String): Boolean {
        val panelKeys = mutableSetOf<MatchKey>()
        val entityKeys = mutableSetOf<MatchKey>()
        var found = false
        if (!found) {
            val m = patternx.matcher(command)
            if (m.find()) { if (!m.group(3).isNullOrBlank()) { found = true; panelKeys.add(MatchKey(m.group(2))); entityKeys.add(MatchKey(m.group(3))) }}
        }
        if (!found) {
            val m = pattern4.matcher(command)
            if (m.find()) { if (!m.group(4).isNullOrBlank()) { found = true; if (!m.group(2).isNullOrBlank()) panelKeys.add(MatchKey(m.group(3))); entityKeys.add(MatchKey(m.group(4))) }}
        }
        if (!found) {
            val m = pattern2.matcher(command)
            if (m.find()) { if (!m.group(1).isNullOrBlank()) { found = true; entityKeys.add(MatchKey(m.group(1))) }}
        }
        if (!found) return false
        val panels = db.listPanel()
        var matches = panels.filter {p-> p.name.isNotBlank() && panelKeys.find { it.name == p.name } != null }
        if (matches.size < 1) matches = panels.filter {p-> p.spell.isNotBlank() && panelKeys.find { it.spell == p.spell } != null }
        if (matches.size < 1) matches = panels.filter {p-> p.similar.isNotBlank() && panelKeys.find { it.similar == p.similar } != null }
        if (matches.size < 1) matches = panels.filter {p-> p.name.isNotBlank() && panelKeys.find { it.name.contains(p.name) || p.name.contains(it.name) } != null }
        if (matches.size < 1) matches = panels.filter {p-> p.spell.isNotBlank() && panelKeys.find { it.spell.contains(p.spell) || p.spell.contains(it.spell) } != null }
        if (matches.size < 1) matches = panels.filter {p-> p.similar.isNotBlank() && panelKeys.find { it.similar.contains(p.similar) || p.similar.contains(it.similar) } != null }
        if (matches.size < 1) matches = panels
        val dashboards = mutableListOf<Dashboard>()
        matches.forEach {  dashboards.addAll(db.listDashborad(it.id)) }
        var entities = dashboards.filter {p-> p.itemType == ItemType.entity && entityKeys.find { it.spell == p.spell } != null }
        if (entities.size < 1) entities = dashboards.filter {p-> p.itemType == ItemType.entity && entityKeys.find { it.similar == p.similar } != null }
        if (entities.size < 1) entities = dashboards.filter {p-> p.itemType == ItemType.entity && entityKeys.find { it.spell.contains(p.spell) || p.spell.contains(it.spell) } != null }
        if (entities.size < 1) entities = dashboards.filter {p-> p.itemType == ItemType.entity && entityKeys.find { it.similar.contains(p.similar) || p.similar.contains(it.similar) } != null }
        val panelMap = panels.associateBy { it.id }
        val candicates = entities.map { Candicate(panelMap.get(it.panelId), db.getEntity(it.entityId), it) }.filter { it.entity != null }
        if (candicates.size < 1) return false
        else if (candicates.size == 1) doCommand(candicates.get(0))
        else if (candicates.size > 1) showChoice(candicates)
        return true
    }
    private fun matchChoice(result: String) {
        candicates?.let { candicate ->
            val it = result.replace("的", "").replace("地", "").replace("得", "")
            val spell = Pinyin.toPinyin(it, "`").toLowerCase()
            val similar = LocalStorage.getSimilar(spell)
            var matches = candicate.filter { spell == it.panel?.spell || spell == it.dashboard.spell }
            if (matches.size < 1) matches = candicate.filter { similar == it.panel?.similar || similar == it.dashboard.similar }
            if (matches.size < 1) matches = candicate.filter { it.panel?.spell?.contains(spell) ?: false || it.dashboard.spell.contains(spell) }
            if (matches.size < 1) matches = candicate.filter { it.panel?.similar?.contains(similar) ?: false || it.dashboard.similar.contains(similar) }
            if (matches.size == 1) doCommand(matches.get(0))
            else if (matches.size > 1) showChoice(matches)
            else this.controller.setInput("我没听懂，请再说一遍", this)
        }
    }
    private fun matchConfirm(result: String) {
        if (result.contains("不") || result.contains("错")) {
            this.controller.finish(FinishAction.reset, "请重新说出你要的操作！")
        } else if (result.contains("是") || result.contains("对")) {
            this.doCommand(confirm)
        } else {
            this.controller.setInput("我没听懂，请说是或者不是", this)
        }
    }
    private var blindly: Boolean = true
    private var candicates: List<Candicate>? = null
    private var confirm: Candicate? = null
    private fun showChoice(candicates: List<Candicate>) {
        if (blindly) return doConfirm(candicates.get(0))
        val items = candicates.map {
            val name = if (it.dashboard.showName.isNullOrEmpty()) it.entity?.friendlyName else it.dashboard.showName
            val display = if (it.panel?.name.isNullOrBlank()) "查询${name}" else "查询${it.panel?.name}的${name}"
            DetailItem(display, it)
        }
        this.workStage = WorkStage.Choice
        this.controller.setInput("请选择具体的设备", this)
        this.controller.setDetail(items, this)
    }
    private fun doConfirm(candicate: Candicate) {
        val entity = candicate.entity
        if (entity == null) return controller.finish(FinishAction.reset, "未找到可控制的设备！")
        val name = if (entity.showName.isNullOrEmpty()) entity.friendlyName else entity.showName
        val command = if (candicate.panel?.name.isNullOrBlank()) "你是要查询${name}吗？"
        else "你是要查询${candicate.panel?.name}的${name}吗？"
        this.confirm = candicate
        this.workStage = WorkStage.Confirm
        this.controller.setInput(command, this)
    }
    private fun doTracker(candicate: Candicate) {
        val entity = candicate.entity
        Observable.create<String> { emitter->
            val geocoder = GeoCoder.newInstance()
            val op = ReverseGeoCodeOption()
            op.location(LatLng(entity?.attributes?.latitude?.toDouble() ?: .0, entity?.attributes?.longitude?.toDouble() ?: .0))
            geocoder.setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                override fun onGetGeoCodeResult(p0: GeoCodeResult?) { }
                override fun onGetReverseGeoCodeResult(p0: ReverseGeoCodeResult?) {
                    p0?.apply {
                        emitter.onNext(this.address)
                        emitter.onComplete()
                    }
                }
            })
            geocoder.reverseGeoCode(op)
        }.subscribeOn(Schedulers.computation())
                .timeout(3, TimeUnit.SECONDS)
                .nextOnMain {
                    doStatus(candicate, it)
                }
                .error {
                    val status = entity!!.let { if (it.isAnySensors && it.attributes?.unitOfMeasurement != null) String.format(Locale.ENGLISH, "%s %s", it.state, it.attributes?.unitOfMeasurement) else it.getFriendlyState(it.state) ?: "" }
                    doStatus(candicate, status)
                }
    }
    private fun doCommand(candicate: Candicate?) {
        if (candicate == null) return controller.finish(FinishAction.reset, "数据处理错误！")
        val entity = candicate.entity
        if (entity == null) return controller.finish(FinishAction.reset, "未找到可控制的设备！")
        if (entity.isDeviceTracker && entity.attributes?.latitude != null && entity.attributes?.longitude != null) return doTracker(candicate)
        val status = entity.let { if (it.isAnySensors && it.attributes?.unitOfMeasurement != null) String.format(Locale.ENGLISH, "%s %s", it.state, it.attributes?.unitOfMeasurement) else it.getFriendlyState(it.state) ?: "" }
        doStatus(candicate, status)
    }
    private fun doStatus(candicate: Candicate, status: String) {
        val entity = candicate.entity!!
        val name = if (candicate.dashboard.showName.isNullOrEmpty()) entity.friendlyName else candicate.dashboard.showName
        val method = if (candicate.panel?.name.isNullOrBlank()) "${name}"
        else "${candicate.panel?.name}的${name}"
        this.controller.setTips("${method}状态是：${status}", save = true)
        this.controller.setTips(status, tts = false)
        this.controller.setStatus(method)
        val details = mutableListOf<DetailItem>()
        try {
            val attributes = entity.attributes
            if (attributes?.ihassDetail != null && !attributes.ihassDetail.isNullOrBlank()) {
                LocalStorage.instance.getDbEntity(entity.entityId)?.let {
                    try {
                        JSONObject(it.rawJson).optJSONObject("attributes")?.let { attr->
                            attributes.ihassDetail.toString().trim('"').split(',').forEach {
                                it.trim().let {
                                    if (it.isBlank()) return@let
                                    val parts = it.split('!')
                                    val value = attr.optString(parts[0])
                                    if (value.isNullOrBlank()) return@let
                                    when (parts.size) {
                                        1-> details.add(DetailItem("${parts[0]}：${value}"))
                                        2-> details.add(DetailItem("${parts[1]}：${value}"))
                                        3-> details.add(DetailItem("${parts[1]}：${value}${parts[2]}"))
                                        else-> return@let
                                    }
                                }
                            }
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
                                details.add(DetailItem("${metadata.name}：${value}${metadata.unit}"))
                            }
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        this.controller.setDetail(details)
        this.controller.finish(FinishAction.halt)
    }
}