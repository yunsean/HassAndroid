package cn.com.thinkwatch.ihass2.ui

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import com.yunsean.dynkotlins.extensions.toHex


class NfcReadActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!cfg.getBoolean(HassConfig.Probe_NfcCard)) return finish()
        if (intent != null) {
            try { intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) } catch (_: Exception) { null }?.let {
                it.id.toHex()?.let { app.nfcTrigger(it) }
            }
        }
        finish()
    }
}

