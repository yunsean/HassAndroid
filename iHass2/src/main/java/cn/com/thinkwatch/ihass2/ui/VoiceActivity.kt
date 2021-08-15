package cn.com.thinkwatch.ihass2.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.db.LocalStorage
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.Dashboard
import com.baidu.aip.asrwakeup3.core.recog.MyRecognizer
import com.baidu.aip.asrwakeup3.core.recog.RecogResult
import com.baidu.aip.asrwakeup3.core.recog.listener.IRecogListener
import com.baidu.speech.asr.SpeechConstant
import com.dylan.common.rx.RxBus2
import com.github.promeg.pinyinhelper.Pinyin
import com.yunsean.dynkotlins.extensions.logis
import kotlinx.android.synthetic.main.activity_hass_voice.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.lang.StringBuilder
import java.util.regex.Pattern


class VoiceActivity : Activity(), IRecogListener {
    override fun onAsrReady() {
        logis("onAsrReady")
        result.visibility = View.GONE
        status.setText("请讲话...")
        recognizing = true
        waveView.startPreparingAnimation()
    }
    override fun onAsrBegin() {
        logis("onAsrBegin")
        status.setText("正在聆听...")
        waveView.startRecognizingAnimation()
    }
    override fun onAsrEnd() {
        logis("onAsrEnd")
        recognizing = false
        waveView.resetAnimation()
    }
    override fun onAsrPartialResult(results: Array<out String>?, recogResult: RecogResult?) {
        logis("onAsrPartialResult")
        if (results != null && results.size > 0) {
            status.setText(results[0])
            match(results)
        }
    }
    override fun onAsrOnlineNluResult(nluResult: String?) {
        logis("onAsrOnlineNluResult(${nluResult})")
    }
    override fun onAsrFinalResult(results: Array<out String>?, recogResult: RecogResult?) {
        logis("onAsrFinalResult(${results?.fold(StringBuilder(), {s, t-> s.append(t)})})")
    }
    override fun onAsrFinish(recogResult: RecogResult?) {
        logis("onAsrFinish(${recogResult})")
    }
    override fun onAsrFinishError(errorCode: Int, subErrorCode: Int, descMessage: String?, recogResult: RecogResult?) {
        status.setText("识别失败")
        logis("onAsrFinishError")
    }
    override fun onAsrLongFinish() {
        logis("onAsrLongFinish")
    }
    override fun onAsrVolume(volumePercent: Int, volume: Int) {
    }
    override fun onAsrAudio(data: ByteArray?, offset: Int, length: Int) {
    }
    override fun onAsrExit() {
        logis("onAsrExit")
    }
    override fun onOfflineLoaded() {
        logis("onOfflineLoaded")
    }
    override fun onOfflineUnLoaded() {
        logis("onOfflineUnLoaded")
    }

