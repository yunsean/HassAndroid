package cn.com.thinkwatch.ihass2.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.ServiceIntercepted
import cn.com.thinkwatch.ihass2.fragment.HassFragment
import com.dylan.common.rx.RxBus2
import kotlinx.android.synthetic.main.activity_service_intercept.*

class ServiceInterceptActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewNoTitlebar(R.layout.activity_service_intercept)
        setTitle("服务录制", true, "", R.drawable.ic_action_panels)
        supportActionBar?.elevation = 0F

        disposable = RxBus2.getDefault().register(ServiceIntercepted::class.java, {
            setResult(Activity.RESULT_OK, Intent()
                    .putExtra("serviceId", it.serviceId)
                    .putExtra("content", it.content))
            finish()
        }, disposable)
    }
    override fun onResume() {
        super.onResume()
        app.serviceIntercepting = true
    }
    override fun onPause() {
        app.serviceIntercepting = false
        super.onPause()
    }
    override fun doRight() {
        (this.hass as HassFragment?)?.showPanel(null)
    }
}

