package cn.com.thinkwatch.ihass2.ui

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import cn.com.thinkwatch.ihass2.base.BaseActivity
import com.yunsean.dynkotlins.extensions.toHex
import com.yunsean.dynkotlins.extensions.toastex

class NfcReadActivity : BaseActivity() {

    private var nfcAdapter: NfcAdapter? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent != null && NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.action)) {
            val tag: Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            val cardId = tag.id.toHex()
            toastex(cardId)
        }
        finish()
    }
}

