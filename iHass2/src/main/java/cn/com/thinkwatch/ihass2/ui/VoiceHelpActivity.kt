package cn.com.thinkwatch.ihass2.ui

import android.os.Bundle
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import kotlinx.android.synthetic.main.activity_hass_voice_help.*

class VoiceHelpActivity : BaseActivity() {

    /*
    对word生成的网页做如下正则替换：
    <!--[\s\S]*?--> 替换为空
    <!\[if !vml\]> 替换为空
    <!\[endif\]> 替换为空
    <img[\s\S]*?src="(.*?)"[\s\S]*?> 替换为 <img style="width: 100%;" src="$1">
    */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_voice_help)
        setTitle("语音控制说明", true)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.allowFileAccessFromFileURLs = true
        webView.loadUrl("file:///android_asset/html/voice.html")
    }
}