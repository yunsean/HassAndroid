package cn.com.thinkwatch.ihass2.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import com.yunsean.dynkotlins.extensions.logis
import com.yunsean.dynkotlins.extensions.toastex

class LauncherActivity : AppCompatActivity() {

    private lateinit var serviceConnection: ServiceConnection
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra("packageName")
        if (packageName.isNullOrBlank()) {
            try {
                val intent = Intent()
                val cmp = ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
                intent.action = Intent.ACTION_MAIN
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.component = cmp
                startActivity(intent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        } else {
            try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (ex: Exception) {
                toastex("该应用无法被启动！")
                ex.printStackTrace()
            }
        }
        openService(packageName, "cn.com.thinkwatch.ihass2.service.DataSyncService", "cn.com.thinkwatch.ihass2.service.DataSyncService")
        finish()
    }
    private fun openService(packageName: String, className: String, actionName: String? = null) {
        try {
            val intent = Intent()
            intent.action = if (actionName.isNullOrBlank()) null else actionName
            intent.component = ComponentName(packageName, className)
            serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(className: ComponentName, service: IBinder) {
                    finish()
                    System.exit(0)
                }

                override fun onServiceDisconnected(arg0: ComponentName) = Unit
            }
            applicationContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}
