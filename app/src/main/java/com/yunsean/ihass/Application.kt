package com.yunsean.ihass

import android.content.Context
import android.support.multidex.MultiDex
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.fragment.album.AlbumMainFragment
import com.tencent.bugly.Bugly

class Application: HassApplication() {
    override fun onCreate() {
        super.onCreate()
        Bugly.init(getApplicationContext(), "3217dc351d", false)

        stubbornTabs.add(Companion.FragmentItem("网页", "mdi:web", WebFragment::class.java.name))
        stubbornTabs.add(Companion.FragmentItem("相册", "mdi:image", AlbumMainFragment::class.java.name))
        stubbornTabs.add(Companion.FragmentItem("设置", "mdi:settings", MoreFragment::class.java.name))
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}