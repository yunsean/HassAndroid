package cn.com.thinkwatch.ihass2.utils

import android.content.Context
import android.media.AudioManager
import android.os.Environment
import android.widget.Toast
import com.baidu.tts.client.SpeechError
import com.baidu.tts.client.SpeechSynthesizer
import com.baidu.tts.client.SpeechSynthesizerListener
import com.baidu.tts.client.TtsMode
import com.yunsean.dynkotlins.extensions.loges
import com.yunsean.dynkotlins.extensions.toastex
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream


class TtsEngine {

    private var speechSynthesizer: SpeechSynthesizer? = null
    private var sampleDirPath: String? = null
    private var onSpeechFinished: (()->Unit)? = null
    fun initialEnv(context: Context, useFemale: Boolean = false): Boolean {
        if (sampleDirPath == null) {
            val sdcardPath = Environment.getExternalStorageDirectory().toString()
            sampleDirPath = sdcardPath + "/" + SAMPLE_DIR_NAME
        }
        File(sampleDirPath!!).let { if (!it.exists()) it.mkdir() }
        copyFromAssetsToSdcard(context, SPEECH_FEMALE_MODEL_NAME, sampleDirPath + "/" + SPEECH_FEMALE_MODEL_NAME)
        copyFromAssetsToSdcard(context, SPEECH_MALE_MODEL_NAME, sampleDirPath + "/" + SPEECH_MALE_MODEL_NAME)
        copyFromAssetsToSdcard(context, TEXT_MODEL_NAME, sampleDirPath + "/" + TEXT_MODEL_NAME)
        copyFromAssetsToSdcard(context, "english/" + ENGLISH_SPEECH_FEMALE_MODEL_NAME, sampleDirPath + "/" + ENGLISH_SPEECH_FEMALE_MODEL_NAME)
        copyFromAssetsToSdcard(context, "english/" + ENGLISH_SPEECH_MALE_MODEL_NAME, sampleDirPath + "/" + ENGLISH_SPEECH_MALE_MODEL_NAME)
        copyFromAssetsToSdcard(context, "english/" + ENGLISH_TEXT_MODEL_NAME, sampleDirPath + "/" + ENGLISH_TEXT_MODEL_NAME)
        speechSynthesizer = SpeechSynthesizer.getInstance()
        speechSynthesizer?.let {
            it.setContext(context)
            it.setSpeechSynthesizerListener(object : SpeechSynthesizerListener {
                override fun onSynthesizeStart(arg0: String) {
                }
                override fun onSynthesizeFinish(arg0: String) {
                }
                override fun onSynthesizeDataArrived(arg0: String, arg1: ByteArray, arg2: Int) {
                }
                override fun onSpeechStart(arg0: String) {
                }
                override fun onSpeechProgressChanged(arg0: String, arg1: Int) {
                }
                override fun onSpeechFinish(arg0: String) {
                    onSpeechFinished?.invoke()
                }
                override fun onError(arg0: String, arg1: SpeechError) {
                }
            })
            it.setParam(SpeechSynthesizer.PARAM_TTS_TEXT_MODEL_FILE, sampleDirPath + "/" + TEXT_MODEL_NAME)
            it.setParam(SpeechSynthesizer.PARAM_TTS_SPEECH_MODEL_FILE, sampleDirPath + "/" + (if (useFemale) SPEECH_FEMALE_MODEL_NAME else SPEECH_MALE_MODEL_NAME))
            it.setAppId("9798958")
            it.setApiKey("wYs0HxoEceVvR7pcFvAGhQfI", "449b6126cd7d9200021d1e549ebc6cfe")
            it.setParam(SpeechSynthesizer.PARAM_SPEAKER, "0")
            it.setParam(SpeechSynthesizer.PARAM_SPEED, "6")
            it.setParam(SpeechSynthesizer.PARAM_PITCH, "8")
            it.setParam(SpeechSynthesizer.PARAM_VOLUME, "10")
            it.setParam(SpeechSynthesizer.PARAM_MIX_MODE, SpeechSynthesizer.MIX_MODE_HIGH_SPEED_SYNTHESIZE_WIFI)
            it.setAudioStreamType(AudioManager.STREAM_MUSIC)
            it.setStereoVolume(0.8f, 0.8f)
            val authInfo = it.auth(TtsMode.MIX)
            if (!authInfo.isSuccess) {
                context.toastex("TTS授权失败：${authInfo.ttsError.detailMessage}", Toast.LENGTH_LONG)
                loges("TTS授权失败：${authInfo.ttsError.detailMessage}")
                return false
            }
            it.initTts(TtsMode.MIX)
            it.loadEnglishModel(sampleDirPath + "/" + ENGLISH_TEXT_MODEL_NAME, sampleDirPath + "/" + ENGLISH_SPEECH_FEMALE_MODEL_NAME)
        }
        return true
    }
    fun speech(text: String, callback: (()->Unit)? = null) {
        onSpeechFinished = callback
        speechSynthesizer?.stop()
        speechSynthesizer?.speak(text)
    }
    fun stop() {
        speechSynthesizer?.stop()
    }

    private val SAMPLE_DIR_NAME = "baiduTTS"
    private val SPEECH_FEMALE_MODEL_NAME = "bd_etts_speech_female.dat"
    private val SPEECH_MALE_MODEL_NAME = "bd_etts_speech_male.dat"
    private val TEXT_MODEL_NAME = "bd_etts_text.dat"
    private val ENGLISH_SPEECH_FEMALE_MODEL_NAME = "bd_etts_speech_female_en.dat"
    private val ENGLISH_SPEECH_MALE_MODEL_NAME = "bd_etts_speech_male_en.dat"
    private val ENGLISH_TEXT_MODEL_NAME = "bd_etts_text_en.dat"
    private fun copyFromAssetsToSdcard(context: Context, source: String, dest: String) {
        File(dest).let {
            if (!it.exists()) {
                var inputStream: InputStream? = null
                var fileOutputStream: FileOutputStream? = null
                try {
                    inputStream = context.resources.assets.open(source)
                    val path = dest
                    fileOutputStream = FileOutputStream(path)
                    val buffer = ByteArray(1024)
                    var size = inputStream!!.read(buffer, 0, 1024)
                    while (size >= 0) {
                        fileOutputStream!!.write(buffer, 0, size)
                        size = inputStream!!.read(buffer, 0, 1024)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    fileOutputStream?.let { try { it.close() } catch (e: IOException) { e.printStackTrace() } }
                    inputStream?.let { try { it.close() } catch (ex: Exception) { ex.printStackTrace() } }
                }
            }
        }
    }

    companion object {
        fun get(): TtsEngine {
            return Inner.instance
        }
    }
    private object Inner {
        val instance = TtsEngine()
    }
}
