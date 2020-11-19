package cn.com.thinkwatch.ihass2.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.bluetoothManager
import java.util.concurrent.TimeUnit


object BluetoothUtils {
    fun getConnected(context: Context, bluetoothAdapter: BluetoothAdapter, timeout: Long): List<BluetoothDevice> {
        return try {
            getConnected(context, bluetoothAdapter)
                    .timeout(timeout, TimeUnit.SECONDS)
                    .blockingIterable().fold(mutableListOf<BluetoothDevice>(), {a, it-> a.addAll(it); a})
        } catch (_: Exception) {
            listOf()
        }
    }
    fun getConnected(context: Context, bluetoothAdapter: BluetoothAdapter): Observable<List<BluetoothDevice>> {
         return Observable.mergeArray(
                 getConnected(BluetoothProfile.GATT, context),
                 getConnected(BluetoothProfile.GATT_SERVER, context),
                 getConnected(BluetoothProfile.A2DP, context, bluetoothAdapter),
                 getConnected(BluetoothProfile.HEADSET, context, bluetoothAdapter),
                 getConnected(BluetoothProfile.HEALTH, context, bluetoothAdapter),
                 getConnected(BluetoothProfile.GATT, context, bluetoothAdapter),
                 getConnected(BluetoothProfile.GATT_SERVER, context, bluetoothAdapter))
                 .subscribeOn(Schedulers.computation())
    }
    private fun getConnected(profile: Int, context: Context): Observable<List<BluetoothDevice>> {
        return Observable.create<List<BluetoothDevice>> { emitter->
            try {
                context.bluetoothManager.getConnectedDevices(profile)?.let { it ->
                    emitter.onNext(it)
                }
            } catch (_: Exception) {
            }
            emitter.onComplete()
        }
    }
    private fun getConnected(profile: Int, context: Context, bluetoothAdapter: BluetoothAdapter): Observable<List<BluetoothDevice>> {
        return Observable.create<List<BluetoothDevice>> { emitter->
            if (bluetoothAdapter.getProfileConnectionState(profile) == BluetoothProfile.STATE_CONNECTED) {
                try {
                    bluetoothAdapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                        override fun onServiceDisconnected(profile: Int) {
                            emitter.onComplete()
                        }
                        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                            proxy.connectedDevices?.let {it->
                                emitter.onNext(proxy.getConnectedDevices())
                            }
                            emitter.onComplete()
                        }
                    }, profile)
                } catch (_: Exception) {
                    emitter.onComplete()
                }
            } else {
                emitter.onComplete()
            }
        }
    }
}