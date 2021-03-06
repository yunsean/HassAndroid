package cn.com.thinkwatch.ihass2.ui

import android.os.Bundle
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity


class XmlyBroadcastActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_container)
        val entityId = intent.getStringExtra("entityId")
        val title = intent.getStringExtra("title")
        setTitle(if (title.isNullOrBlank()) "喜马拉雅" else title, true)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)
        supportActionBar?.elevation = 0F
        val fragment = cn.com.thinkwatch.ihass2.fragment.xmly.MainFragment()
        val argments = Bundle()
        argments.putString("entityId", entityId)
        fragment.arguments = argments
        supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, fragment)
                .commit()
    }
}

