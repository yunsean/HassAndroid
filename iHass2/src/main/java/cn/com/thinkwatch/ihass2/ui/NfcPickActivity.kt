package cn.com.thinkwatch.ihass2.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import com.yunsean.dynkotlins.extensions.toHex
import kotlinx.android.synthetic.main.activity_hass_nfc_read.*

class NfcPickActivity : BaseActivity() {

    private var adapter: NfcAdapter? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_nfc_read)
        setTitle("NFC读取", true)
        adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter == null || !adapter!!.isEnabled) return finish()
        gifView.setGifImage(R.drawable.read_card)
    }
    override fun onResume() {
        super.onResume()
        adapter?.let {
            val techList = arrayOf(arrayOf(android.nfc.tech.IsoDep::class.java.name), arrayOf(android.nfc.tech.NfcA::class.java.name))
            val intentFilter = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
            intentFilter.addDataType("*/*")
            val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0)
            it.enableForegroundDispatch(this, pendingIntent, arrayOf(intentFilter), techList)
        }
    }
    override fun onPause() {
        adapter?.disableForegroundDispatch(this)
        super.onPause()
    }
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        try { intent?.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) } catch (_: Exception) { null }?.let {
            val data = Intent().putExtra("uid", it.id.toHex())
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }
}

