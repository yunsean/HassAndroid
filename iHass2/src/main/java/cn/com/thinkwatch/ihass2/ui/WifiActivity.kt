package cn.com.thinkwatch.ihass2.ui

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import com.dylan.uiparts.activity.RequestPermissionResult
import com.dylan.uiparts.activity.RequestPermissionResultDispatch
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.dip2px
import com.yunsean.dynkotlins.extensions.toastex
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.activity_hass_wifi.*
import kotlinx.android.synthetic.main.listitem_hass_wifi.view.*
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick


class WifiActivity : BaseActivity() {

    private var multipe = false
    private val checkedBssids = mutableListOf<String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_wifi)

        multipe = intent.getBooleanExtra("multiple", false)
        intent.getStringArrayExtra("bssids")?.let { checkedBssids.addAll(it) }
        intent.getStringExtra("bssid").let { if (!it.isNullOrBlank()) checkedBssids.add(it) }
        if (multipe) setTitle("选择WIFI设备", true, "确定")
        else setTitle("选择WIFI设备", true)

        ui()
        RequestPermissionResultDispatch.requestPermissions(this, 101, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    override fun onDestroy() {
        broadcastReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }
    override fun doRight() {
        val data = Intent().putExtra("bssids", checkedBssids.toTypedArray())
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private lateinit var adapter: RecyclerAdapter<WifiItem>
    private fun ui() {
        this.adapter = RecyclerAdapter(R.layout.listitem_hass_wifi, null) {
            view, index, item ->
            view.name.setText(if (item.SSID.isNullOrBlank()) item.BSSID else item.SSID)
            view.checked.visibility = if (checkedBssids.contains(item.BSSID)) View.VISIBLE else View.GONE
            view.onClick {
                if (!multipe) {
                    checkedBssids.clear()
                    checkedBssids.add(item.BSSID)
                } else if (checkedBssids.contains(item.BSSID)) {
                    checkedBssids.remove(item.BSSID)
                } else {
                    checkedBssids.add(item.BSSID)
                }
                adapter.notifyDataSetChanged()
                if (!multipe) {
                    val data = Intent().putExtra("bssid", item.BSSID)
                    setResult(Activity.RESULT_OK, data)
                    finish()
                }
            }
        }
        this.recyclerView.adapter = this.adapter
        this.recyclerView.layoutManager = LinearLayoutManager(ctx)
        this.recyclerView.addItemDecoration(RecyclerViewDivider()
                .setColor(0xffeeeeee.toInt())
                .setSize(dip2px(1f)))
    }

    private data class WifiItem(val BSSID: String,
                                val SSID: String?)

    private var wifiManager: WifiManager? = null
    private var broadcastReceiver: BroadcastReceiver? = null
    @RequestPermissionResult(requestCode = 101)
    private fun data() {
        broadcastReceiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                when (intent?.action) {
                    WifiManager.SCAN_RESULTS_AVAILABLE_ACTION-> {
                        val result = wifiManager?.scanResults
                        val items = mutableListOf<WifiItem>()
                        result?.let { items.addAll(it.map { WifiItem(it.BSSID, it.SSID) }) }
                        items.addAll(checkedBssids.filter {bssid-> items.find { it.BSSID == bssid } == null }.map { WifiItem(it, it) })
                        adapter.items = items
                        loading?.visibility = View.GONE
                    }
                }
            }
        }
        val filter = IntentFilter()
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(broadcastReceiver, filter)
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!(wifiManager?.isWifiEnabled ?: false)) return error("请在系统设置中打开WIFI开关")
        if (Build.VERSION.SDK_INT >= 23) {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) &&
                    !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                return error("Android 6.0以上版本获取WIFI列表需要打开定位功能，请在系统设置中打开定位开关")
            }
        }
        wifiManager?.startScan()
    }
    private fun error(message: String = "您的设备不支持WIFI") {
        toastex(message)
        finish()
    }
}

