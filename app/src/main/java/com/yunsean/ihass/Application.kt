package com.yunsean.ihass

import cn.com.thinkwatch.ihass2.HassApplication
import com.tencent.bugly.Bugly

class Application: HassApplication() {
    override fun onCreate() {
        super.onCreate()
        Bugly.init(getApplicationContext(), "3217dc351d", false)
    }
}