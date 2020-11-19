package cn.com.thinkwatch.ihass2.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Vibrator
import cn.bingoogolapple.qrcode.core.QRCodeView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import com.dylan.common.data.StrUtil
import com.dylan.uiparts.views.ToastEx
import kotlinx.android.synthetic.main.activity_hass_qr_scanner.*
import java.io.IOException

class QrScannerActivity : BaseActivity(), QRCodeView.Delegate {

    private var mediaPlayer: MediaPlayer? = null
    private var playBeep: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra("title")
        val resId = intent.getIntExtra("resId", R.layout.activity_hass_qr_scanner)
        setContentViewNoTitlebar(resId)
        if (StrUtil.isBlank(title))
            setTitle("二维码扫描", true)
        else
            setTitle(title, true)
        initBeepSoundAndVibrate()
        zxingview.setDelegate(this)
    }

    override fun onStart() {
        super.onStart()
        zxingview.startCamera()
        zxingview.startSpot()
    }

    override fun onStop() {
        zxingview.stopCamera()
        super.onStop()
    }

    override fun onDestroy() {
        zxingview.onDestroy()
        super.onDestroy()
    }

    override fun onScanQRCodeSuccess(result: String?) {
        playBeepSoundAndVibrate()
        zxingview.stopCamera()
        val data = Intent()
        data.putExtra("code", result)
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    override fun onScanQRCodeOpenCameraError() {
        ToastEx.makeText(this, "抱歉，打开摄像头失败！", ToastEx.LENGTH_SHORT).show()
        finish()
    }

    private fun initBeepSoundAndVibrate() {
        playBeep = true
        val audioService = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioService.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            playBeep = false
        }
        if (playBeep && mediaPlayer == null) {
            volumeControlStream = AudioManager.STREAM_MUSIC
            mediaPlayer = MediaPlayer()
            mediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
            mediaPlayer!!.setOnCompletionListener { mediaPlayer!!.seekTo(0) }
            val file = resources.openRawResourceFd(R.raw.beep)
            try {
                mediaPlayer!!.setDataSource(file.fileDescriptor, file.startOffset, file.length)
                file.close()
                mediaPlayer!!.setVolume(BEEP_VOLUME, BEEP_VOLUME)
                mediaPlayer!!.prepare()
            } catch (e: IOException) {
                mediaPlayer = null
            }

        }
    }

    private fun playBeepSoundAndVibrate() {
        if (playBeep && mediaPlayer != null) {
            mediaPlayer!!.start()
        }
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VIBRATE_DURATION)
    }

    companion object {
        private val BEEP_VOLUME = 0.30f
        private val VIBRATE_DURATION = 200L
    }
}
