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
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.regex.Pattern

class MessageHandler: VoiceHandler {
    private enum class WorkStage{First, Confirm, Choice, Message}
    private data class MatchKey(val name: String,
                                val spell: String = Pinyin.toPinyin(name, "`").toLowerCase(),
                                val similar: String = LocalStorage.getSimilar(spell))
    private data class Candicate(val panel: Panel?,
                                 val entity: JsonEntity?,
                                 val dashboard: Dashboard)

    private lateinit var controller: VoiceController
    private val db by lazy { LocalStorage.instance }
    private val pattern3 by lazy { Pattern.compile(".*?(发送消息)[到|给]?(.*)[的地得](.*)") }
    private val pattern2 by lazy { Pattern.compile(".*?(发送消息)[到|给]?(.*)") }
    private val patternx by lazy { Pattern.compile(db.listPanel().foldIndexed(StringBuilder(".*?(发送消息)[到|给]?("), {index, ss, it->
        if (index > 0) ss.append("|")
        ss.append(it.name)
    }).append(")[的地得]?(.*)").toString()) }
    private var workStage: WorkStage = WorkStage.First
    override fun setup(controller: VoiceController) {
        this.controller = controller
        this.controller.register(".*?(发送消息)(.*)[的地得](.*)", this)
        this.controller.register(".*?(发送消息)(.*)", this)
        this.controller.register(db.listPanel().foldIndexed(StringBuilder(".*?(发送消息)?("), {index, ss, it->
            if (index > 0) ss.append("|")
            ss.append(it.name)
        }).append(")(.*)").toString(), this)
    }
    override fun handle(command: String, more: Boolean, blindly: Boolean): Boolean {
        this.blindly = blindly
        if (!more) this.workStage = WorkStage.First
        when (workStage) {
            WorkStage.First-> return matchAll(command)
            WorkStage.Confirm-> matchConfirm(command)
            WorkStage.Choice-> matchChoice(command)
            WorkStage.Message-> sendMessage(command)
        }
        return true
    }
    override fun detailClicked(item: DetailItem) {
        (item.data as Candicate?)?.let { doCommand(it) }
    }
    override fun reset() {
        this.workStage = WorkStage.First
        this.disposable?.dispose()
    }
    private fun matchAll(command: String): Boolean {
        val panelKeys = mutableSetOf<MatchKey>()
        val entityKeys = mutableSetOf<MatchKey>()
        var found = false
        if (!found) {
            val m = patternx.matcher(command)
            if (m.find()) { found = true; panelKeys.add(MatchKey(m.group(2))); entityKeys.add(MatchKey(m.group(3))) }
        }
        if (!found) {
            val m = pattern3.matcher(command)
            if (m.find()) { found = true; panelKeys.add(MatchKey(m.group(2))); entityKeys.add(MatchKey(m.group(3))) }
        }
        if (!found) {
            val m = pattern2.matcher(command)
            if (m.find()) { found = true; entityKeys.add(MatchKey(m.group(2))) }
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
        matches.forEach {  dashboards.addAll(db.listDashborad(it.id).filter { it.entityId.startsWith("input_text.") }) }
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
            this.controller.finish(FinishAction.reset, "请重新说出你要的设备！")
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
            val name = if (it.dashboard.showName.isNullOrBlank()) it.entity?.friendlyName else it.dashboard.showName
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
        val name = if (entity.showName.isNullOrBlank()) entity.friendlyName else entity.showName
        val command = if (candicate.panel?.name.isNullOrBlank()) "你是要发送消息到${name}吗？"
        else "你是要发送消息到${candicate.panel?.name}的${name}吗？"
        this.confirm = candicate
        this.workStage = WorkStage.Confirm
        this.controller.setInput(command, this)
    }
    private var disposable: Disposable? = null
    private fun sendMessage(command: String) {
        val entity = confirm?.entity
        if (entity == null) return controller.finish(FinishAction.reset, "发送消息错误！")
        val name = if (confirm?.dashboard?.showName.isNullOrBlank()) entity.friendlyName else confirm?.dashboard?.showName
        val method = if (confirm?.panel?.name.isNullOrBlank()) "发送消息到${name}"
        else "发送消息到${confirm?.panel?.name}的${name}"
        HassApplication.application.ws.callService(entity.domain, "set_value", ServiceRequest(entity.domain, "set_value", entity.entityId, value = command))
                .subscribeOn(Schedulers.computation())
                .withNext { this.controller.finish(FinishAction.close, "${method}完成") }
                .error { this.controller.finish(FinishAction.reset, "${method}失败") }
                .subscribeOnMain { this.disposable = it }
    }
    private fun doCommand(candicate: Candicate?) {
        if (candicate == null) return controller.finish(FinishAction.reset, "数据处理错误！")
        val entity = candicate.entity
        if (entity == null) return controller.finish(FinishAction.reset, "未找到可控制的设备！")
        this.confirm = candicate
        this.workStage = WorkStage.Message
        this.controller.setInput("请说内容：", this)
    }
}