    private var recognizing = false
    set(value) {
        field = value
        toggle.isActivated = value
    }
    private lateinit var recoginizer: MyRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_voice)

        recoginizer = MyRecognizer(this, this)
        start()
        ui()
    }
    override fun onPause() {
        super.onPause()
        recoginizer.release()
        finish()
    }
    private fun ui() {
        this.close.onClick {
            onBackPressed()
        }
        this.toggle.onClick {
            if (recognizing) stop()
            else start()
        }
    }

    private val pattern3 by lazy { Pattern.compile(".*?(打开|关闭|执行|查询|开|关)(.*)[的地得](.*)") }
    private val pattern2 by lazy { Pattern.compile(".*?(打开|关闭|执行|查询|开|关)(.*)") }
    private fun match(results: Array<out String>) {
        results.forEach { command->
            var m = pattern3.matcher(command)
            if (m.find()) return@match action3(m.group(1), m.group(2), m.group(3))
            m = pattern2.matcher(command)
            if (m.find()) return@match action2(m.group(1), m.group(2))
        }
    }
    private fun action3(action: String, panel: String, entity: String) {
        var spell = Pinyin.toPinyin(panel, "`").toLowerCase()
        var similar = LocalStorage.getSimilar(spell)
        val panels = db.listPanel()
        var matches = panels.filter { it.spell.isNotBlank() && spell.contains(it.spell) }
        if (matches.size > 1) matches.find { it.spell == spell }?.let { matches = listOf(it) }
        else if (matches.size < 1) matches = panels.filter { it.similar.isNotBlank() && similar.contains(it.similar) }
        if (matches.size > 1) matches.find { it.similar == similar }?.let { matches = listOf(it) }
        else if (matches.size < 1) return setResultError("未找到可控制的设备！")
        spell = Pinyin.toPinyin(entity, "`").toLowerCase()
        similar = LocalStorage.getSimilar(spell)
        val allMatches = mutableListOf<Dashboard>()
        val allSimilars = mutableListOf<Dashboard>()
        matches.forEach {
            val entities = db.listDashborad(it.id)
            allMatches.addAll(entities.filter { it.spell.isNotBlank() && spell.contains(it.spell) })
            allSimilars.addAll(entities.filter { it.similar.isNotBlank() && similar.contains(it.similar) })
        }
        if (allMatches.size == 1) return runEntity(action, allMatches.get(0))
        else if (allMatches.size > 1) allMatches.find { it.spell == spell }?.let { return@action3 runEntity(action, it) }
        if (allMatches.size > 0) return showChoice(action, panel, allMatches)
        if (allSimilars.size == 1) return runEntity(action, allSimilars.get(0))
        else if (allSimilars.size > 1) allSimilars.find { it.similar == similar }?.let { return@action3 runEntity(action, it) }
        if (allSimilars.size > 0) return showChoice(action, panel, allSimilars)
        setResultError("未找到可控制的设备！")
    }
    private fun showChoice(action: String, panel: String, dashboard: List<Dashboard>) {

    }
    private fun runEntity(action: String, dashboard: Dashboard) {
        val panel = db.getPanel(dashboard.panelId)
        val entity = db.getEntity(dashboard.entityId)
        if (entity == null) return setResultError("未找到可控制的设备！")
        val name = if (entity.showName.isNullOrEmpty()) entity.friendlyName else entity.showName
        result.setText("${action}${panel?.name}的${name}")
        if (entity.isToggleable) {
            RxBus2.getDefault().post(ServiceRequest(entity.domain, "toggle", entity.entityId))
            result.setText("指令已执行")
        } else if (entity.isSensor) {
            result.setText("${name}当前状态：${entity.friendlyStateRow}")
        } else if (entity.isScript) {
            RxBus2.getDefault().post(ServiceRequest("homeassistant", "turn_on", entity.entityId))
            result.setText("指令已执行")
        } else {
            result.setText("当前暂不执行该操作！")
        }
    }
    private fun action2(action: String, panel: String) {

    }
    private fun setResultError(message: String) {
        result.visibility = View.VISIBLE
        result.setText(message)
        result.setTextColor(0xffff0000.toInt())
    }
    private fun setResultMessage(message: String) {
        result.visibility = View.VISIBLE
        result.setText(message)
        result.setTextColor(0xff777777.toInt())
    }
    private fun start() {
        val params = mapOf<String, Any>(SpeechConstant.SOUND_START to R.raw.bdspeech_recognition_start,
                SpeechConstant.SOUND_END to R.raw.bdspeech_speech_end,
                SpeechConstant.SOUND_SUCCESS to R.raw.bdspeech_recognition_success,
                SpeechConstant.SOUND_ERROR to R.raw.bdspeech_recognition_error,
                SpeechConstant.SOUND_CANCEL to R.raw.bdspeech_recognition_cancel)
        recoginizer.start(params)
    }
    private fun stop() {
        recoginizer.stop()
    }
}

