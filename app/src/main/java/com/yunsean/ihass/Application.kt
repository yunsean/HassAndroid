package com.yunsean.ihass

import android.content.Context
import android.support.multidex.MultiDex
import cn.com.thinkwatch.ihass2.HassApplication
import com.tencent.bugly.Bugly

class Application: HassApplication() {
    override fun onCreate() {
        super.onCreate()
        Bugly.init(getApplicationContext(), "3217dc351d", false)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}