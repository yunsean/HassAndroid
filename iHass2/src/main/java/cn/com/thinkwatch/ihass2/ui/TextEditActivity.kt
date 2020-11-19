package cn.com.thinkwatch.ihass2.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import com.yunsean.dynkotlins.extensions.*
import kotlinx.android.synthetic.main.activity_hass_text_edit.*
import org.jetbrains.anko.act

class TextEditActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_text_edit)
        setTitle("内容编辑", true, "确定")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        val content = intent.getStringExtra("content")
        act.content.setText(content)

        ui()
    }
    override fun doRight() {
        val content = act.content.text()
        setResult(Activity.RESULT_OK, Intent().putExtra("content", content))
        finish()
    }

    private fun ui() {
    }
}

