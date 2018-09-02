package cn.com.thinkwatch.ihass2.fragment

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.api.BaseApi
import cn.com.thinkwatch.ihass2.api.RestApi
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.bus.HassConfiged
import cn.com.thinkwatch.ihass2.db.LocalStorage
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.ui.CertificateActivity
import cn.com.thinkwatch.ihass2.ui.ChoiceFileActivity
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Sketch
import com.dylan.common.utils.Utility
import com.dylan.uiparts.activity.ActivityResult
import com.yunsean.dynkotlins.extensions.*
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.dialog_hass_import.view.*
import kotlinx.android.synthetic.main.fragment_hass_conf.*
import org.jetbrains.anko.sdk25.coroutines.onCheckedChange
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.sdk25.coroutines.onEditorAction
import org.jetbrains.anko.sdk25.coroutines.onTouch
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx


class ConfFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.fragment_hass_conf
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ui()
    }
    private fun ui() {
        this.hostUrl.setText(app.haHostUrl)
        this.password.setText(app.haPassword)
        this.appLogo.onTouch { v, event ->
            val animation = { to: Float->
                val scaleDown = AnimatorSet()
                scaleDown.play(ObjectAnimator.ofFloat(v, "scaleX", to).setDuration(200))
                        .with(ObjectAnimator.ofFloat(v, "scaleY", to).setDuration(200))
                scaleDown.interpolator = OvershootInterpolator()
                scaleDown.start()
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> animation(0.95f)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animation(1f)
            }
            true
        }
        this.password.onEditorAction { v, id, event ->
            if (id == R.id.connect || id == EditorInfo.IME_NULL || id == EditorInfo.IME_ACTION_DONE) attemptLogin()
            false
        }
        this.connect.onClick {
            attemptLogin()
        }
        this.showPassword.onCheckedChange { buttonView, checked ->
            password.setInputType(InputType.TYPE_CLASS_TEXT or if (checked) 0 else InputType.TYPE_TEXT_VARIATION_PASSWORD)
        }
        this.progressBar.visibility = View.INVISIBLE
        this.progressText.visibility = View.INVISIBLE
        this.input.onClick {
            startActivityForResult(Intent(ctx, ChoiceFileActivity::class.java), 108)
        }
        this.certifacte.onClick {
            startActivity(Intent(ctx, CertificateActivity::class.java))
        }
    }

    @ActivityResult(requestCode = 108)
    private fun afterPickFile(data: Intent) {
        val path = data.getStringExtra("path")
        if (path == null) return showError("导入配置文件失败！")
        val config = db.import(path)
        if (config == null) return showError("读取配置文件失败！")
        val detail = StringBuilder()
        detail.append("HASS服务器：").append(config.server.hostUrl).append("\n")
        detail.append("共包含").append(config.panels.size).append("个面板：").append("\n")
        config.panels.forEach {
            detail.append("\t").append(it.name).append("\n")
        }
        ctx.createDialog(R.layout.dialog_hass_import, object: OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                contentView.detail.text = detail.toString()
            }
        }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
            override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                dialog.dismiss()
                if (clickedView.id == R.id.ok) {
                    import(config)
                }
            }
        }).show()
    }
    private fun import(config: LocalStorage.HassConfig) {
        hideError()
        Utility.hideSoftKeyboard(act)
        this.connect.setText("正在连接中…")
        this.hostUrl.isEnabled = false
        this.password.isEnabled = false
        this.connect.isEnabled = false
        this.progressBar.visibility = View.VISIBLE
        this.progressText.visibility = View.VISIBLE
        disposable?.dispose()
        BaseApi.api(config.server.hostUrl ?: "", RestApi::class.java)
                .getStates(config.server.password ?: "")
                .flatMap {
                    db.initEntities(it, true)
                    Observable.just(it)
                }
                .flatMap {
                    db.import(config)
                    Observable.just(it)
                }
                .nextOnMain {
                    disposable = null
                    cfg.set(HassConfig.Hass_HostUrl, config.server.hostUrl ?: "")
                    cfg.set(HassConfig.Hass_Password, config.server.password ?: "")
                    RxBus2.getDefault().post(HassConfiged(config.server.hostUrl ?: "", config.server.password ?: ""))
                    act.setResult(Activity.RESULT_OK)
                    act.finish()
                }
                .error {
                    disposable = null
                    showError(it.localizedMessage ?: "未知错误", "重试") { attemptLogin() }
                    act.hostUrl.isEnabled = true
                    act.password.isEnabled = true
                    act.connect.isEnabled = true
                    act.connect.setText("连接")
                    act.progressBar.visibility = View.INVISIBLE
                    act.progressText.visibility = View.INVISIBLE
                }
                .subscribe {
                    disposable = CompositeDisposable(it)
                }
    }

    private fun attemptLogin() {
        hideError()
        Utility.hideSoftKeyboard(act)
        var hostUrl = this.hostUrl.text.toString().trim { it <= ' ' }
        val password = this.password.text.toString()
        if (hostUrl.endsWith("/")) {
            hostUrl = hostUrl.substring(0, hostUrl.length - 1)
            this.hostUrl.setText(hostUrl)
        }
        if (hostUrl.isBlank()) return showError("请输入Hass服务器地址")
        if (!(hostUrl.startsWith("http://") || hostUrl.startsWith("https://"))) return showError("Hass服务器地址错误")
        if (db.listPanel().size > 0) {
            ctx.showDialog(R.layout.dialog_hass_confirm, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    Sketch.set_tv(contentView, R.id.title, "配置迁移")
                    Sketch.set_tv(contentView, R.id.content, "当前存在面板配置，是否清除所有面板信息？")
                    Sketch.set_tv(contentView, R.id.cancel, "保留")
                    Sketch.set_tv(contentView, R.id.ok, "清除")
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    dialog.dismiss()
                    if (clickedView.id == R.id.ok) doLogin(hostUrl, password, true)
                    else doLogin(hostUrl, password, false)
                }
            })
        } else {
            doLogin(hostUrl, password, true)
        }
    }

    private fun doLogin(hostUrl: String, password: String, reset: Boolean) {
        this.connect.setText("正在连接中…")
        this.hostUrl.isEnabled = false
        this.password.isEnabled = false
        this.connect.isEnabled = false
        this.progressBar.visibility = View.VISIBLE
        this.progressText.visibility = View.VISIBLE
        disposable?.dispose()
        BaseApi.api(hostUrl, RestApi::class.java)
                .getStates(password)
                .flatMap {
                    db.initEntities(it, reset)
                    Observable.just(it)
                }
                .nextOnMain {
                    disposable = null
                    cfg.set(HassConfig.Hass_HostUrl, hostUrl)
                    cfg.set(HassConfig.Hass_Password, password)
                    RxBus2.getDefault().post(HassConfiged(hostUrl, password))
                    act.setResult(Activity.RESULT_OK)
                    act.finish()
                }
                .error {
                    disposable = null
                    showError(it.localizedMessage ?: "未知错误", "重试") { attemptLogin() }
                    act.hostUrl.isEnabled = true
                    act.password.isEnabled = true
                    act.connect.isEnabled = true
                    act.connect.setText("连接")
                    act.progressBar.visibility = View.INVISIBLE
                    act.progressText.visibility = View.INVISIBLE
                }
                .subscribe {
                    disposable = CompositeDisposable(it)
                }
    }
}

