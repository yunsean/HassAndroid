package cn.com.thinkwatch.ihass2.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.TriggerChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.enums.TriggerType
import cn.com.thinkwatch.ihass2.model.EventTrigger
import com.dylan.common.rx.RxBus2
import com.dylan.dyn3rdparts.pickerview.DateTimePicker
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import kotlinx.android.synthetic.main.activity_hass_trigger_edit.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.util.*

class TriggerEditActivity : BaseActivity() {

    private var triggerId: Long = 0L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_trigger_edit)
        setTitle("场景触发", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        triggerId = intent.getLongExtra("triggerId", 0L)
        ui()
        if (triggerId != 0L) {
            db.getTrigger(triggerId)?.let {e->
                triggerTypes.find { it == e.type }?.let {
                    type.setText(it.desc)
                    typeIcon.setImageResource(it.iconResId)
                    mTriggerType = it
                }
                mBeginTime = e.beginTime
                mEndTime = e.endTime
                mServiceId = e.serviceId
                name.setText(e.name)
                beginTime.setText(mBeginTime?.kdateTime("HH:mm:ss") ?: "")
                endTime.setText(mEndTime?.kdateTime("HH:mm:ss") ?: "")
                service.setText(mServiceId ?: "")
                params.setText(e.params)
                notify.isChecked = e.notify
                content.setText(e.content)
            }
        }
    }
    override fun doRight() {
        if (mServiceId == null) return showError("请选择服务！")
        val params = this.params.text()
        val name = this.name.text()
        val content = this.content.text()
        val notify = this.notify.isChecked
        if (mTriggerType != TriggerType.screenOff && mTriggerType != TriggerType.screenOn && params.isBlank()) return showError("请选择场景参数！")
        if (name.isBlank()) return showError("请输入名称！")
        val trigger = EventTrigger(mTriggerType, params, name, mServiceId!!, mBeginTime, mEndTime, content, notify, false, triggerId)
        if (triggerId == 0L) db.addTrigger(trigger)
        else db.saveTrigger(trigger)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private val triggerTypes = mutableListOf(TriggerType.enterGps, TriggerType.leaveGps,
            TriggerType.foundWifi, TriggerType.loseWifi,
            TriggerType.screenOn, TriggerType.screenOff)
    private var mBeginTime: Date? = null
    private var mEndTime: Date? = null
    private var mTriggerType: TriggerType = TriggerType.foundWifi
    private var mServiceId: String? = null
    private fun ui() {
        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter != null && adapter.isEnabled) triggerTypes.add(TriggerType.nfc)
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            triggerTypes.add(TriggerType.foundBluetooth)
            triggerTypes.add(TriggerType.loseBluetooth)
        }

        type.setText("WIFI区域进入")
        typeIcon.setImageResource(R.drawable.ic_trigger_wifi_found)
        this.typePanel.onClick {
            showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.recyclerView.layoutManager = LinearLayoutManager(ctx)
                    val adaptar = RecyclerAdapter(R.layout.listitem_hass_trigger_type, triggerTypes) {
                        view, index, item ->
                        view.findViewById<ImageView>(R.id.icon).setImageResource(item.iconResId)
                        view.findViewById<TextView>(R.id.name).setText(item.desc)
                        view.onClick {
                            val compatibility = when (item) {
                                TriggerType.nfc-> mTriggerType == TriggerType.nfc
                                TriggerType.enterGps, TriggerType.leaveGps-> mTriggerType == TriggerType.leaveGps || mTriggerType == TriggerType.enterGps
                                TriggerType.foundBluetooth, TriggerType.loseBluetooth-> mTriggerType == TriggerType.loseBluetooth || mTriggerType == TriggerType.foundBluetooth
                                TriggerType.foundWifi, TriggerType.loseWifi-> mTriggerType == TriggerType.loseWifi || mTriggerType == TriggerType.foundWifi
                                else-> false
                            }
                            val showParam = when (item) {
                                TriggerType.screenOff, TriggerType.screenOn-> View.GONE
                                else-> View.VISIBLE
                            }
                            mTriggerType = item
                            type.setText(item.desc)
                            if (!compatibility) params.setText("")
                            params.visibility = showParam
                            typeIcon.setImageResource(item.iconResId)
                            dialog.dismiss()
                        }
                    }
                    val footer = layoutInflater.inflate(R.layout.layout_cancel, contentView.recyclerView, false)
                    footer.onClick { dialog.dismiss() }
                    contentView.recyclerView.adapter = RecyclerAdapterWrapper(adaptar)
                            .addFootView(footer)
                    contentView.recyclerView.addItemDecoration(RecyclerViewDivider()
                            .setColor(0xffeeeeee.toInt())
                            .setSize(1))
                }
            }, null, null)
        }
        this.params.onClick {
            when (mTriggerType) {
                TriggerType.nfc-> {
                    activity(NfcPickActivity::class.java, 103)
                }
                TriggerType.loseWifi, TriggerType.foundWifi-> {
                    Intent(act, WifiActivity::class.java)
                            .putExtra("bssid", act.params.text())
                            .start(act, 101)
                }
                TriggerType.loseBluetooth, TriggerType.foundBluetooth-> {
                    Intent(act, BluetoothActivity::class.java)
                            .putExtra("address", act.params.text())
                            .start(act, 102)
                }
                TriggerType.leaveGps, TriggerType.enterGps-> {
                    val params = act.params.text()
                    val regex = Regex("([-+]?[0-9]*\\.?[0-9]+),([-+]?[0-9]*\\.?[0-9]+):([0-9]*)")
                    val intent = Intent(act, GpsPickActivity::class.java)
                    regex.find(params)?.let {
                        intent.putExtra("latitude", it.groupValues[1].toDoubleOrNull())
                        intent.putExtra("longitude", it.groupValues[2].toDoubleOrNull())
                        intent.putExtra("radius", it.groupValues[3].toIntOrNull())
                    }
                    intent.start(act, 104)
                }
            }
        }
        this.beginTime.onClick {
            createDialog(R.layout.dialog_choice_time, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    mBeginTime?.let { contentView.findViewById<DateTimePicker>(R.id.picker).setDate(it) }
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        contentView.findViewById<TextView>(R.id.title).setText("生效开始时间")
                        mBeginTime = contentView.findViewById<DateTimePicker>(R.id.picker).date.time
                        beginTime.setText(mBeginTime?.kdateTime("HH:mm:ss"))
                    } else {
                        mBeginTime = null
                        beginTime.setText("")
                    }
                    dialog.dismiss()
                }
            }).show()
        }
        this.endTime.onClick {
            createDialog(R.layout.dialog_choice_time, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    mEndTime?.let { contentView.findViewById<DateTimePicker>(R.id.picker).setDate(it) }
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        contentView.findViewById<TextView>(R.id.title).setText("生效结束时间")
                        mEndTime = contentView.findViewById<DateTimePicker>(R.id.picker).date.time
                        endTime.setText(mEndTime?.kdateTime("HH:mm:ss"))
                    } else {
                        mEndTime = null
                        endTime.setText("")
                    }
                    dialog.dismiss()
                }
            }).show()
        }
        this.service.onClick {
            Intent(act, ServiceListActivity::class.java)
                    .putExtra("serviceId", mServiceId)
                    .start(act, 105)
        }
        this.choiceService.onClick {
            activity(InterceptGuideActivity::class.java, 108)
        }
        this.content.setMovementMethod(ScrollingMovementMethod.getInstance())
        this.content.setHorizontallyScrolling(true)
    }

    @ActivityResult(requestCode = 105)
    private fun servicePicked(data: Intent?) {
        data?.getStringExtra("serviceId")?.let {
            mServiceId = it
            content.setText(data.getStringExtra("content") ?: "{}")
            service.setText(mServiceId)
        }
    }

    @ActivityResult(requestCode = 101)
    private fun wifiPicked(data: Intent?) {
        data?.getStringExtra("bssid")?.let {
            params.setText(it)
        }
    }
    @ActivityResult(requestCode = 102)
    private fun bluetoothPicked(data: Intent?) {
        data?.getStringExtra("address")?.let {
            params.setText(it)
        }
    }
    @ActivityResult(requestCode = 103)
    private fun nfcPicked(data: Intent?) {
        data?.getStringExtra("uid")?.let {
            params.setText(it)
        }
    }
    @ActivityResult(requestCode = 104)
    private fun gpsPicked(data: Intent?) {
        if (data != null && data.hasExtra("longitude") ?: false) {
            val latitude = data.getDoubleExtra("latitude", .0)
            val longitude = data.getDoubleExtra("longitude", .0)
            val radius = data.getIntExtra("radius", 0)
            val param = "${latitude},${longitude}:${radius}"
            params.setText(param)
        }
    }
    @ActivityResult(requestCode = 108)
    private fun serviceIntercepted(data: Intent?) {
        if (data == null || !data.hasExtra("serviceId")) return
        mServiceId = data.getStringExtra("serviceId")
        this.service.text = mServiceId
        this.content.setText(data.getStringExtra("content"))
    }
}

