package cn.com.thinkwatch.ihass2.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import cn.com.thinkwatch.ihass2.bus.AdminEnabled
import com.dylan.common.rx.RxBus2

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        RxBus2.getDefault().post(AdminEnabled())
        super.onEnabled(context, intent)
    }
    override fun onDisabled(context: Context, intent: Intent) = super.onDisabled(context, intent)
    override fun onReceive(context: Context, intent: Intent) = super.onReceive(context, intent)
}
