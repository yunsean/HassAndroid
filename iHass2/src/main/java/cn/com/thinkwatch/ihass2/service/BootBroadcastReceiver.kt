package cn.com.thinkwatch.ihass2.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        var intent = intent
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            intent = Intent(context, DataSyncService::class.java)
            context.startService(intent)
        }
    }
}
