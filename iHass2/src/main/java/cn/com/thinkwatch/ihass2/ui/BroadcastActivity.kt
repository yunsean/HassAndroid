package cn.com.thinkwatch.ihass2.ui

import android.os.Bundle
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity


class BroadcastActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_container)
        val entityId = intent.getStringExtra("entityId")
        val isQtfm = "broadcast.qtfm" == entityId
        setTitle(if (isQtfm) "蜻蜓FM" else "喜马拉雅FM", true)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)
        supportActionBar?.elevation = 0F
        val fragment = if (isQtfm) cn.com.thinkwatch.ihass2.fragment.qtfm.MainFragment() else cn.com.thinkwatch.ihass2.fragment.xmly.MainFragment()
        val argments = Bundle()
        argments.putString("entityId", entityId)
        fragment.arguments = argments
        supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, fragment)
                .commit()
    }
}

