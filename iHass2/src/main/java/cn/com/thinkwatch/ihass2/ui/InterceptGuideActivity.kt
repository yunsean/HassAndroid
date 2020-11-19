package cn.com.thinkwatch.ihass2.ui

import android.content.Intent
import android.os.Bundle
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import com.dylan.uiparts.activity.ActivityResult
import com.yunsean.dynkotlins.extensions.activity
import com.yunsean.dynkotlins.extensions.readPref
import com.yunsean.dynkotlins.extensions.savePref
import kotlinx.android.synthetic.main.activity_intercept_guide.*
import org.jetbrains.anko.sdk25.coroutines.onClick

class InterceptGuideActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intercept_guide)
        setTitle("服务录制", true)

        this.next.onClick {
            savePref("Intercept.DontShowGuid", dontShow.isChecked)
            activity(ServiceInterceptActivity::class.java, 100)
        }
        if (readPref("Intercept.DontShowGuid")?.toBoolean() ?: false) {
            activity(ServiceInterceptActivity::class.java, 100)
        }
    }

    @ActivityResult(requestCode = 100)
    private fun afterIntercept(resultCode: Int, data: Intent?) {
        setResult(resultCode, data)
        finish()
    }
}

