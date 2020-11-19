package cn.com.thinkwatch.ihass2.voice

import android.content.Context
import android.media.AudioManager
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.LongNumerals
import com.yunsean.dynkotlins.extensions.kdateTime
import com.yunsean.dynkotlins.extensions.nextOnMain
import io.reactivex.Observable
import java.util.*
import java.util.regex.Pattern

class SystemHandler: VoiceHandler {
    private val pattern3 by lazy { Pattern.compile("^(音量|声音|静音|调整音量|调整声音|运行|打开|几点|当前时间|导航到)(.*)") }
    private lateinit var controller: VoiceController
    override fun setup(controller: VoiceController) {
        this.controller = controller
        this.controller.register("^(音量|声音|静音|调整音量|调整声音|运行|打开|几点|当前时间|导航到)(.*)", this)
    }
    override fun handle(command: String, more: Boolean, blindly: Boolean): Boolean {
        val m = pattern3.matcher(command)
        if (m.find()) {
            val action = m.group(1)
            val param = m.group(2)
            when (action) {
                "音量", "声音", "调整音量", "调整声音", "静音"-> return doVolume(param)
                "运行", "打开"-> return doRun(param)
                "几点", "当前时间"-> return doTime()
            }
        }
        return false
    }
    override fun detailClicked(item: DetailItem) {

    }
    override fun reset() {

    }

    private fun doTime(): Boolean {
        this.controller.finish(FinishAction.close, "当前时间是：${Date().kdateTime("M月d日H点m分")}")
        return true
    }
    private val allPackages by lazy {
        try {
            val allPackages = mutableListOf<Pair<String, String>>()
            val packageManager = HassApplication.application.packageManager
            val packageInfos = packageManager.getInstalledPackages(0)
            for (packageInfo in packageInfos) {
                if (packageInfo.applicationInfo.loadIcon(packageManager) == null) continue
                val packageName = packageInfo.packageName
                val appName = packageInfo.applicationInfo.loadLabel(packageManager)
                if (appName == null) continue
                allPackages.add(Pair(appName.toString(), packageName))
            }
            allPackages
        } catch (e: Exception) {
            null
        }
    }
    private fun doRun(param: String): Boolean {
        if (!HassConfig.INSTANCE.getBoolean(HassConfig.Speech_VoiceOpenApp)) return false
        if (param.contains("的")) return false
        allPackages?.forEach {
            if (it.first == param) {
                try {
                    val packageManager = HassApplication.application.packageManager
                    val intent = packageManager.getLaunchIntentForPackage(it.second)
                    HassApplication.application.startActivity(intent)
                    controller.finish(FinishAction.close, "打开应用${it.first}完成")
                } catch (ex: Exception) {
                    controller.finish(FinishAction.close, "打开应用${it.first}失败")
                    ex.printStackTrace()
                }
                return@doRun true
            }
        }
        allPackages?.forEach {
            if (it.first.contains(param) || param.contains(it.first)) {
                try {
                    val packageManager = HassApplication.application.packageManager
                    val intent = packageManager.getLaunchIntentForPackage(it.second)
                    HassApplication.application.startActivity(intent)
                    controller.finish(FinishAction.close, "打开应用${it.first}完成")
                } catch (ex: Exception) {
                    controller.finish(FinishAction.close, "打开应用${it.first}失败")
                    ex.printStackTrace()
                }
                return@doRun true
            }
        }
        return false
    }
    
    private fun finish(message: String) {
        Observable.just(message).nextOnMain {
            controller.finish(FinishAction.close, message)
        }
    }

    private val patternNumber by lazy { Pattern.compile("(\\d{1,3})") }
    private fun doVolume(param: String): Boolean {
        var param = param
        if (LongNumerals.numberPattern.matcher(param).find()) {
            var matched: String? = null
            var start = 0
            var end = 0
            val m = LongNumerals.numberPattern.matcher(param)
            while (m.find()) {
                start = m.start();
                end = m.end();
                matched = m.group(0);
            }
            if (matched == null) return false
            param = param.substring(0, start) + LongNumerals.convert(matched).toString() + param.substring(end)
        }
        val audioManager = HassApplication.application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (param.contains("最小") || param.contains("静音")) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_PLAY_SOUND)
            finish("已设置静音")
            return true
        } else if (param.contains("最大") || param.contains("静音")) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_PLAY_SOUND)
            finish("已设置最大音量")
            return true
        } else if (param.contains("太大") || (param.contains("小") && !param.contains("太小"))) {
            val index = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (index > 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index - 1, AudioManager.FLAG_PLAY_SOUND)
                finish("音量已调小到${index - 1}")
            } else {
                finish("当前已静音")
            }
            return true
        } else if (param.contains("太小") || (param.contains("大") && !param.contains("太大"))) {
            val index = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (index < audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index + 1, AudioManager.FLAG_PLAY_SOUND)
                finish("音量已调大到${index + 1}")
            } else {
                finish("当前已是最大音量")
            }
            return true
        } else {
            val m = patternNumber.matcher(param)
            if (m.find()) {
                var number = m.group(1).toIntOrNull()
                if (number != null) {
                    number = number % 100
                    val index = number * audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 100
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index + 1, AudioManager.FLAG_PLAY_SOUND)
                    finish("已经调整音量到百分之$number")
                    return true
                }
            }
        }
        return false
    }
}