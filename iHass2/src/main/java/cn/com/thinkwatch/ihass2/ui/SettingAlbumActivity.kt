package cn.com.thinkwatch.ihass2.ui

import android.os.Bundle
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.fragment.SettingAlbumFragment
import cn.com.thinkwatch.ihass2.fragment.SettingVoiceFragment


class SettingAlbumActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_container)
        setTitle("相册同步设置", true)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)
        supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, SettingAlbumFragment())
                .commit()
    }
}

