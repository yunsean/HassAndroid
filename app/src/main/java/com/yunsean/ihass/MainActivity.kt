package com.yunsean.ihass

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import cn.com.thinkwatch.ihass2.base.BaseActivity

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewNoTitlebar(R.layout.activity_main)
    }

    internal var waitTime: Long = 2000
    internal var touchTime: Long = 0
    override fun onBackPressed() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - touchTime >= waitTime) {
            Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show()
            touchTime = currentTime
        } else {
            val launcherIntent = Intent(Intent.ACTION_MAIN)
            launcherIntent.addCategory(Intent.CATEGORY_HOME)
            startActivity(launcherIntent)
        }
    }
}
