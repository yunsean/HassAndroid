package cn.com.thinkwatch.ihass2.ui

import android.os.Bundle
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.fragment.ConfFragment


class ConfActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_container)
        setTitle("连接到HA", true)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)
        supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, ConfFragment())
                .commit()
    }
}

