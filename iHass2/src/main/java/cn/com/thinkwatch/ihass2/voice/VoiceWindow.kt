package cn.com.thinkwatch.ihass2.voice

import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.support.v7.widget.LinearLayoutManager
import android.util.DisplayMetrics
import android.view.*
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.db.LocalStorage
import cn.com.thinkwatch.ihass2.model.VoiceHistory
import cn.com.thinkwatch.ihass2.ui.VoiceHistoryActivity
import cn.com.thinkwatch.ihass2.utils.TtsEngine
import com.baidu.aip.asrwakeup3.core.recog.MyRecognizer
import com.baidu.aip.asrwakeup3.core.recog.RecogResult
import com.baidu.aip.asrwakeup3.core.recog.listener.IRecogListener
import com.baidu.speech.asr.SpeechConstant
import com.dylan.common.sketch.Actions
import com.dylan.common.utils.Utility
import com.github.promeg.pinyinhelper.Pinyin
import com.yunsean.dynkotlins.extensions.nextOnMain
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.layout_hass_voice.view.*
import kotlinx.android.synthetic.main.listitem_voice_candidate.view.*
import org.jetbrains.anko.audioManager
import org.jetbrains.anko.layoutInflater
import org.jetbrains.anko.runOnUiThread
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.util.*
import java.util.regex.Pattern


class VoiceWindow private constructor(val act: Context): IRecogListener, VoiceController {
    private data class HandlerItem(val pattern: Pattern,
                                   val handler: VoiceHandler)
    private val allHandlers: MutableList<HandlerItem>
    init {
        allHandlers = mutableListOf()
        SystemHandler().setup(this)
        NumberHandler().setup(this)
        StatusHandler().setup(this)
        SwitchHandler().setup(this)
        MessageHandler().setup(this)
        ContactHandler().setup(this)
    }

    override fun onAsrReady() {
        floatLayout.status.setText("请讲话...")
        recognizing = true
        floatLayout.waveView.startPreparingAnimation()
    }
    override fun onAsrBegin() {
        floatLayout.tips.visibility = View.GONE
        floatLayout.calling.visibility = View.GONE
        floatLayout.status.setText("正在聆听...")
        floatLayout.waveView.startRecognizingAnimation()
    }
    override fun onAsrEnd() {
        recognizing = false
        floatLayout.waveView.resetAnimation()
    }
    override fun onAsrPartialResult(results: Array<out String>?, recogResult: RecogResult?) {
        if (results != null && results.size > 0) floatLayout.status.setText(results[0])
    }
    override fun onAsrOnlineNluResult(nluResult: String?) = Unit
    override fun onAsrFinalResult(results: Array<out String>?, recogResult: RecogResult?) {
        floatLayout.waveView.resetAnimation()
        if (results == null || results.size < 1 || results.get(0).isBlank()) {
            floatLayout.postDelayed({ finish(FinishAction.reset, "我没听懂，请再说一遍") }, 100)
            return
        }
        floatLayout.status.setText(results[0])
        try {
            match(results.get(0))
        } catch (ex: Throwable) {
            ex.printStackTrace()
        }
    }
    override fun onAsrFinish(recogResult: RecogResult?) = Unit
    override fun onAsrFinishError(errorCode: Int, subErrorCode: Int, descMessage: String?, recogResult: RecogResult?) {
        floatLayout.waveView.resetAnimation()
        if (subErrorCode == 7001 && !useTts && nextHandler != null) return  //当前需要进一步操作，并且是前台运行的，但没有说话，则保留界面不做处理
        if (useTts) speech("我没有听到你讲话，有事再叫我哦")
        dismiss(true)
    }
    override fun onAsrLongFinish() = Unit
    override fun onAsrVolume(volumePercent: Int, volume: Int) = floatLayout.waveView.setCurrentDBLevelMeter(volumePercent.toFloat())
    override fun onAsrAudio(data: ByteArray?, offset: Int, length: Int) = Unit
    override fun onAsrExit() = Unit
    override fun onOfflineLoaded() = Unit
    override fun onOfflineUnLoaded() = Unit

