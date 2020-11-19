package cn.com.thinkwatch.ihass2.fragment

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.support.v4.content.pm.ShortcutInfoCompat
import android.support.v4.content.pm.ShortcutManagerCompat
import android.support.v4.graphics.drawable.IconCompat
import android.support.v7.widget.LinearLayoutManager
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.bus.ConfigChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.ui.LauncherActivity
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Animations
import com.dylan.common.sketch.Dialogs
import com.dylan.common.sketch.Sketch
import com.dylan.uiparts.activity.RequestPermissionResult
import com.dylan.uiparts.activity.RequestPermissionResultDispatch
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import kotlinx.android.synthetic.main.dialog_hass_prompt.view.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import kotlinx.android.synthetic.main.fragment_hass_setting.*
import kotlinx.android.synthetic.main.listitem_entity_item.view.*
import org.jetbrains.anko.image
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.dip


class SettingFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.fragment_hass_setting
    private var modified = false
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ui()
    }
    override fun onDestroy() {
        if (modified) RxBus2.getDefault().post(ConfigChanged())
        super.onDestroy()
    }
    
    private fun set(key: String, value: Any?) {
        cfg.set(key, value)
        modified = true
    }
    private fun ui() {
        this.save.onClick {
            RequestPermissionResultDispatch.requestPermissions(this@SettingFragment, 107, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
        this.pullRefresh.isChecked = cfg.getBoolean(HassConfig.Ui_PullRefresh)
        this.pullRefresh.setOnCheckedChangeListener { buttonView, isChecked ->
            set(HassConfig.Ui_PullRefresh, isChecked)
        }
        this.homePanels.isChecked = cfg.getBoolean(HassConfig.Ui_HomePanels)
        this.homePanels.setOnCheckedChangeListener { buttonView, isChecked ->
            set(HassConfig.Ui_HomePanels, isChecked)
        }
        this.webFirst.isChecked = cfg.getBoolean(HassConfig.Ui_WebFrist)
        this.webFirst.setOnCheckedChangeListener { buttonView, isChecked ->
            set(HassConfig.Ui_WebFrist, isChecked)
        }
        this.gpsLogger.isChecked = cfg.getBoolean(HassConfig.Gps_Logger)
        this.gpsLoggerPanel.setVisibility(if (gpsLogger.isChecked) View.VISIBLE else View.GONE)
        this.gpsLogger.onClick {
            if (gpsLogger.isChecked) {
                gpsLogger.isChecked = false
                RequestPermissionResultDispatch.requestPermissions(this@SettingFragment, 101, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE))
            } else {
                set(HassConfig.Gps_Logger, false)
                gpsLoggerPanel.setVisibility(View.GONE)
            }
        }
        this.gpsWebHookId.setText(cfg.getString(HassConfig.Gps_WebHookId))
        this.gpsDevice.text = cfg.getString(HassConfig.Gps_DeviceName)
        this.gpsDevicePanel.onClick {
            val devices = db.listEntity("device_tracker.%")
            ctx.showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.recyclerView.layoutManager = LinearLayoutManager(ctx)
                    val adaptar = RecyclerAdapter(R.layout.listitem_entity_item, devices) {
                        view, index, item ->
                        view.name.text = item.friendlyName
                        MDIFont.get().setIcon(view.icon, item.mdiIcon)
                        view.value.text = item.friendlyState
                        view.checked.visibility = View.GONE
                        view.onClick {
                            gpsDevice.text = item.friendlyName
                            set(HassConfig.Gps_DeviceName, item.friendlyName)
                            set(HassConfig.Gps_DeviceId, item.entityId.replace("device_tracker.", ""))
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
        this.gpsPassword.setText(cfg.getString(HassConfig.Gps_Password).trim().let { if (it.isBlank()) "无" else "********" })
        this.gpsPasswordPanel.onClick {
            ctx.showDialog(R.layout.dialog_hass_prompt, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.title.text = "输入预设密码"
                    contentView.content.text = "      请输入在配置文件中gpslogger下为password选项设置的密码（如果没有设置请留空）："
                    contentView.input.gravity = Gravity.CENTER
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object: OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    dialog.dismiss()
                    if (clickedView.id == R.id.ok) {
                        set(HassConfig.Gps_Password, contentView.input.text().trim())
                        gpsPassword.setText(cfg.getString(HassConfig.Gps_Password).trim().let { if (it.isBlank()) "无" else "********" })
                    }
                }
            })
        }
        this.gpsWebHookIdPanel.onClick {
            ctx.showDialog(R.layout.dialog_hass_prompt, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.title.text = "输入Web Hook ID"
                    contentView.content.text = "      如果你使用的是home assitant 0.84以后版本，并且使用原生的gpslogger组件，请在下方输入Web Hook ID（此时将无法上报除开经纬度和电量之外的其他信息）："
                    contentView.input.gravity = Gravity.CENTER
                    contentView.input.setText(cfg.getString(HassConfig.Gps_WebHookId).trim())
                    contentView.input.inputType = InputType.TYPE_CLASS_TEXT
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object: OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    dialog.dismiss()
                    if (clickedView.id == R.id.ok) {
                        contentView.input.text().trim().let {
                            set(HassConfig.Gps_WebHookId, it)
                            gpsWebHookId.setText(if (it.isBlank()) "" else "已设置")
                            if (it.isEmpty() && gpsHassComponentPanel.visibility == View.GONE) {
                                gpsHassComponentPanel.visibility = View.VISIBLE
                                Animations.HeightAnimation(gpsHassComponentPanel, 0, dip(89)).duration(300).start()
                            }
                            if (it.isNotEmpty() && gpsHassComponentPanel.visibility == View.VISIBLE) {
                                Animations.HeightAnimation(gpsHassComponentPanel, 0).duration(300).animationListener { gpsHassComponentPanel.visibility = View.GONE }.start()
                            }
                        }
                    }
                }
            })
        }
        cfg.getString(HassConfig.Gps_WebHookId).trim().let {
            this.gpsWebHookId.setText(if (it.isBlank()) "" else "已设置" )
            if (it.isEmpty() && gpsHassComponentPanel.visibility == View.GONE) {
                gpsHassComponentPanel.visibility = View.VISIBLE
                Animations.HeightAnimation(gpsHassComponentPanel, 0, dip(89)).duration(300).start()
            }
            if (it.isNotEmpty() && gpsHassComponentPanel.visibility == View.VISIBLE) {
                Animations.HeightAnimation(gpsHassComponentPanel, 0).duration(300).animationListener { gpsHassComponentPanel.visibility = View.GONE }.start()
            }
        }
        this.appLogger.onClick {
            set(HassConfig.Gps_AppLogger, appLogger.isChecked)
            if (appLogger.isChecked) startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        this.screenOffEvent.setOnCheckedChangeListener { buttonView, isChecked ->
            set(HassConfig.Connect_ScreenOff, isChecked)
        }
        this.batteryOptimizations.onClick {
            if (Build.VERSION.SDK_INT >= 23) {
                if ((act.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(act.packageName)) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } else {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.setData(Uri.parse("package:${act.packageName}"))
                        startActivity(intent)
                    } catch (_: Exception) {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            }
        }
        this.nfcCard.isChecked = cfg.getBoolean(HassConfig.Probe_NfcCard)
        this.nfcCard.onClick {
            set(HassConfig.Probe_NfcCard, nfcCard.isChecked)
        }
        this.nfcCardPanel.visibility = if (hasNfc()) View.VISIBLE else View.GONE

        this.bluetooth.isChecked = cfg.getBoolean(HassConfig.Probe_BluetoothBle)
        this.bluetooth.onClick {
            set(HassConfig.Probe_BluetoothBle, bluetooth.isChecked)
        }
        this.bluetoothPanel.visibility = if (hasBle()) View.VISIBLE else View.GONE

        this.wifi.isChecked = cfg.getBoolean(HassConfig.Probe_Wifi)
        this.wifi.onClick {
            set(HassConfig.Probe_Wifi, wifi.isChecked)
        }
        this.wifiPanel.visibility = if (hasWifi()) View.VISIBLE else View.GONE

        this.gps.isChecked = cfg.getBoolean(HassConfig.Probe_Gps)
        this.gps.onClick {
            if (gps.isChecked) {
                gps.isChecked = false
                RequestPermissionResultDispatch.requestPermissions(this@SettingFragment, 102, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE))
            } else {
                set(HassConfig.Probe_Gps, gps.isChecked)
            }
        }
        this.appLogger.isChecked = cfg.getBoolean(HassConfig.Gps_AppLogger)
        this.screenOffEvent.isChecked = cfg.getBoolean(HassConfig.Connect_ScreenOff)
        this.batteryOptimizations.visibility = if (Build.VERSION.SDK_INT >= 23) View.VISIBLE else View.GONE

        this.shortcut.onClick {
            doShortcut()
        }
    }
    private fun hasNfc() = try { (ctx.getSystemService(Context.NFC_SERVICE) as NfcManager?)?.defaultAdapter?.isEnabled ?: false } catch (_: Exception) { false }
    private fun hasBle() = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    private fun hasWifi() = try { (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?)?.isWifiEnabled ?: false } catch (_: Exception) { false }

    @RequestPermissionResult(requestCode = 101)
    private fun afterLocation() {
        set(HassConfig.Gps_Logger, true)
        gpsLoggerPanel.setVisibility(View.VISIBLE)
        gpsLogger.isChecked = true
    }
    @RequestPermissionResult(requestCode = 102)
    private fun afterGps() {
        set(HassConfig.Probe_Gps, true)
        gps.isChecked = true
    }
    @RequestPermissionResult(requestCode = 107)
    private fun afterStorage() {
        val file = db.export(ctx)
        if (file == null) showError("导出配置文件到外部存储失败！")
        else showInfo("导出配置成功：${file}")
    }

    private fun doShortcut() {
        if (!(ctx.readPref("NotFirstShortcut")?.toBoolean() ?: false)) {
            ctx.showDialog(R.layout.dialog_hass_confirm, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    Sketch.set_tv(contentView, R.id.title, "创建快捷图标")
                    Sketch.set_tv(contentView, R.id.content, "    通过创建快捷图标可以为任意的应用创建一个新图标，点击这个新创建的图标的时候即可启动原来的应用，并同时尝试拉起上述配置的服务器。\n\n    创建快捷图标需要开启权限，是否已经为服务拉起开启了“创建快捷方式”的权限？")
                    Sketch.set_tv(contentView, R.id.cancel, "先去检查一下")
                    Sketch.set_tv(contentView, R.id.ok, "已开启")
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    dialog.dismiss()
                    if (clickedView.id == R.id.ok) {
                        ctx.savePref("NotFirstShortcut", true.toString())
                        createShortcut()
                    }
                }
            })
        } else {
            createShortcut()
        }
    }
    private fun createShortcut() {
        val waiting = Dialogs.showWait(ctx, "正在加载应用列表...")
        db.async {
            ctx.packageManager.getInstalledPackages(PackageManager.GET_ACTIVITIES).filter { ctx.packageManager.getLaunchIntentForPackage(it.packageName) != null }
        }.nextOnMain { packageItems->
            waiting.dismiss()
            ctx.showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.recyclerView.layoutManager = LinearLayoutManager(act)
                    val adaptar = RecyclerAdapter(R.layout.listitem_app_item, packageItems) {
                        view, index, item ->
                        view.findViewById<ImageView>(R.id.icon).image = item.applicationInfo.loadIcon(ctx.packageManager)
                        view.findViewById<TextView>(R.id.name).text = item.applicationInfo.loadLabel(ctx.packageManager).toString()
                        view.findViewById<TextView>(R.id.desc).text = item.packageName
                        view.onClick {
                            addShortCutCompact(item)
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
        }.error {
            waiting.dismiss()
        }
    }
    private fun addShortCutCompact(packageInfo: PackageInfo) {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
            val shortcutInfoIntent = Intent(ctx, LauncherActivity::class.java)
            shortcutInfoIntent.putExtra("packageName", packageInfo.packageName)
            shortcutInfoIntent.action = Intent.ACTION_VIEW
            shortcutInfoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            shortcutInfoIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val icon = packageInfo.applicationInfo.loadIcon(ctx.packageManager)
            val info = ShortcutInfoCompat.Builder(ctx, packageInfo.packageName)
                    .setIcon(IconCompat.createWithBitmap((icon as BitmapDrawable).bitmap))
                    .setShortLabel(packageInfo.applicationInfo.loadLabel(ctx.packageManager).toString())
                    .setIntent(shortcutInfoIntent)
                    .build()
            ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
            ctx.toastex("创建快捷方式成功！")
        } else {
            ctx.toastex("创建快捷方式失败！")
        }
    }
}

