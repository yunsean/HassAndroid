package cn.com.thinkwatch.ihass2.control

import android.Manifest
import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.getMessage
import cn.com.thinkwatch.ihass2.network.BaseApi
import cn.com.thinkwatch.ihass2.network.http.HttpRawApi
import cn.com.thinkwatch.ihass2.retrofit.FileRequestBody
import cn.com.thinkwatch.ihass2.ui.ChoiceFileActivity
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.activity.RequestPermissionResult
import com.dylan.uiparts.activity.RequestPermissionResultDispatch
import com.yunsean.dynkotlins.extensions.readPref
import com.yunsean.dynkotlins.extensions.savePref
import com.yunsean.dynkotlins.extensions.toastex
import com.yunsean.dynkotlins.extensions.withNext
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.control_voice.view.*
import okhttp3.MediaType
import okhttp3.RequestBody
import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx
import java.io.File
import java.util.concurrent.TimeUnit


@TargetApi(Build.VERSION_CODES.HONEYCOMB)
class VoiceFragment : ControlFragment() {

    private var fragment: View? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        fragment = activity?.layoutInflater?.inflate(R.layout.control_voice, null)
        builder.setView(fragment)
        builder.setTitle(if (entity?.showName.isNullOrEmpty()) entity?.friendlyName else entity?.showName)
        return builder.create()
    }
    fun calcViewScreenLocation(view: View?): Rect {
        if (view == null) return Rect()
        return Rect(0, 0, view.width, view.height)
    }
    private var isInViewBound = true
    override fun onResume() {
        super.onResume()
        fragment?.apply {
            button_close.onClick {
                dismiss()
            }
            talk.setOnTouchListener { view, motionEvent ->
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isInViewBound = true
                        beginRecord()
                    }
                    MotionEvent.ACTION_UP -> {
                        if (calcViewScreenLocation(fragment?.talk).contains(motionEvent.x.toInt(), motionEvent.y.toInt())) {
                            stopRecord()
                        } else {
                            cancelRecord()
                        }
                    }
                    MotionEvent.ACTION_MOVE-> {
                        val isIn = calcViewScreenLocation(fragment?.talk).contains(motionEvent.x.toInt(), motionEvent.y.toInt())
                        if (isIn && !isInViewBound) {
                            isInViewBound = isIn
                            fragment?.talk?.setImageResource(R.drawable.hass_voice_small_pressed)
                        } else if (!isIn && isInViewBound) {
                            isInViewBound = isIn
                            fragment?.talk?.setImageResource(R.drawable.hass_voice_small_cancel)
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> cancelRecord()
                }
                true
            }
            file.onClick {
                startActivityForResult(Intent(ctx, ChoiceFileActivity::class.java)
                        .putExtra("title", "选择音频文件")
                        .putExtra("currentDirectory", ctx.readPref("VoiceFilePath"))
                        .putExtra("extensionNames", arrayListOf("mp3", "aac", "flac", "wav")), 108)
            }
            volume.setOnProgressChangeListener(object: DiscreteSeekBar.OnProgressChangeListener {
                override fun onProgressChanged(seekBar: DiscreteSeekBar?, value: Int, fromUser: Boolean) {
                }
                override fun onStartTrackingTouch(seekBar: DiscreteSeekBar?) { }
                override fun onStopTrackingTouch(seekBar: DiscreteSeekBar?) {
                    volume_value.setText(volume.progress.toString())
                }
            })
        }
        refreshUi()
    }
    override var dismissWhenPause: Boolean = false
    override fun onPause() {
        cancelRecord()
        super.onPause()
    }
    @ActivityResult(requestCode = 108)
    private fun afterPickFile(data: Intent?) {
        val file = data?.getStringExtra("path")
        if (file.isNullOrBlank()) return
        val filePath = File(file)
        ctx.savePref("VoiceFilePath", filePath.absolutePath)
        upload(filePath)
    }
    private fun upload(filePath: File) {
        fragment?.talk?.visibility = View.GONE
        fragment?.uploading?.visibility = View.VISIBLE
        fragment?.file?.isEnabled = false
        fragment?.uploading?.progress = 0
        val body = RequestBody.create(MediaType.parse("application/x-www-form-urlencoded"), filePath)
        val fileBody = FileRequestBody(body) {total, progress, percent ->
            act.runOnUiThread { fragment?.uploading?.progress = (progress * 100 / total).toInt() }
        }
        BaseApi.rawApi(cfg.haHostUrl, HttpRawApi::class.java)
                .sendVoice(cfg.haPassword, cfg.haToken, fileBody, fragment?.volume?.progress)
                .withNext {
                    ctx.toastex("发送语音文件完成")
                    fragment?.postDelayed({ dismiss() }, 1000)
                }
                .error {
                    ctx.toastex("发送语音文件失败：${it.getMessage()}")
                    fragment?.talk?.visibility = View.VISIBLE
                    fragment?.uploading?.visibility = View.GONE
                    fragment?.file?.isEnabled = true
                }
                .subscribeOnMain {
                    if (disposable == null) disposable = CompositeDisposable(it)
                    else disposable?.add(it)
                }
    }
    private var audioRecord: MediaRecorder? = null
    private val filePath by lazy {
        File(ctx.cacheDir, "voice_temp.aac")
    }
    private var timer: Disposable? = null
    private fun beginRecord() {
        RequestPermissionResultDispatch.requestPermissions(this, 108, arrayOf(Manifest.permission.RECORD_AUDIO))
    }
    @RequestPermissionResult(requestCode = 108)
    private fun doRecord() {
        try {
            cancelRecord()
            muteAudio(true)
            try { filePath.delete() } catch (_: Exception) { }
            audioRecord = MediaRecorder()
            audioRecord?.apply {
                setAudioChannels(1)
                setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(filePath.absolutePath)
                prepare()
                start()
            }
        } catch (_: Exception) {
            return ctx.toastex("启动录音失败，请检查是否未关闭语音唤醒！")
        }
        fragment?.talk?.setImageResource(R.drawable.hass_voice_small_pressed)
        Observable.interval(200, TimeUnit.MILLISECONDS).withNext {
            val db = (audioRecord?.maxAmplitude ?: 0) / 3000
            when (db) {
                0 -> fragment?.status?.setImageResource(R.drawable.ic_volume_1)
                1 -> fragment?.status?.setImageResource(R.drawable.ic_volume_2)
                2 -> fragment?.status?.setImageResource(R.drawable.ic_volume_3)
                3 -> fragment?.status?.setImageResource(R.drawable.ic_volume_4)
                4 -> fragment?.status?.setImageResource(R.drawable.ic_volume_5)
                5 -> fragment?.status?.setImageResource(R.drawable.ic_volume_6)
                6 -> fragment?.status?.setImageResource(R.drawable.ic_volume_7)
                else -> fragment?.status?.setImageResource(R.drawable.ic_volume_8)
            }
        }.subscribeOnMain {
            timer = it
        }
    }
    private fun cancelRecord() {
        try {
            timer?.dispose()
            fragment?.status?.setImageResource(R.drawable.ic_volume_0)
            fragment?.talk?.setImageResource(R.drawable.hass_voice_small_normal)
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        muteAudio(false)
    }
    private fun stopRecord() {
        try {
            timer?.dispose()
            fragment?.status?.setImageResource(R.drawable.ic_volume_0)
            fragment?.talk?.setImageResource(R.drawable.hass_voice_small_normal)
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            upload(filePath)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        muteAudio(false)
    }
    private fun refreshUi() {
        fragment?.apply {
            volume.progress = entity?.attributes?.volume?.toInt() ?: 50
            volume_value.text = volume.progress.toString()
        }
    }
    override fun onChange() = refreshUi()
    private fun muteAudio(mute: Boolean) {
        if (Build.VERSION.SDK_INT >= 26) {
            if (mute) (act.getSystemService(Context.AUDIO_SERVICE) as AudioManager).requestAudioFocus(AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).build())
            else (act.getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).build())
        } else {
            if (mute) (act.getSystemService(Context.AUDIO_SERVICE) as AudioManager).requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            else (act.getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocus(null)
        }
    }
}