    private fun match(result: String) {
        save(false, result)
        val spell = Pinyin.toPinyin(result, "`").toLowerCase()
        if (spell == "qu`xiao" || spell.startsWith("tui`xia") || spell == "mei`le" || spell == "wan`le" || spell == "bi`zui") {
            save(true, "那我退下了！")
            if (useTts) speech("那我退下了！")
            dismiss(true)
            return
        } else if (spell == "bei`ke`bei`ke" || spell == "ni`hao`bei`ke" || spell == "bei`ke`bang`wo") {
            reset()
            save(true, "我在，你说！")
            this.floatLayout.postDelayed( {start(true)}, 300)
            return
        } else {
            if (nextHandler != null) {
                nextHandler!!.handle(result, true, useTts)
            } else {
                setTips("正在匹配...", true, false)
                LocalStorage.instance.async {
                    allHandlers.forEach { p ->
                        if (p.pattern.matcher(result).find() && p.handler.handle(result, false, useTts)) return@async true
                    }
                    false
                }.nextOnMain {
                    if (!it) {
                        finish(FinishAction.reset, "我没听懂，请再说一遍")
                    }
                }.error {
                    finish(FinishAction.reset, "我没听懂，请再说一遍哦")
                }
            }
        }
    }

    private var nextHandler: VoiceHandler? = null
    override fun register(pattern: String, handler: VoiceHandler) {
        try { allHandlers.add(HandlerItem(Pattern.compile(pattern), handler)) } catch (ex: Exception) { ex.printStackTrace() }
    }
    override fun setStatus(status: String) {
        act.runOnUiThread {
            floatLayout.status.setText(status)
        }
    }
    override fun setTips(reason: String, waiting: Boolean, tts: Boolean, save: Boolean) {
        act.runOnUiThread {
            floatLayout.tips.text = reason
            floatLayout.calling.visibility = if (waiting) View.VISIBLE else View.GONE
            floatLayout.tips.visibility = View.VISIBLE
            if (tts && useTts) speech(reason, save)
            else if (save) save(true, reason)
        }
    }
    private var clickedHandler: VoiceHandler? = null
    private val detailAdapter: RecyclerAdapter<DetailItem>
    override fun setDetail(details: List<DetailItem>, clickHandler: VoiceHandler?) {
        act.runOnUiThread {
            clickedHandler = clickHandler
            detailAdapter.items = details
            floatLayout.details.visibility = View.VISIBLE
        }
    }
    override fun setInput(tips: String, handler: VoiceHandler?) {
        act.runOnUiThread {
            floatLayout.tips.text = tips
            floatLayout.tips.visibility = View.VISIBLE
            floatLayout.calling.visibility = View.GONE
            nextHandler = handler
            save(true, tips)
            if (useTts) speech(tips) { start() }
            else floatLayout.postDelayed({ start() }, 500)
        }
    }
    override fun finish(action: FinishAction, tips: String?, closeTimeout: Long) {
        act.runOnUiThread {
            floatLayout.calling.visibility = View.GONE
            if (tips != null) {
                floatLayout.tips.text = tips
                floatLayout.tips.visibility = View.VISIBLE
            }
            nextHandler = null
            if (action == FinishAction.close || (useTts && action == FinishAction.halt)) {
                if (tips != null && useTts) speech(tips)
                else if (tips != null) save(true, tips)
                if (useTts) dismiss(true)
                else floatLayout.postDelayed({ dismiss(true) }, closeTimeout)
            } else if (action == FinishAction.reset) {
                if (tips != null && !useTts) save(true, tips)
                if (tips != null && useTts) speech(tips) { start() }
                else floatLayout.post { start() }
            } else {
                if (tips != null && useTts) speech(tips) { start() }
                else if (tips != null) save(true, tips)
            }
        }
    }
    private fun isBluetoothMicOn(): Boolean {
        try {
            return BluetoothAdapter.getDefaultAdapter().getProfileConnectionState(android.bluetooth.BluetoothProfile.HEADSET) == android.bluetooth.BluetoothProfile.STATE_CONNECTED
        } catch (ex: Exception) {
            return false
        }
    }
    private fun speech(message: String, save: Boolean = true, callback: (()->Unit)? = null) {
        if (save) save(true, message)
        act.audioManager.stopBluetoothSco()
        TtsEngine.get().speech(message, callback)
    }
    private var beginOfSession = true
    private fun save(speech: Boolean, content: String) {
        LocalStorage.instance.addVoiceHistory(VoiceHistory(speech, content, if (beginOfSession) Date() else null))
        beginOfSession = false
    }

