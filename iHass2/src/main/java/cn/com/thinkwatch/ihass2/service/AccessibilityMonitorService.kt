package cn.com.thinkwatch.ihass2.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import cn.com.thinkwatch.ihass2.bus.RunningAppEvent
import com.dylan.common.rx.RxBus2


class AccessibilityMonitorService : AccessibilityService() {
    override fun onInterrupt() = Unit
    private var currentPackage: String? = null
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                event.packageName?.toString()?.let {
                    if (currentPackage != it) {
                        currentPackage = it
                        RxBus2.getDefault().post(RunningAppEvent(it))
                    }
                }
            }
            else -> {
            }
        }
    }
}