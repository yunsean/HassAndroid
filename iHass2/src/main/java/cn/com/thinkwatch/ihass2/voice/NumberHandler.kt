package cn.com.thinkwatch.ihass2.voice

import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.db.LocalStorage
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.enums.ItemType
import cn.com.thinkwatch.ihass2.model.Dashboard
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.model.Panel
import cn.com.thinkwatch.ihass2.network.base.ws
import cn.com.thinkwatch.ihass2.utils.LongNumerals
import com.github.promeg.pinyinhelper.Pinyin
import com.yunsean.dynkotlins.extensions.withNext
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.math.BigDecimal
import java.util.regex.Pattern



class NumberHandler: VoiceHandler {
    private data class MatchKey(val name: String,
                                val spell: String = Pinyin.toPinyin(name, "`").toLowerCase(),
                                val similar: String = LocalStorage.getSimilar(spell),
                                val equal: Boolean = false)
    private data class Candicate(val panel: Panel?,
                                 val entity: JsonEntity?,
                                 val dashboard: Dashboard)

    private lateinit var controller: VoiceController
    private val db by lazy { LocalStorage.instance }
    private val pattern3 by lazy { Pattern.compile("[把]?((.*)[的地得])?(.*?)调.*?(\\d{1,3})") }
    private val pattern2 by lazy { Pattern.compile(".*?调[整]?((.*)[的地得])?(.*?)[到至为]?(\\d{1,3})") }
    private val patternx3 by lazy { Pattern.compile(db.listPanel().foldIndexed(StringBuilder(".*?调.*?("), {index, ss, it->
        if (index > 0) ss.append("|")
        ss.append(it.name)
    }).append(")[的地得]?(.*?)(\\d{1,3})").toString()) }
    private val patternx2 by lazy { Pattern.compile(db.listPanel().foldIndexed(StringBuilder(".*?("), {index, ss, it->
        if (index > 0) ss.append("|")
        ss.append(it.name)
    }).append(")[的地得]?(.+?)调.*?(\\d{1,3})").toString()) }
    private val numberal1 by lazy { Pattern.compile(".*?调.*?(\\d{1,3})") }
    override fun setup(controller: VoiceController) {
        this.controller = controller
        this.controller.register(".*?调.*?(\\d{1,3})", this)
        this.controller.register(".*?调.*?([壹贰叁肆伍陆柒捌玖零拾佰仟萬一二三四五六七八九〇十百千万亿两]+)", this)
    }
    override fun handle(command: String, more: Boolean, blindly: Boolean): Boolean {
        return matchAll(command)
    }
    override fun detailClicked(item: DetailItem) {
    }
    override fun reset() {
        this.disposable?.dispose()
    }
    private fun matchAll(command: String): Boolean {
        var command = command
        if (!numberal1.matcher(command).find() && LongNumerals.numberPattern.matcher(command).find()) {
            var matched: String? = null
            var start = 0
            var end = 0
            val m = LongNumerals.numberPattern.matcher(command)
            while (m.find()) {
                start = m.start();
                end = m.end();
                matched = m.group(0);
            }
            if (matched == null) return false
            command = command.substring(0, start) + LongNumerals.convert(matched).toString() + command.substring(end)
        }
        val panelKeys = mutableSetOf<MatchKey>()
        val entityKeys = mutableSetOf<MatchKey>()
        var number: String? = null
        var found = false
        if (!found) {
            val m = patternx3.matcher(command)
            if (m.find()) { if (m.group(2).isNotBlank()) { found = true; if (!m.group(1).isNullOrBlank()) panelKeys.add(MatchKey(m.group(1))); entityKeys.add(MatchKey(m.group(2))); number = m.group(3) } }
        }
        if (!found) {
            val m = patternx2.matcher(command)
            if (m.find()) { if (m.group(2).isNotBlank()) { found = true; if (!m.group(1).isNullOrBlank()) panelKeys.add(MatchKey(m.group(1))); entityKeys.add(MatchKey(m.group(2))); number = m.group(3) } }
        }
        if (!found) {
            val m = pattern3.matcher(command)
            if (m.find()) { if (m.group(3).isNotBlank()) { found = true; if (!m.group(1).isNullOrBlank()) panelKeys.add(MatchKey(m.group(2))); entityKeys.add(MatchKey(m.group(3))); number = m.group(4) } }
        }
        if (!found) {
            val m = pattern2.matcher(command)
            if (m.find()) { if (m.group(3).isNotBlank()) { found = true; if (!m.group(1).isNullOrBlank()) panelKeys.add(MatchKey(m.group(2))); entityKeys.add(MatchKey(m.group(3))); number = m.group(4) } }
        }
        if (!found) return false
        if (panelKeys.size < 1 && entityKeys.size < 1) return false
        val panels = db.listPanel()
        var matches = panels.filter {p-> p.name.isNotBlank() && panelKeys.find { it.name == p.name } != null }
        if (matches.size < 1) matches = panels.filter {p-> p.spell.isNotBlank() && panelKeys.find { it.spell == p.spell } != null }
        if (matches.size < 1) matches = panels.filter {p-> p.similar.isNotBlank() && panelKeys.find { it.similar == p.similar } != null }
        if (matches.size < 1) matches = panels.filter {p-> p.name.isNotBlank() && panelKeys.find { it.name.contains(p.name) || p.name.contains(it.name) } != null }
        if (matches.size < 1) matches = panels.filter {p-> p.spell.isNotBlank() && panelKeys.find { it.spell.contains(p.spell) || p.spell.contains(it.spell) } != null }
        if (matches.size < 1) matches = panels.filter {p-> p.similar.isNotBlank() && panelKeys.find { it.similar.contains(p.similar) || p.similar.contains(it.similar) } != null }
        if (matches.size < 1) matches = panels
        val dashboards = mutableListOf<Dashboard>()
        matches.forEach {  dashboards.addAll(db.listDashborad(it.id).filter { it.entityId.startsWith("light.") || it.entityId.startsWith("cover.") || it.entityId.startsWith("climate.") || it.entityId.startsWith("input_slider.") || it.entityId.startsWith("input_number.") }) }
        var entities = dashboards.filter {p-> p.itemType == ItemType.entity && entityKeys.find { it.name == p.showName } != null }
        if (entities.size < 1) entities = dashboards.filter {p-> p.itemType == ItemType.entity && entityKeys.find { it.spell == p.spell } != null }
        if (entities.size < 1) entities = dashboards.filter {p-> p.itemType == ItemType.entity && entityKeys.find { it.similar == p.similar } != null }
        if (entities.size < 1) entities = dashboards.filter {p-> p.itemType == ItemType.entity && entityKeys.find { !it.equal && it.spell.contains(p.spell) || p.spell.contains(it.spell) } != null }
        if (entities.size < 1) entities = dashboards.filter {p-> p.itemType == ItemType.entity && entityKeys.find { !it.equal && it.similar.contains(p.similar) || p.similar.contains(it.similar) } != null }
        val panelMap = panels.associateBy { it.id }
        val candicates = entities.map { Candicate(panelMap.get(it.panelId), db.getEntity(it.entityId), it) }.filter { it.entity != null }
        val volume = number?.toIntOrNull()
        if (volume == null) return false
        if (candicates.size < 1) return false
        else if (candicates.size == 1) doCommand(candicates.get(0), command, volume)
        else this.controller.finish(FinishAction.reset, "找到多个可控制的设备，请说的更具体一些！")
        return true
    }
    private var disposable: Disposable? = null
    private fun doCommand(candicate: Candicate?, command: String, volume: Int) {
        if (candicate == null) return controller.finish(FinishAction.reset, "数据处理错误！")
        val entity = candicate.entity
        if (entity == null) return controller.finish(FinishAction.reset, "未找到可控制的设备！")
        val name = if (candicate.dashboard.showName.isNullOrEmpty()) entity.friendlyName else candicate.dashboard.showName
        var method: String = ""
        var request: ServiceRequest? = null
        if (entity.entityId.startsWith("light.")) {
            method = "调整${name}的亮度为百分之${volume}"
            request = ServiceRequest(entity.domain, "turn_on", entity.entityId, brightness = volume * 255 / 100)
        } else if (entity.entityId.startsWith("cover.") && (command.contains("角") || command.contains("度"))) {
            method = "调整${name}的倾角为百分之${volume}"
            request = ServiceRequest(entity.domain, "set_cover_tilt_position", entity.entityId, tiltPosition = volume.toString())
        } else if (entity.entityId.startsWith("cover.")) {
            method = "打开${name}到百分之${volume}"
            request = ServiceRequest(entity.domain, "set_cover_position", entity.entityId, position = volume.toString())
        } else if (entity.entityId.startsWith("climate.")) {
            method = "调整${name}的温度到${volume}摄氏度"
            request = ServiceRequest(entity.domain, "set_temperature", entity.entityId, temperature = BigDecimal(volume))
        } else if (entity.entityId.startsWith("input_slider.") || entity.entityId.startsWith("input_number.")) {
            method = "调整${name}的值为${volume}"
            request = ServiceRequest(entity.domain, "set_cover_position", entity.entityId, value = volume.toString())
        }
        if (request == null) return controller.finish(FinishAction.reset, "无法执行指令！")
        this.controller.setStatus(method)
        this.controller.setTips("正在执行，请稍候...", true)
        HassApplication.application.ws.callService(request.domain ?: "", request.service ?: "", request)
                .subscribeOn(Schedulers.computation())
                .withNext { this.controller.finish(FinishAction.close, "${method}完成") }
                .error { this.controller.finish(FinishAction.reset, "${method}失败") }
                .subscribeOnMain { this.disposable = it }
    }
}