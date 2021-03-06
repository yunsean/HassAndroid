package com.yunsean.ihass

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.ui.ConfActivity
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.common.sketch.Dialogs
import com.dylan.common.utils.Utility
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.activity.ActivityResultDispatch
import com.dylan.uiparts.activity.RequestPermissionResult
import com.dylan.uiparts.activity.RequestPermissionResultDispatch
import com.yunsean.dynkotlins.extensions.activity
import com.yunsean.dynkotlins.extensions.loges
import kotlinx.android.synthetic.main.activity_app_splash.*

class SplashActivity : com.dylan.common.application.SplashActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSplashImage(R.drawable.splash)
        setShowSplashTime(1000)
        version.text = "iHass v" + Utility.getVerName(this)
    }

    override fun needCheckNetwork(): Boolean {
        return false
    }
    override fun doInBackgroundWhenShowSplash(): Any? {
        val begin = System.currentTimeMillis()
        val icons = MDIFont.get().icons
        loges("load ${icons.size} mdies consumed: ${System.currentTimeMillis() - begin} ms")
        return null
    }
    override fun shouldGoHomeAfterSplash(resultOfInBackground: Any?) {
        RequestPermissionResultDispatch.requestPermissions(this, 100, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }
    override fun shouldGoGuideAfterSplash(resultOfInBackground: Any?) {
        shouldGoHomeAfterSplash(resultOfInBackground)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        RequestPermissionResultDispatch.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        ActivityResultDispatch.onActivityResult(this, requestCode, resultCode, data)
    }
    @RequestPermissionResult(requestCode = 100)
    private fun afterPermission(result: Boolean) {
        if (!result) {
            Dialogs.showMessage(this, "需要权限", "需要读写外部存储的权限才能正确运行，请选择允许！", "重新选择", "退出程序", { _, _ ->
                shouldGoHomeAfterSplash(null)
            }) { _, _ ->
                finish()
            }
        } else {
            if (!cfg.isInited() || !db.isInited()) {
                activity(ConfActivity::class.java, 100)
            } else {
                activity(MainActivity::class.java)
                finish()
            }
        }
    }
    @ActivityResult(requestCode = 100)
    private fun afterConnect(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            activity(MainActivity::class.java)
        }
        finish()
    }
}
