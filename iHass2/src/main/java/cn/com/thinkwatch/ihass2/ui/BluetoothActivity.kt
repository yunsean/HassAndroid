package cn.com.thinkwatch.ihass2.ui

import android.app.Activity
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.utils.BluetoothUtils
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.dip2px
import com.yunsean.dynkotlins.extensions.nextOnMain
import com.yunsean.dynkotlins.extensions.toastex
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.activity_hass_bluetooth.*
import kotlinx.android.synthetic.main.listitem_hass_bluetooth.view.*
import org.jetbrains.anko.bluetoothManager
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick


class BluetoothActivity : BaseActivity() {

    private var multipe = false
    private var checkedAddresses = mutableListOf<String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_bluetooth)
        multipe = intent.getBooleanExtra("multiple", false)
        intent.getStringArrayExtra("addresses")?.let { checkedAddresses.addAll(it) }
        intent.getStringExtra("address").let { if (!it.isNullOrBlank()) checkedAddresses.add(it) }
        if (multipe) setTitle("选择蓝牙设备", true, "确定")
        else setTitle("选择蓝牙设备", true)
        checkedAddresses.forEach { bluetoothDevices.add(BluetoothItem(it, "已选定的蓝牙")) }

        ui()
        data()
    }
    override fun onDestroy() {
        bluetoothScanCallback?.let {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(it)
        }
        super.onDestroy()
    }
    override fun doRight() {
        val data = Intent().putExtra("addresses", checkedAddresses.filter { it.isNotBlank() }.toTypedArray())
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private data class BluetoothItem(val address: String,
                                     var name: String?,
                                     var connected: Boolean = false)
    private var bluetoothDevices = mutableListOf<BluetoothItem>()
    private lateinit var adapter: RecyclerAdapter<BluetoothItem>
    private fun ui() {
        this.adapter = RecyclerAdapter(R.layout.listitem_hass_bluetooth, bluetoothDevices) {
            view, index, item ->
            view.name.setText(item.name)
            view.address.setText(item.address)
            view.checked.visibility = if (checkedAddresses.contains(item.address)) View.VISIBLE else View.GONE
            view.icon.text = if (item.connected) "\uf0b1" else "\uf0af"
            view.onClick {
                if (!multipe) {
                    checkedAddresses.clear()
                    checkedAddresses.add(item.address)
                } else if (checkedAddresses.contains(item.address)) {
                    checkedAddresses.remove(item.address)
                } else {
                    checkedAddresses.add(item.address)
                }
                adapter.notifyDataSetChanged()
                if (!multipe) {
                    val data = Intent().putExtra("address", item.address)
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
        this.pullable.setOnRefreshListener {
            bluetoothDevices.clear()
            data()
            pullable.isRefreshing = false
        }
    }

    private val bluetoothAdapter by lazy { bluetoothManager.getAdapter() }
    private var bluetoothScanCallback: ScanCallback? = null
    private fun data() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) return error()
        if (bluetoothAdapter == null) return error()
        if (!bluetoothAdapter!!.isEnabled) return error("请先从系统设置中打开蓝牙开关")
        BluetoothUtils.getConnected(this, bluetoothAdapter).nextOnMain {
           it.forEach { d->
               val found = bluetoothDevices.find { it.address == d.address }
               if (found == null) {
                   bluetoothDevices.add(d.let { BluetoothItem(it.address, it.name, true) })
               } else {
                   if (!found.name.isNullOrBlank()) found.name = d.name
                   found.connected = true
               }
               adapter.notifyDataSetChanged()
           }
        }
        bluetoothScanCallback = object: ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.device?.let { d->
                    var found = bluetoothDevices.find { it.address == d.address }
                    if (found == null) {
                        bluetoothDevices.add(d.let { BluetoothItem(it.address, it.name) })
                        adapter.notifyDataSetChanged()
                        loading?.visibility = View.GONE
                    } else if (!d.name.isNullOrBlank()) {
                        found.name = d.name
                    }
                }
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach {r->
                    val found = bluetoothDevices.find { it.address == r.device.address }
                    if (found == null) {
                        bluetoothDevices.add(r.device.let { BluetoothItem(it.address, it.name) })
                    } else if (!r.device.name.isNullOrBlank()) {
                        found.name = r.device.name
                    }
                }
                adapter.notifyDataSetChanged()
                if (bluetoothDevices.size > 0) loading?.visibility = View.GONE
            }
            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                if (errorCode != ScanCallback.SCAN_FAILED_ALREADY_STARTED && bluetoothDevices.size < 1) {
                    error("查找蓝牙设备失败：${errorCode}")
                }
            }
        }
        bluetoothScanCallback?.let {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(it)
            bluetoothAdapter?.bluetoothLeScanner?.startScan(null, ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(if (bluetoothAdapter?.isOffloadedScanBatchingSupported ?: false) 1_000 else 0)
                    .build(), it)
        }
    }
    private fun error(message: String = "您的设备不支持蓝牙") {
        toastex(message)
        finish()
    }
}

