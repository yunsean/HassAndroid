package cn.com.thinkwatch.ihass2.voice

import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.db.LocalStorage
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.enums.ItemType
import cn.com.thinkwatch.ihass2.model.Dashboard
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.Panel
import cn.com.thinkwatch.ihass2.network.base.ws
import com.github.promeg.pinyinhelper.Pinyin
import com.yunsean.dynkotlins.extensions.withNext
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class SwitchHandler: VoiceHandler {
    private enum class WorkStage{First, Confirm, Choice, ConfirmAll}
    private data class MatchKey(val name: String,
                                val spell: String = Pinyin.toPinyin(name, "`").toLowerCase(),
                                val similar: String = LocalStorage.getSimilar(spell),
                                val equal: Boolean = false)
    private data class Candicate(val panel: Panel?,
                                 val entity: JsonEntity?,
                                 val dashboard: Dashboard)

    private lateinit var controller: VoiceController
    private val db by lazy { LocalStorage.instance }
    private val pattern4 by lazy { Pattern.compile(".*?(打开|关闭|停止|开|关|切换|执行|调用)((.*)[的地得])?(.*)") }
    private val patternx by lazy { Pattern.compile(db.listPanel().foldIndexed(StringBuilder(".*?(打开|关闭|停止|开|关|切换|执行|调用)("), {index, ss, it->
        if (index > 0) ss.append("|")
        ss.append(it.name)
    }).append(")[的地得]?(.*)").toString()) }
    private var workStage: WorkStage = WorkStage.First
    override fun setup(controller: VoiceController) {
        this.controller = controller
        this.controller.register(".*?(打开|关闭|停止|开|关|切换|执行|调用)(.*)", this)
    }
    override fun handle(command: String, more: Boolean, blindly: Boolean): Boolean {
        this.blindly = blindly
        if (!more) {
            this.workStage = WorkStage.First
            this.disposable?.dispose()
        }
        when (workStage) {
            WorkStage.First-> return matchAll(command)
            WorkStage.Confirm-> matchConfirm(command)
            WorkStage.Choice-> matchChoice(command)
            WorkStage.ConfirmAll-> matchConfirmAll(command)
        }
        return true
    }
    override fun detailClicked(item: DetailItem) {
        if (item.data is Boolean) {
            if (item.data) doCommandAll(candicates)
            else this.controller.finish(FinishAction.reset, "请重新说出你要的操作！")
        } else {
            (item.data as Candicate?)?.let { doCommand(it) }
        }
    }
    override fun reset() {
        this.workStage = WorkStage.First
        this.disposable?.dispose()
    }
    private fun matchAll(command: String): Boolean {
        val panelKeys = mutableSetOf<MatchKey>()
        val entityKeys = mutableSetOf<MatchKey>()
        var forAll = false
        var it = command
        if (it.contains("全部") || it.contains("所有")) {
            forAll = true
            it = it.replace("全部", "").replace("所有", "")
        }
        var found = false
        if (!found) {
            val m = patternx.matcher(it)
            if (m.find()) { if (m.group(3).isNotBlank()) { found = true; panelKeys.add(MatchKey(m.group(2))); entityKeys.add(MatchKey(m.group(3))); action = m.group(1) } }
        }
        if (!found) {
            val m = pattern4.matcher(it)
            if (m.find()) { if (m.group(4).isNotBlank()) {found = true; if (!m.group(2).isNullOrBlank()) panelKeys.add(MatchKey(m.group(3))); entityKeys.add(MatchKey(m.group(4))); action = m.group(1) } }
        }
        if (!found && it.isNotBlank()) {
            action = "执行"; entityKeys.add(MatchKey(it, equal = true))
        }
        if (action.contains("开")) action = "打开"
        else if (action.contains("关")) action = "关闭"
        else if (action.contains("切换")) action = "切换"
        if (panelKeys.size < 1 && entityKeys.size < 1) return false
        val panels = db.listPanel()
        var matches = panels.filter {p-> p.name.isNotBlank() && panelKeys.find { it.name == p.name } != null }
        if (matches.size < 1) matches = panels.filter {p-> p.spell.isNotBlank() && panelKeys.find { it.spell == p.spell } != null }
        if (matches.size < 1) matches = panels.filter {p-> p.similar.isNotBlank() && panelKeys.find { it.similar == p.similar } != null }
        if (matches.size < 1) matches = panels.filter {p-> p.name.isNotBlank() && panelKeys.find { it.name.contains(p.name) || p.name.contains(it.name) } != null }
        if (matches.size < 1) matches = panels.filter {p-> p.spell.isNotBlank() && panelKeys.find { it.spell.contains(p.spell) || p.spell.contains(it.spell) } != null }
        if (matches.size < 1) matches = panels.filter {p-> p.similar.isNotBlank() && panelKeys.find { it.similar.contains(p.similar) || p.similar.contains(it.similar) } != null }
        if (matches.size < 1) matches = panels
        val allDashboards = mutableListOf<Dashboard>()
        matches.forEach {  allDashboards.addAll(db.listDashborad(it.id)) }
        val dashboards = if (action.contains("开") || action.contains("关") || action.contains("切换")) allDashboards.filter { !it.entityId.contains("sensor.") && !it.entityId.contains("automation.") && !it.entityId.contains("input.") && !it.entityId.contains("tracker.") && !it.entityId.startsWith("input_") && !it.entityId.contains("notification.") }
        else if (action.contains("调用") || action.contains("运行") || action.contains("执行")) allDashboards.filter { it.entityId.contains("script.") || it.entityId.contains("automation.") }
        else allDashboards
        var entities = dashboards.filter {p-> p.itemType == ItemType.entity && entityKeys.find { it.name == p.showName } != null }
        if (entities.size < 1) dashboards.filter {p-> p.itemType == ItemType.entity && entityKeys.find { it.spell == p.spell } != null }
        if (entities.size < 1) entities = dashboards.filter {p-> p.itemType == ItemType.entity && entityKeys.find { it.similar == p.similar } != null }
        if (entities.size < 1) entities = dashboards.filter {p-> p.itemType == ItemType.entity && entityKeys.find { !it.equal && it.spell.contains(p.spell) || p.spell.contains(it.spell) } != null }
        if (entities.size < 1) entities = dashboards.filter {p-> p.itemType == ItemType.entity && entityKeys.find { !it.equal && it.similar.contains(p.similar) || p.similar.contains(it.similar) } != null }
        val panelMap = panels.associateBy { it.id }
        val candicates = entities.map { Candicate(panelMap.get(it.panelId), db.getEntity(it.entityId), it) }.filter { it.entity != null }
        if (candicates.size < 1) return false
        if (candicates.size == 1) doCommand(candicates.get(0))
        else if (candicates.size > 1 && forAll) doConfirmAll(candicates)
        else if (candicates.size > 1) showChoice(candicates)
        return true
    }
    private fun matchChoice(result: String) {
        candicates?.let { candicate ->
            val it = result.replace("地", "的").replace("得", "的")
            if (it.contains("的")) {
                val panel = it.substring(0, it.indexOf("的"))
                val entity = it.substring(it.indexOf("的") + 1)
                val panelSpell = Pinyin.toPinyin(panel, "`").toLowerCase()
                val panelSimilar = LocalStorage.getSimilar(panelSpell)
                val entitySpell = Pinyin.toPinyin(entity, "`").toLowerCase()
                val entitySimilar = LocalStorage.getSimilar(entitySpell)
                var matches = candicate.filter { (it.panel != null && (it.panel.spell.contains(panelSpell) || panelSpell.contains(it.panel.spell))) && (it.dashboard.spell.contains(entitySpell) || entitySpell.contains(it.dashboard.spell)) }
                if (matches.size < 1) matches = candicate.filter { (it.panel != null && (it.panel.similar.contains(panelSimilar) || panelSimilar.contains(it.panel.similar))) && (it.dashboard.similar.contains(entitySimilar) || entitySimilar.contains(it.dashboard.similar)) }
                if (matches.size < 1) matches = candicate.filter { (it.panel != null && (it.panel.spell.contains(panelSpell) || panelSpell.contains(it.panel.spell))) || (it.dashboard.spell.contains(entitySpell) || entitySpell.contains(it.dashboard.spell)) }
                if (matches.size < 1) matches = candicate.filter { (it.panel != null && (it.panel.similar.contains(panelSimilar) || panelSimilar.contains(it.panel.similar))) || (it.dashboard.similar.contains(entitySimilar) || entitySimilar.contains(it.dashboard.similar)) }
                if (matches.size == 1) doCommand(matches.get(0))
                else if (matches.size > 1) showChoice(matches)
                else this.controller.setInput("我没听懂，请再说一遍", this)
            } else {
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
    private fun matchConfirmAll(result: String) {
        if (result.contains("不") || result.contains("错")) {
            this.controller.finish(FinishAction.reset, "请重新说出你要的操作！")
        } else if (result.contains("是") || result.contains("对")) {
            this.doCommandAll(candicates)
        } else {
            candicates?.let { candicate ->
                val it = result.replace("地", "的").replace("得", "的")
                if (it.contains("的")) {
                    val panel = it.substring(0, it.indexOf("的"))
                    val entity = it.substring(it.indexOf("的") + 1)
                    val panelSpell = Pinyin.toPinyin(panel, "`").toLowerCase()
                    val panelSimilar = LocalStorage.getSimilar(panelSpell)
                    val entitySpell = Pinyin.toPinyin(entity, "`").toLowerCase()
                    val entitySimilar = LocalStorage.getSimilar(entitySpell)
                    var matches = candicate.filter { (it.panel != null && (it.panel.spell.contains(panelSpell) || panelSpell.contains(it.panel.spell))) && (it.dashboard.spell.contains(entitySpell) || entitySpell.contains(it.dashboard.spell)) }
                    if (matches.size < 1) matches = candicate.filter { (it.panel != null && (it.panel.similar.contains(panelSimilar) || panelSimilar.contains(it.panel.similar))) && (it.dashboard.similar.contains(entitySimilar) || entitySimilar.contains(it.dashboard.similar)) }
                    if (matches.size < 1) matches = candicate.filter { (it.panel != null && (it.panel.spell.contains(panelSpell) || panelSpell.contains(it.panel.spell))) || (it.dashboard.spell.contains(entitySpell) || entitySpell.contains(it.dashboard.spell)) }
                    if (matches.size < 1) matches = candicate.filter { (it.panel != null && (it.panel.similar.contains(panelSimilar) || panelSimilar.contains(it.panel.similar))) || (it.dashboard.similar.contains(entitySimilar) || entitySimilar.contains(it.dashboard.similar)) }
                    if (matches.size == 1) doCommand(matches.get(0))
                    else if (matches.size > 1) showChoice(matches)
                    else this.controller.setInput("我没听懂，请再说一遍", this)
                } else {
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
        }
    }
    private var blindly: Boolean = true
    private var action: String = ""
    private var candicates: List<Candicate>? = null
    private var confirm: Candicate? = null
    private fun showChoice(candicates: List<Candicate>) {
        if (blindly) return doConfirm(candicates.get(0))
        val items = candicates.map {
            val name = if (it.dashboard.showName.isNullOrEmpty()) it.entity?.friendlyName else it.dashboard.showName
            val display = if (it.panel?.name.isNullOrBlank()) "${action}${name}" else "${action}${it.panel?.name}的${name}"
            DetailItem(display, it)
        }
        this.candicates = candicates
        this.workStage = WorkStage.Choice
        this.controller.setInput("请选择具体的设备", this)
        this.controller.setDetail(items, this)
    }
    private fun doConfirmAll(candicates: List<Candicate>) {
        this.candicates = candicates
        this.workStage = WorkStage.ConfirmAll
        this.controller.setInput("共${candicates.size}个设备，是要全部${action}吗？", this)
        val list = mutableListOf(DetailItem("是", true), DetailItem("否", false))
        list.addAll(candicates.map {
            val name = if (it.dashboard.showName.isNullOrEmpty()) it.entity?.friendlyName else it.dashboard.showName
            val display = if (it.panel?.name.isNullOrBlank()) "${action}${name}" else "${action}${it.panel?.name}的${name}"
            DetailItem(display, it)
        })
        this.controller.setDetail(list, this)
    }
    private fun doConfirm(candicate: Candicate) {
        val entity = candicate.entity
        if (entity == null) return controller.finish(FinishAction.reset, "未找到可控制的设备！")
        val name = if (entity.showName.isNullOrEmpty()) entity.friendlyName else entity.showName
        val command = if (candicate.panel?.name.isNullOrBlank()) "你是要${action}${name}吗？"
        else "你是要${action}${candicate.panel?.name}的${name}吗？"
        this.confirm = candicate
        this.workStage = WorkStage.Confirm
        this.controller.setInput(command, this)
    }
    private fun doCommand(candicate: Candicate?) {
        if (candicate == null) return controller.finish(FinishAction.reset, "数据处理错误！")
        val entity = candicate.entity
        if (entity == null) return controller.finish(FinishAction.reset, "未找到可控制的设备！")
        val name = if (candicate.dashboard.showName.isNullOrEmpty()) entity.friendlyName else candicate.dashboard.showName
        val method = if (candicate.panel?.name.isNullOrBlank()) "${action}${name}"
        else "${action}${candicate.panel?.name}的${name}"
        this.controller.setStatus(method)
        val action = if (entity.isScript) "turn_on"
        else if (entity.isVacuum && action == "打开") "start"
        else if (entity.isVacuum && action == "关闭") "stop"
        else if (entity.isCover && action == "打开") "open_cover"
        else if (entity.isCover && action == "关闭") "close_cover"
        else if (entity.isCover && action == "停止") "stop_cover"
        else if (action == "打开") "turn_on"
        else if (action == "关闭") "turn_off"
        else if (action == "切换") "toggle"
        else "turn_on"
        callService(method, ServiceRequest(entity.domain, action, entity.entityId))
    }
    private var disposable: Disposable? = null
    private fun callService(method: String, request: ServiceRequest) {
        this.controller.setTips("正在执行，请稍候...", true)
        HassApplication.application.ws.callService(request.domain ?: "", request.service ?: "", request)
                .subscribeOn(Schedulers.computation())
                .withNext { this.controller.finish(FinishAction.close, "${method}完成") }
                .error { this.controller.finish(FinishAction.reset, "${method}失败") }
                .subscribeOnMain { this.disposable = it }
    }
    private fun doCommandAll(candicates: List<Candicate>?) {
        if (candicates == null || candicates.size < 1) return this.controller.finish(FinishAction.close)
        this.controller.setTips("正在执行，请稍候...", true)
        Observable.create<String> {emitter->
            var failCount = 0
            candicates.forEach {
                val entity = it.entity
                val action = if (entity!!.isScript) "turn_on"
                else if (entity.isVacuum && action == "打开") "start"
                else if (entity.isVacuum && action == "关闭") "stop"
                else if (entity.isCover && action == "打开") "open_cover"
                else if (entity.isCover && action == "关闭") "close_cover"
                else if (entity.isCover && action == "停止") "stop_cover"
                else if (action == "打开") "turn_on"
                else if (action == "关闭") "turn_off"
                else if (action == "切换") "toggle"
                else "turn_on"
                val name = if (it.dashboard.showName.isNullOrEmpty()) entity.friendlyName else it.dashboard.showName
                val method = if (it.panel?.name.isNullOrBlank()) "${this.action}${name}"
                else "${this.action}${it.panel?.name}的${name}"
                emitter.onNext(method)
                try {
                    HassApplication.application.ws.callService(entity.domain, action, ServiceRequest(entity.domain, action, entity.entityId))
                            .timeout(3, TimeUnit.SECONDS)
                            .blockingFirst()
                    try { Thread.sleep(1000) } catch (_: Exception) {}
                } catch (ex: Throwable) {
                    failCount++
                    ex.printStackTrace()
                }
            }
            if (failCount > 0) emitter.onError(Throwable("${failCount}个设备执行失败"))
            else emitter.onComplete()
        }.subscribeOn(Schedulers.computation()).withNext {
            this.controller.setTips("正在${it}，请稍候...", true, false)
        }.complete {
            this.controller.finish(FinishAction.close, "${action}${candicates.size}个设备完成")
        }.error {
            this.controller.finish(FinishAction.close, it.localizedMessage)
        }.subscribeOnMain {
            this.disposable = it
        }
    }
}