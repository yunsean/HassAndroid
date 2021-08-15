package cn.com.thinkwatch.ihass2.ui

import android.app.Dialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.db.db
import com.dylan.common.sketch.Dialogs
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.OnSettingDialogListener
import com.yunsean.dynkotlins.extensions.nextOnMain
import com.yunsean.dynkotlins.extensions.showDialog
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.find
import org.jetbrains.anko.image
import org.jetbrains.anko.sdk25.coroutines.onClick

class HomeLauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent()
        intent.component = ComponentName(packageName, "cn.com.thinkwatch.ihass2.service.DataSyncService")
        startService(intent)
        if (app.relayLauncher.isNullOrBlank()) {
            listLauncher()
        } else {
            try { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(app.relayLauncher)) } catch (_: Exception) { }
            finish()
        }
    }

    private fun listLauncher()  {
        val waiting = Dialogs.showWait(ctx, "正在加载应用列表...")
        db.async {
            val intent = Intent("android.intent.action.MAIN")
            intent.addCategory("android.intent.category.HOME")
            val activities = packageManager.queryIntentActivities(intent, 0)
            activities.filter { it.activityInfo.packageName != packageName }
        }.nextOnMain { packageItems->
            waiting.dismiss()
            if (packageItems.size == 1) {
                app.relayLauncher = packageItems[0].activityInfo.packageName
                try { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(app.relayLauncher)) } catch (_: Exception) { }
                finish()
            } else if (packageItems.size > 1) {
                showDialog(R.layout.dialog_list_view, object : OnSettingDialogListener {
                    override fun onSettingDialog(dialog: Dialog, contentView: View) {
                        contentView.recyclerView.layoutManager = LinearLayoutManager(act)
                        contentView.recyclerView.adapter = RecyclerAdapter(R.layout.listitem_app_item, packageItems) { view, index, item ->
                            view.find<ImageView>(R.id.icon).image = item.activityInfo.loadIcon(packageManager)
                            view.find<TextView>(R.id.name).text = item.activityInfo.loadLabel(packageManager).toString()
                            view.find<TextView>(R.id.desc).text = item.activityInfo.packageName
                            view.onClick {
                                dialog.dismiss()
                                app.relayLauncher = item.activityInfo.packageName
                                try { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(app.relayLauncher)) } catch (_: Exception) { }
                                finish()
                            }
                        }
                        contentView.recyclerView.addItemDecoration(RecyclerViewDivider()
                                .setColor(0xffeeeeee.toInt())
                                .setSize(1))
                    }
                }, null, null)
            }
        }.error {
            waiting.dismiss()
        }
    }
}