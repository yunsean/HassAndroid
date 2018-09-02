package cn.com.thinkwatch.ihass2.fragment

import android.Manifest
import android.app.Dialog
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.Gravity
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.bus.ConfigChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.common.rx.RxBus2
import com.dylan.uiparts.activity.RequestPermissionResult
import com.dylan.uiparts.activity.RequestPermissionResultDispatch
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.OnDialogItemClickedListener
import com.yunsean.dynkotlins.extensions.OnSettingDialogListener
import com.yunsean.dynkotlins.extensions.showDialog
import com.yunsean.dynkotlins.extensions.text
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import kotlinx.android.synthetic.main.dialog_hass_prompt.view.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import kotlinx.android.synthetic.main.fragment_hass_setting.*
import kotlinx.android.synthetic.main.listitem_entity_item.view.*
import org.jetbrains.anko.sdk25.coroutines.onCheckedChange
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx

class SettingFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.fragment_hass_setting
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ui()
    }
    private fun ui() {
        this.save.onClick {
            RequestPermissionResultDispatch.requestPermissions(this@SettingFragment, 107, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
        this.pullRefresh.isChecked = cfg.getBoolean(HassConfig.Ui_PullRefresh)
        this.pullRefresh.onCheckedChange { buttonView, isChecked ->
            cfg.set(HassConfig.Ui_PullRefresh, isChecked)
            RxBus2.getDefault().post(ConfigChanged())
        }
        this.homePanels.isChecked = cfg.getBoolean(HassConfig.Ui_HomePanels)
        this.homePanels.onCheckedChange { buttonView, isChecked ->
            cfg.set(HassConfig.Ui_HomePanels, isChecked)
            RxBus2.getDefault().post(ConfigChanged())
        }
        this.gpsLogger.isChecked = cfg.getBoolean(HassConfig.Gps_Logger)
        this.gpsLoggerPanel.setVisibility(if (gpsLogger.isChecked) View.VISIBLE else View.GONE)
        this.gpsLogger.onClick {
            if (gpsLogger.isChecked) {
                gpsLogger.isChecked = false
                RequestPermissionResultDispatch.requestPermissions(this@SettingFragment, 101, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE))
            } else {
                cfg.set(HassConfig.Gps_Logger, false)
                RxBus2.getDefault().post(ConfigChanged())
                gpsLoggerPanel.setVisibility(View.GONE)
            }
        }
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
                            cfg.set(HassConfig.Gps_DeviceName, item.friendlyName)
                            cfg.set(HassConfig.Gps_DeviceId, item.entityId.replace("device_tracker.", ""))
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
                        cfg.set(HassConfig.Gps_Password, contentView.input.text().trim())
                        gpsPassword.setText(cfg.getString(HassConfig.Gps_Password).trim().let { if (it.isBlank()) "无" else "********" })
                    }
                }
            })
        }
    }
    @RequestPermissionResult(requestCode = 101)
    private fun afterLocation() {
        cfg.set(HassConfig.Gps_Logger, true)
        RxBus2.getDefault().post(ConfigChanged())
        gpsLoggerPanel.setVisibility(View.VISIBLE)
        gpsLogger.isChecked = true
    }

    @RequestPermissionResult(requestCode = 107)
    private fun afterStorage() {
        val file = db.export(ctx)
        if (file == null) showError("导出配置文件到外部存储失败！")
        else showInfo("导出配置成功：${file}")
    }
}

