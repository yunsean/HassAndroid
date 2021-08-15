package cn.com.thinkwatch.ihass2.fragment

import android.Manifest
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.Gravity
import android.view.View
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.aidl.IAlbumSyncService
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.service.AlbumSyncService
import cn.com.thinkwatch.ihass2.ui.ChoiceFileActivity
import cn.com.thinkwatch.ihass2.ui.WifiActivity
import cn.com.thinkwatch.ihass2.utils.AddableRecylerAdapter
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.SimpleItemTouchHelperCallback
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.dyn3rdparts.pickerview.DateTimePicker
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.activity.RequestPermissionResultDispatch
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.dylan.uiparts.recyclerview.SwipeItemLayout
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import kotlinx.android.synthetic.main.dialog_hass_prompt.view.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import kotlinx.android.synthetic.main.fragment_hass_setting_album.*
import kotlinx.android.synthetic.main.fragment_hass_setting_voice.*
import kotlinx.android.synthetic.main.listitem_folder_item.view.*
import kotlinx.android.synthetic.main.listitem_textview.view.*
import org.jetbrains.anko.find
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx
import java.util.*

class SettingAlbumFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.fragment_hass_setting_album
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val intent = Intent(ctx, AlbumSyncService::class.java)
        ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        data()
        ui()
    }
    override fun onDestroyView() {
        serviceConnection?.let { ctx.unbindService(it) }
        super.onDestroyView()
    }

    private data class FolderItem(val name: String,
                                  val path: String?,
                                  val mdi: String)
    private val candidateFolders = mutableListOf<FolderItem>()
    private val folders = mutableListOf<FolderItem>()
    private var bssids: List<String>? = null
    private var interval = 0
    private var earlyTime: Calendar? = null
    private val scanIntervalValues = listOf(0, 1, 7, 30)
    private val scanIntervals = listOf("手动", "每天", "每周", "每月")
    private lateinit var adatper: AddableRecylerAdapter<FolderItem>
    private lateinit var touchHelper: ItemTouchHelper
    private fun ui() {
        this.adatper = AddableRecylerAdapter(R.layout.listitem_folder_item, folders) {
            view, index, item, holder ->
            view.icon.text = item.mdi
            view.name.text = item.name
            view.path.text = item.path
            view.delete.onClick {
                folders.remove(item)
                adatper.notifyDataSetChanged()
            }
        }
        act.allFolders.adapter = this.adatper
        act.allFolders.layoutManager = LinearLayoutManager(ctx)
        act.allFolders.addOnItemTouchListener(SwipeItemLayout.OnSwipeItemTouchListener(ctx))
        act.allFolders.addItemDecoration(RecyclerViewDivider()
                .setColor(0xfff2f2f2.toInt())
                .setSize(ctx.dip2px(1f)))
        val callback = SimpleItemTouchHelperCallback(this.adatper, false)
        touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(act.allFolders)

        this.useAlbum.setOnClickListener {
            allPanel.visibility = if (useAlbum.isChecked) View.VISIBLE else View.GONE
            tipsPanel.visibility = if (useAlbum.isChecked) View.GONE else View.VISIBLE
        }
        this.uploadMode.setOnCheckedChangeListener { group, checkedId ->
            val mode = fragment.findViewById<View>(checkedId).tag.toString().toInt()
            wifiPanel.visibility = if (mode != HassConfig.UploadMode_SomeWifi) View.GONE else View.VISIBLE
        }
        this.addFolder.setOnClickListener {
            val validFolders = candidateFolders.filter { !folders.contains(it) }
            ctx.showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.recyclerView.layoutManager = LinearLayoutManager(ctx)
                    val adaptar = RecyclerAdapter(R.layout.listitem_textview, validFolders) {
                        view, index, item ->
                        view.text.text = item.name
                        view.onClick {
                            if (item.path == null) {
                                startActivityForResult(Intent(ctx, ChoiceFileActivity::class.java)
                                        .putExtra("title", "选择同步目录")
                                        .putExtra("forFolder", true), 1078)
                            } else {
                                folders.add(item)
                                adatper.notifyDataSetChanged()
                            }
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
        this.userStubPanel.setOnClickListener {
            ctx.showDialog(R.layout.dialog_hass_prompt, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.title.text = "输入用户标识"
                    contentView.content.text = "      用户标识用于在服务器端区分不同的文件夹："
                    contentView.input.gravity = Gravity.CENTER
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object: OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        val stub = contentView.input.text().trim()
                        if (stub.isBlank()) return ctx.toastex("用户标识不能为空")
                        userStub.text = stub
                    }
                    dialog.dismiss()
                }
            })
        }
        this.wifiPanel.setOnClickListener {
            startActivityForResult(Intent(act, WifiActivity::class.java)
                    .putExtra("multiple", true)
                    .putExtra("bssids", bssids?.toTypedArray()), 100)
        }
        this.scanPanel.setOnClickListener {
            ctx.showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.recyclerView.layoutManager = LinearLayoutManager(ctx)
                    val adaptar = RecyclerAdapter(R.layout.listitem_textview, scanIntervals) {
                        view, index, item ->
                        view.text.text = item
                        view.onClick {
                            interval = scanIntervalValues[index]
                            scanInterval.text = scanIntervals[index]
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
        cfg.getLong(HassConfig.Album_EarlyTime).let {
            if (it != 0L) {
                earlyTime = Calendar.getInstance().apply { timeInMillis = it }
                onlyAfter.text = earlyTime.kdateTime("yyyy-MM-dd HH:mm")
            }
        }
        this.onlyAfterPanel.setOnClickListener {
            ctx.showDialog(R.layout.dialog_choice_datetime, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.findViewById<TextView>(R.id.title).setText("最早同步时间")
                    val time = earlyTime ?: Calendar.getInstance().apply { add(Calendar.YEAR, -1) }
                    contentView.findViewById<DateTimePicker>(R.id.picker).time = time.timeInMillis
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        earlyTime = contentView.findViewById<DateTimePicker>(R.id.picker).date
                        onlyAfter.text = earlyTime.kdateTime("yyyy-MM-dd HH:mm")
                    } else {
                        earlyTime = null
                        onlyAfter.text = ""
                    }
                    dialog.dismiss()
                }
            })
        }
        this.save.setOnClickListener {
            val mode = fragment.find<View>(uploadMode.checkedRadioButtonId).tag.toString().toInt()
            if (useAlbum.isChecked) {
                if (userStub.text().isBlank()) return@setOnClickListener showError("请设置用户标识！")
                if (folders.isEmpty()) return@setOnClickListener showError("请设置需要备份的目录！")
                if (mode == HassConfig.UploadMode_SomeWifi && bssids?.isEmpty() != false) return@setOnClickListener showError("请设置自动上传所使用的WIFI！")
            }
            syncService?.let {
                cfg.set(HassConfig.Album_Used, useAlbum.isChecked)
                cfg.set(HassConfig.Album_UserStub, userStub.text())
                cfg.set(HassConfig.Album_Folders, folders.map { it.path }.joinToString("*"))
                cfg.set(HassConfig.Album_AutoUpload, mode)
                cfg.set(HassConfig.Album_UploadWifi, bssids?.joinToString("*") ?: "")
                cfg.set(HassConfig.Album_ScanInterval, interval)
                cfg.set(HassConfig.Album_Override, autoOverride.isChecked)
                cfg.set(HassConfig.Album_EarlyTime, earlyTime?.timeInMillis ?: 0L)
                it.configChanged()
            } ?: return@setOnClickListener showError("保存配置失败")
            act.finish()
        }
    }

    private fun data() {
        candidateFolders.add(FolderItem("相册目录", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath, "\uf104"))
        candidateFolders.add(FolderItem("图片目录", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath, "\uf975"))
        candidateFolders.add(FolderItem("视频目录", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath, "\uf381"))
        candidateFolders.add(FolderItem("文档目录", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath, "\uf21a"))
        candidateFolders.add(FolderItem("自定义目录", null, "\uf76f"))

        useAlbum.isChecked = cfg.getBoolean(HassConfig.Album_Used)
        tipsPanel.visibility = if (useAlbum.isChecked) View.GONE else View.VISIBLE
        userStub.setText(cfg.getString(HassConfig.Album_UserStub))
        folders.addAll(cfg.getString(HassConfig.Album_Folders).split("*").filter { it.isNotBlank() }.map { f->
            candidateFolders.find { it.path == f } ?: FolderItem("自定义目录", f, "\uf76f")
        })
        autoOverride.isChecked = cfg.getBoolean(HassConfig.Album_Override)
        bssids = cfg.getString(HassConfig.Album_UploadWifi).split("*").filter { it.isNotBlank() }
        wifiBssid.text = bssids?.fold(StringBuilder()) { r, i-> r.append(i).append(" ")}
        interval = cfg.getInt(HassConfig.Album_ScanInterval)
        scanInterval.text = when (interval) {
            1-> "每天"
            7-> "每周"
            30-> "每月"
            else-> "手动"
        }
        allPanel.visibility = if (useAlbum.isChecked) View.VISIBLE else View.GONE
        cfg.getInt(HassConfig.Album_AutoUpload).let {
            uploadMode.clearCheck()
            when (it) {
                HassConfig.UploadMode_SomeWifi-> someWifi.isChecked = true
                HassConfig.UploadMode_AllWifi-> allWifi.isChecked = true
                HassConfig.UploadMode_Always-> always.isChecked = true
                else-> none.isChecked = true
            }
            wifiPanel.visibility = if (it != HassConfig.UploadMode_SomeWifi) View.GONE else View.VISIBLE
        }
    }

    @ActivityResult(requestCode = 100)
    private fun afterBssids(data: Intent?) {
        bssids = data?.getStringArrayExtra("bssids")?.toList()
        this.wifiBssid.text = bssids?.fold(StringBuilder()) { r, i-> r.append(i).append(" ")}
    }
    @ActivityResult(requestCode = 1078)
    private fun afterPickFile(data: Intent) {
        val path = data.getStringExtra("path")
        folders.add(FolderItem("自定义目录", path, "\uf76f"))
        adatper.notifyDataSetChanged()
    }

    private var syncService: IAlbumSyncService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            syncService = IAlbumSyncService.Stub.asInterface(service)
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            syncService = null
        }
    }
}