    private var recognizing = false
        set(value) {
            field = value
            floatLayout.toggle.isActivated = value
        }
    private var recoginizer: MyRecognizer? = null
    private fun start(reset: Boolean = false) {
        if (nextHandler == null) act.runOnUiThread { floatLayout.details.visibility = View.GONE }
        try { recoginizer?.cancel() } catch (_: Exception) { dismiss(true); return }
        val params = mutableMapOf(SpeechConstant.SOUND_START to R.raw.bdspeech_recognition_start,
                SpeechConstant.SOUND_END to R.raw.bdspeech_speech_end,
                SpeechConstant.SOUND_SUCCESS to R.raw.bdspeech_recognition_success,
                SpeechConstant.SOUND_ERROR to R.raw.bdspeech_recognition_error,
                SpeechConstant.SOUND_CANCEL to R.raw.bdspeech_recognition_cancel,
                SpeechConstant.VAD to SpeechConstant.VAD_DNN,
                SpeechConstant.PID to 1536,
                SpeechConstant.VAD_ENDPOINT_TIMEOUT to 1500,
                SpeechConstant.IN_FILE to "#com.baidu.aip.asrwakeup3.core.inputstream.MyMicrophoneInputStream.getInstance()")
        if (reset && noWait) params.put(SpeechConstant.AUDIO_MILLS, System.currentTimeMillis() - 500)
        if (useTts && reset && !noWait) speech("在呢，请讲") {
            recoginizer?.start(params)
            if (isBluetoothMicOn() && !act.audioManager.isBluetoothScoOn) try {
                act.audioManager.mode = AudioManager.MODE_IN_CALL
                act.audioManager.isBluetoothScoOn = true
                act.audioManager.startBluetoothSco()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        } else {
            recoginizer?.start(params)
            if (isBluetoothMicOn() && !act.audioManager.isBluetoothScoOn) try {
                act.audioManager.mode = AudioManager.MODE_IN_CALL
                act.audioManager.isBluetoothScoOn = true
                act.audioManager.startBluetoothSco()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
    private fun stop() {
        recoginizer?.stop()
        recognizing = false
    }
    private fun muteAudio(mute: Boolean) {
        if (Build.VERSION.SDK_INT >= 26) {
            if (mute) (act.getSystemService(AUDIO_SERVICE) as AudioManager).requestAudioFocus(AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).build())
            else (act.getSystemService(AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).build())
        } else {
            if (mute) (act.getSystemService(AUDIO_SERVICE) as AudioManager).requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            else (act.getSystemService(AUDIO_SERVICE) as AudioManager).abandonAudioFocus(null)
        }
    }
    
    private var windowParams: WindowManager.LayoutParams
    private var windowManager: WindowManager
    private var floatLayout: View
    init {
        floatLayout = act.layoutInflater.inflate(R.layout.layout_hass_voice, null) as View
        windowParams = WindowManager.LayoutParams()
        windowManager = act.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowParams.type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        windowParams.format = PixelFormat.RGBA_8888
        windowParams.flags = WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        windowParams.gravity = Gravity.START or Gravity.TOP
        windowParams.width = Math.min(Utility.screenWidth(act), Utility.screenHeight(act)) * 9 / 10
        windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        detailAdapter = RecyclerAdapter(R.layout.listitem_voice_candidate, null) {
            view, index, item ->
            view.entity.setText(item.display)
            view.onClick {
                if (clickedHandler != null) {
                    floatLayout.details.visibility = View.GONE
                    clickedHandler?.detailClicked(item)
                }
            }
        }
        floatLayout.details.adapter = detailAdapter
        floatLayout.details.layoutManager = LinearLayoutManager(act)
    }
    private fun getLayoutHeight(): Int {
        val dm = act.getResources().getDisplayMetrics()
        floatLayout.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.AT_MOST))
        return floatLayout.getMeasuredHeight()
    }
    private var restoreApp: UsageEvents.Event? = null
    private var useTts = false
    private var noWait = false
    private fun reset() {
        this.nextHandler = null
        this.floatLayout.details.visibility = View.GONE
        this.floatLayout.tips.visibility = View.GONE
        this.floatLayout.calling.visibility = View.GONE
    }
    @Synchronized fun show(noWait: Boolean = false, restoreApp: UsageEvents.Event? = null, tts: Boolean = false) {
        this.beginOfSession = true
        this.noWait = noWait
        this.restoreApp = restoreApp
        this.useTts = tts
        this.floatLayout.status.setText("语音初始化中...")
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        windowParams.x = (metrics.widthPixels - windowParams.width) / 2
        windowParams.y = metrics.heightPixels - getLayoutHeight() * 5 / 4
        if (floatLayout.parent == null) windowManager.addView(floatLayout, windowParams)
        else this.floatLayout.requestLayout()
        this.floatLayout.setOnKeyListener { view, keyCode, keyEvent ->
            if (keyCode == KeyEvent.KEYCODE_BACK) dismiss()
            false
        }
        this.floatLayout.setOnTouchListener { view, motionEvent ->
            if ((motionEvent.action and MotionEvent.ACTION_OUTSIDE) != 0) dismiss()
            false
        }
        this.floatLayout.isFocusableInTouchMode = true
        this.floatLayout.close.onClick {
            reset()
            dismiss()
        }
        this.floatLayout.toggle.onClick {
            if (recognizing) stop() else start()
        }
        if (System.currentTimeMillis() % 10 >= 8L) this.floatLayout.bellaPanel.visibility = View.VISIBLE
        else this.floatLayout.bellaPanel.visibility = View.GONE
        this.floatLayout.bella.setText("Yaobeina (1981-2015) was a talented and courageous Chinese singer who won numerous awards for the best Chinese pop song performance. One of Yao's famous songs, \"Fire of the Heart\", was about the reflections on her battle with breast cancer. She donated her corneas. [Ref: Minor Planet Circ. 93670]")
        this.floatLayout.bella.onClick {
            if (System.currentTimeMillis() % 2 == 0L) Actions.openUrl(act, "https://minorplanetcenter.net/db_search/show_object?object_id=41981")
            else Actions.openUrl(act, "https://baike.baidu.com/item/%E5%B0%8F%E8%A1%8C%E6%98%9F41981/17192055?noadapt=1")
        }
        this.floatLayout.history.onClick {
            try {
                val intent = Intent(act, VoiceHistoryActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(act, 0, intent, 0)
                pendingIntent.send()
                dismiss(false)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
        try { recoginizer?.release() } catch (_: Throwable) {}
        this.recoginizer = MyRecognizer(act, this)
        reset()
        muteAudio(true)
        start(true)
    }
    fun isRecognizing() = floatLayout.parent != null && recognizing
    @Synchronized fun dismiss(restore: Boolean = true) {
        recoginizer?.let {
            try { it.release() } catch (_: Throwable) {}
        }
        muteAudio(false)
        if (floatLayout.parent != null) {
            windowManager.removeView(floatLayout)
        }
        if (restore) {
            this.restoreApp?.let {
                try {
                    val intent = Intent()
                    intent.setClassName(it.packageName, it.className)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    val pendingIntent = PendingIntent.getActivity(act, 0, intent, 0);
                    pendingIntent.send()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
        this.restoreApp = null
        act.audioManager.stopBluetoothSco()
        act.audioManager.isBluetoothScoOn = false
    }
    companion object {
        private var _instant: VoiceWindow? = null
        val instant: VoiceWindow by lazy { _instant = VoiceWindow(HassApplication.application); _instant!! }
        fun dismiss() { _instant?.dismiss(false) }
    }
}