package cn.com.thinkwatch.ihass2.voice

data class DetailItem(val display: String,
                      val data: Any? = null)
enum class FinishAction{close, reset, halt}
interface VoiceController {
    fun register(pattern: String, handler: VoiceHandler)
    fun setStatus(status: String)                                                                       //无TTS
    fun setTips(reason: String, waiting: Boolean = false, tts: Boolean = true, save: Boolean = false)   //tts将TTS
    fun setDetail(details: List<DetailItem>, clickHandler: VoiceHandler? = null)
    fun setInput(tips: String, handler: VoiceHandler?)                                                  //TTS后开始侦听
    fun finish(action: FinishAction, tips: String? = null, closeTimeout: Long = 2000)                                              //tips将TTS
}

interface VoiceHandler {
    fun setup(controller: VoiceController)
    fun handle(result: String, more: Boolean, blindly: Boolean): Boolean
    fun detailClicked(item: DetailItem)
    fun reset()
}