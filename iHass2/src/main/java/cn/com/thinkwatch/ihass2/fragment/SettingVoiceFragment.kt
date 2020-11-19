package cn.com.thinkwatch.ihass2.fragment

import android.Manifest
import android.app.Dialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.bus.AdminEnabled
import cn.com.thinkwatch.ihass2.bus.ConfigChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.service.AdminReceiver
import cn.com.thinkwatch.ihass2.ui.BluetoothActivity
import cn.com.thinkwatch.ihass2.ui.VoiceHelpActivity
import cn.com.thinkwatch.ihass2.ui.WifiActivity
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import cn.com.thinkwatch.ihass2.voice.ContactHandler
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Dialogs
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.activity.RequestPermissionResult
import com.dylan.uiparts.activity.RequestPermissionResultDispatch
import com.yunsean.dynkotlins.extensions.activity
import com.yunsean.dynkotlins.extensions.nextOnMain
import com.yunsean.dynkotlins.extensions.toastex
import com.yunsean.dynkotlins.extensions.withNext
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_hass_setting_voice.*
import kotlinx.android.synthetic.main.layout_hass_voice.*
import org.jetbrains.anko.act
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx

class SettingVoiceFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.fragment_hass_setting_voice
    private var modified = false
    private var rebuilding: Disposable? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ui()
        RxBus2.getDefault().register(AdminEnabled::class.java, {
            tripleHomeKey.isChecked = true
            set(HassConfig.Speech_TripleHomeKey, true)
        }, disposable)
    }
    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= 23) {
            if (Settings.canDrawOverlays(act)) {
                this.permissionPanel.visibility = View.GONE
                this.voicePanel.visibility = View.VISIBLE
            } else {
                this.permissionPanel.visibility = View.VISIBLE
                this.voicePanel.visibility = View.GONE
            }
        } else {
            this.permissionPanel.visibility = View.GONE
            this.voicePanel.visibility = View.VISIBLE
        }
    }
    override fun onDestroy() {
        rebuilding?.dispose()
        if (modified) RxBus2.getDefault().post(ConfigChanged())
        super.onDestroy()
    }
    
    private fun set(key: String, value: Any?) {
        cfg.set(key, value)
        modified = true
    }
    private fun ui() {
        this.openPermission.onClick {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))
            startActivityForResult(intent, 1000)
        }
        this.doubleHomeKey.isChecked = cfg.getBoolean(HassConfig.Speech_DoubleHomeKey)
        this.tripleHomeKey.isChecked = cfg.getBoolean(HassConfig.Speech_TripleHomeKey)
        this.showNotification.isChecked = cfg.getBoolean(HassConfig.Speech_Notification)
        this.showWakeup.isChecked = cfg.getBoolean(HassConfig.Speech_ShowWakeup)
        cfg.getInt(HassConfig.Speech_ScreenOnMode).let {
            screenOnPanel.clearCheck()
            when (it) {
                HassConfig.Wakeup_Forbid-> screenOnNone.isChecked = true
                HassConfig.Wakeup_Condition-> screenOnIf.isChecked = true
                else-> screenOnAlways.isChecked = true
            }
            screenOnConditions.visibility = if (it != HassConfig.Wakeup_Condition) View.GONE else View.VISIBLE
        }
        this.screenOnCharging.isChecked = cfg.getBoolean(HassConfig.Speech_ScreenOnCharging)
        this.screenOnWifi.text = cfg.getString(HassConfig.Speech_ScreenOnWifi)
        this.screenOnBluettooth.text = cfg.getString(HassConfig.Speech_ScreenOnBluetooth)
        cfg.getInt(HassConfig.Speech_ScreenOffMode).let {
            screenOffPanel.clearCheck()
            when (it) {
                HassConfig.Wakeup_Forbid-> screenOffNone.isChecked = true
                HassConfig.Wakeup_Condition-> screenOffIf.isChecked = true
                else-> screenOffAlways.isChecked = true
            }
            screenOffConditions.visibility = if (it != HassConfig.Wakeup_Condition) View.GONE else View.VISIBLE
        }
        this.screenOffCharging.isChecked = cfg.getBoolean(HassConfig.Speech_ScreenOffCharging)
        this.screenOffWifi.text = cfg.getString(HassConfig.Speech_ScreenOffWifi)
        this.screenOffBluettooth.text = cfg.getString(HassConfig.Speech_ScreenOffBluetooth)
        this.recordFromBluetooth.text = cfg.getString(HassConfig.Speech_FromBluetooth)
        this.headsetWakeup.isChecked = cfg.getBoolean(HassConfig.Speech_HeadsetWakeup)
        this.noWakeupLock.isChecked = cfg.getBoolean(HassConfig.Speech_NoWakeupLock)
        this.voiceOpenApp.isChecked = cfg.getBoolean(HassConfig.Speech_VoiceOpenApp)
        cfg.getBoolean(HassConfig.Speech_VoiceContact).let {
            this.voiceCall.isChecked = it
            this.rebuildContact.visibility = if (it) View.VISIBLE else View.GONE
        }
        this.tripleHomeKey.onClick {
            if (tripleHomeKey.isChecked) {
                (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).let {
                    if (!it.isAdminActive(ComponentName(ctx, AdminReceiver::class.java))) {
                        val componentName = ComponentName(ctx, AdminReceiver::class.java)
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "激活后iHass才能使用三击HOME锁屏功能")
                        ctx.startActivity(intent)
                        tripleHomeKey.isChecked = false
                        return@onClick
                    }
                }
            }
            set(HassConfig.Speech_TripleHomeKey, tripleHomeKey.isChecked)
        }
        this.doubleHomeKey.onClick {
            set(HassConfig.Speech_DoubleHomeKey, doubleHomeKey.isChecked)
        }
        this.showNotification.onClick {
            set(HassConfig.Speech_Notification, showNotification.isChecked)
            if (showNotification.isChecked && db.getAllNotificationEntityIds()?.size ?: 0 < 1) {
                showInfo("通知栏显示需要配置通知栏小组件后方能生效！")
            }
        }
        this.showWakeup.onClick {
            set(HassConfig.Speech_ShowWakeup, showWakeup.isChecked)
            if (showWakeup.isChecked && db.getAllNotificationEntityIds()?.size ?: 0 < 1) {
                showInfo("通知栏显示需要配置通知栏小组件后方能生效！")
            }
        }

        this.screenOnPanel.setOnCheckedChangeListener { group, checkedId ->
            val mode = fragment.findViewById<View>(checkedId).tag.toString().toInt()
            set(HassConfig.Speech_ScreenOnMode, mode)
            screenOnConditions.visibility = if (mode != HassConfig.Wakeup_Condition) View.GONE else View.VISIBLE
            if (mode != HassConfig.Wakeup_Forbid) {
                RequestPermissionResultDispatch.requestPermissions(this@SettingVoiceFragment, 500,
                        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.INTERNET, Manifest.permission.READ_PHONE_STATE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
            RequestPermissionResultDispatch.requestPermissions(act, 10, arrayOf(Manifest.permission.RECORD_AUDIO))
        }
        this.screenOnCharging.onClick {
            set(HassConfig.Speech_ScreenOnCharging, screenOnCharging.isChecked)
        }
        this.screenOnWifiPanel.onClick {
            startActivityForResult(Intent(act, WifiActivity::class.java)
                    .putExtra("multiple", true)
                    .putExtra("bssids", cfg.getString(HassConfig.Speech_ScreenOnWifi).split(' ').filter { it.isNotBlank() }.toTypedArray()), 110)
        }
        this.screenOnBluettoothPanel.onClick {
            startActivityForResult(Intent(act, BluetoothActivity::class.java)
                    .putExtra("multiple", true)
                    .putExtra("addresses", cfg.getString(HassConfig.Speech_ScreenOnBluetooth).split(' ').filter { it.isNotBlank() }.toTypedArray()), 111)
        }

        this.screenOffPanel.setOnCheckedChangeListener { group, checkedId ->
            val mode = fragment.findViewById<View>(checkedId).tag.toString().toInt()
            set(HassConfig.Speech_ScreenOffMode, mode)
            screenOffConditions.visibility = if (mode != HassConfig.Wakeup_Condition) View.GONE else View.VISIBLE
            if (mode != HassConfig.Wakeup_Forbid) {
                RequestPermissionResultDispatch.requestPermissions(this@SettingVoiceFragment, 500,
                        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.INTERNET, Manifest.permission.READ_PHONE_STATE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
            RequestPermissionResultDispatch.requestPermissions(act, 10, arrayOf(Manifest.permission.RECORD_AUDIO))
        }
        this.screenOffCharging.onClick {
            set(HassConfig.Speech_ScreenOffCharging, screenOffCharging.isChecked)
        }
        this.screenOffWifiPanel.onClick {
            startActivityForResult(Intent(act, WifiActivity::class.java)
                    .putExtra("multiple", true)
                    .putExtra("bssids", cfg.getString(HassConfig.Speech_ScreenOffWifi).split(' ').filter { it.isNotBlank() }.toTypedArray()), 120)
        }
        this.screenOffBluettoothPanel.onClick {
            startActivityForResult(Intent(act, BluetoothActivity::class.java)
                    .putExtra("multiple", true)
                    .putExtra("addresses", cfg.getString(HassConfig.Speech_ScreenOffBluetooth).split(' ').filter { it.isNotBlank() }.toTypedArray()), 121)
        }

        this.recordFromBluetoothPanel.onClick {
            startActivityForResult(Intent(act, BluetoothActivity::class.java)
                    .putExtra("multiple", true)
                    .putExtra("addresses", cfg.getString(HassConfig.Speech_FromBluetooth).split(' ').filter { it.isNotBlank() }.toTypedArray()), 131)
        }
        this.headsetWakeup.onClick {
            set(HassConfig.Speech_HeadsetWakeup, headsetWakeup.isChecked)
        }
        this.noWakeupLock.onClick {
            set(HassConfig.Speech_NoWakeupLock, noWakeupLock.isChecked)
        }
        this.voiceOpenApp.onClick {
            set(HassConfig.Speech_VoiceOpenApp, voiceOpenApp.isChecked)
        }
        this.voiceCall.onClick {
            if (voiceCall.isChecked) {
                voiceCall.isChecked = false
                RequestPermissionResultDispatch.requestPermissions(this@SettingVoiceFragment, 105, arrayOf(Manifest.permission.READ_CONTACTS))
            } else {
                set(HassConfig.Speech_VoiceContact, voiceCall.isChecked)
            }
            act.rebuildContact.visibility = if (voiceCall.isChecked) View.VISIBLE else View.GONE
        }
        this.rebuildContact.onClick {
            var canceled = false
            val waiting = Dialogs.showWait(act, "正在重建联系人数据库...") {
                canceled = true
            }
            db.async {
                ContactHandler.rebuildContact(true, {
                    act.runOnUiThread { waiting.findViewById<TextView>(R.id.tips)?.setText("正在重建联系人数据库${it}%...")}
                    canceled
                })
            }
                    .withNext { waiting?.dismiss() }
                    .error { waiting?.dismiss(); ctx.toastex(it.message ?: "重建数据库错误") }
                    .subscribeOnMain { rebuilding = it }
        }
        this.help.onClick {
            act.activity(VoiceHelpActivity::class.java)
        }
    }

    @RequestPermissionResult(requestCode = 105)
    private fun afterGetReadContacts() {
        voiceCall.isChecked = true
        set(HassConfig.Speech_VoiceContact, voiceCall.isChecked)
        act.rebuildContact.visibility = View.VISIBLE
    }

    @ActivityResult(requestCode = 110)
    private fun afterScreenOnWifi(data: Intent?) {
        if (data == null) return
        val bssids = data.getStringArrayExtra("bssids")?.toList()
        val wifi = bssids?.fold(StringBuilder(), { r, i-> r.append(i).append(" ")})
        this.screenOnWifi.text = wifi
        set(HassConfig.Speech_ScreenOnWifi, wifi)
    }
    @ActivityResult(requestCode = 111)
    private fun afterScreenOnBluetooth(data: Intent?) {
        if (data == null) return
        val addresses = data.getStringArrayExtra("addresses")?.toList()
        val bluetooth = addresses?.fold(StringBuilder(), { r, i-> r.append(i).append(" ")})
        this.screenOnBluettooth.text = bluetooth
        set(HassConfig.Speech_ScreenOnBluetooth, bluetooth)
    }

    @ActivityResult(requestCode = 120)
    private fun afterScreenOffWifi(data: Intent?) {
        if (data == null) return
        val bssids = data.getStringArrayExtra("bssids")?.toList()
        val wifi = bssids?.fold(StringBuilder(), { r, i-> r.append(i).append(" ")})
        this.screenOffWifi.text = wifi
        set(HassConfig.Speech_ScreenOffWifi, wifi)
    }
    @ActivityResult(requestCode = 121)
    private fun afterScreenOffBluetooth(data: Intent?) {
        if (data == null) return
        val addresses = data.getStringArrayExtra("addresses")?.toList()
        val bluetooth = addresses?.fold(StringBuilder(), { r, i-> r.append(i).append(" ")})
        this.screenOffBluettooth.text = bluetooth
        set(HassConfig.Speech_ScreenOffBluetooth, bluetooth)
    }

    @ActivityResult(requestCode = 131)
    private fun afterRecordFromBluetooth(data: Intent?) {
        if (data == null) return
        val addresses = data.getStringArrayExtra("addresses")?.toList()
        val bluetooth = addresses?.fold(StringBuilder(), { r, i-> r.append(i).append(" ")})
        this.recordFromBluetooth.text = bluetooth
        set(HassConfig.Speech_FromBluetooth, bluetooth)
    }
}

