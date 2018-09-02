package com.yunsean.ihass

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import cn.com.thinkwatch.ihass2.base.BaseActivity
import com.yunsean.dynkotlins.extensions.toastex
import kotlinx.android.synthetic.main.activity_share.*
import org.jetbrains.anko.sdk25.coroutines.onClick


class ShareActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share)
        setTitle("分享与反馈", true)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        ui()
    }

    private fun ui() {
        this.copy.onClick {
            try {
                val data = ClipData.newRawUri("iHass下载", Uri.parse("https://www.pgyer.com/ihass"))
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip = data
                toastex("下载地址已复制到剪贴板！")
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
        this.qqGroup.onClick {
            try {
                val key = "0ejzv97MG4PQW_yk__CLIqFTfseJKu9l"
                val intent = Intent()
                intent.setData(Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26k%3D" + key))
                startActivity(intent)
            } catch (ex: Exception) {
                ex.printStackTrace()
                toastex("拉起QQ失败，可能未安装QQ！")
            }
        }

    }
}

