package cn.com.thinkwatch.ihass2.fragment

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.InputType
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.bus.HassConfiged
import cn.com.thinkwatch.ihass2.db.LocalStorage
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.network.BaseApi
import cn.com.thinkwatch.ihass2.network.http.HttpRestApi
import cn.com.thinkwatch.ihass2.ui.CertificateActivity
import cn.com.thinkwatch.ihass2.ui.ChoiceFileActivity
import cn.com.thinkwatch.ihass2.ui.WifiActivity
import cn.com.thinkwatch.ihass2.utils.HassConfig
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Animations
import com.dylan.common.sketch.Sketch
import com.dylan.common.utils.Utility
import com.dylan.uiparts.activity.ActivityResult
import com.yunsean.dynkotlins.extensions.*
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.dialog_hass_import.view.*
import kotlinx.android.synthetic.main.fragment_hass_http.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.dip

class HttpFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.fragment_hass_http
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ui()
    }
    private fun ui() {
        this.hostUrl.setText(cfg.getString(HassConfig.Hass_HostUrl, ""))
        this.lanHostUrl.setText(cfg.getString(HassConfig.Hass_LocalUrl, ""))
        this.bssids = cfg.get(HassConfig.Hass_LocalBssid)?.split(",")?.filter { it.isNotEmpty() }
        this.lanBssid.setText(bssids?.fold(StringBuilder(), { r, i-> r.append(i).append(" ")}))
        this.lanHostUrl.visibility = if (this.bssids?.size ?: 0 > 0) View.VISIBLE else View.GONE
        this.password.setText(cfg.getString(HassConfig.Hass_Password, ""))
        this.token.setText(cfg.optString(HassConfig.Hass_Token)?.replace("Bearer ", ""))
        if (this.token.text().isNotBlank()) passwordPanel.visibility = View.GONE
        else if (this.password.text().isNotBlank()) token.visibility = View.GONE
        this.tips.setText(Html.fromHtml("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;*&nbsp;访问密码和长效访问令牌二者选择一个填写即可。<br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;*&nbsp;从home assistant 0.78开始，提供长效访问令牌身份验证方式，你可以在网页登录后点击左上角头像进行长效令牌的配置，相关说明参考<a href='https://developers.home-assistant.io/docs/en/auth_api.html#making-authenticated-requests'>Authentication API</a>。"))
        this.tips.setMovementMethod(LinkMovementMethod.getInstance())
        this.appLogo.setOnTouchListener { v, event ->
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
        this.password.setOnEditorActionListener { v, id, event ->
            if (id == R.id.connect || id == EditorInfo.IME_NULL || id == EditorInfo.IME_ACTION_DONE) attemptLogin()
            false
        }
        this.password.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit
            override fun afterTextChanged(p0: Editable?) {
                if (password.text().isEmpty() && token.visibility == View.GONE) {
                    token.visibility = View.VISIBLE
                    Animations.HeightAnimation(token, 0, dip(44)).duration(300).start()
                } else if (password.text().isNotEmpty() && token.visibility == View.VISIBLE) {
                    Animations.HeightAnimation(token, 0).duration(300).animationListener { token.visibility = View.GONE }.start()
                }
            }
        })
        this.token.setOnEditorActionListener { v, id, event ->
            if (id == R.id.connect || id == EditorInfo.IME_NULL || id == EditorInfo.IME_ACTION_DONE) attemptLogin()
            false
        }
        this.token.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit
            override fun afterTextChanged(p0: Editable?) {
                if (token.text().isEmpty() && passwordPanel.visibility == View.GONE) {
                    passwordPanel.visibility = View.VISIBLE
                    Animations.HeightAnimation(passwordPanel, 0, dip(44)).duration(300).start()
                } else if (token.text().isNotEmpty() && passwordPanel.visibility == View.VISIBLE) {
                    Animations.HeightAnimation(passwordPanel, 0).duration(300).animationListener { passwordPanel.visibility = View.GONE }.start()
                }
            }
        })
        this.connect.onClick {
            attemptLogin()
        }
        this.showPassword.setOnCheckedChangeListener { buttonView, checked ->
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
        this.lanBssid.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                startActivityForResult(Intent(act, WifiActivity::class.java)
                        .putExtra("multiple", true)
                        .putExtra("bssids", bssids?.toTypedArray()), 100)
            }
            true
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
        detail.append("共包含").append(config.panels?.size ?: 0).append("个面板：").append("\n")
        config.panels?.forEach {
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
        Animations.HeightAnimation(this.tips, 0)
                .duration(300)
                .animationListener { tips.visibility = View.GONE }
                .start()
        Utility.hideSoftKeyboard(act)
        this.connect.setText("正在连接中…")
        this.hostUrl.setText(config.server.hostUrl)
        this.password.setText(config.server.password)
        this.token.setText(config.server.token)
        this.hostUrl.isEnabled = false
        this.password.isEnabled = false
        this.token.isEnabled = false
        this.connect.isEnabled = false
        this.progressBar.visibility = View.VISIBLE
        this.progressText.visibility = View.VISIBLE
        disposable?.dispose()
        val hostUrl = config.server.hostUrl ?: ""
        val localUrl = ""
        val password = config.server.password
        val token = config.server.token
        if (token.isNullOrBlank()) Animations.HeightAnimation(this.token, 0).duration(300).animationListener { this.token.visibility = View.GONE }.start()
        else Animations.HeightAnimation(this.passwordPanel, 0).duration(300).animationListener { this.passwordPanel.visibility = View.GONE }.start()
        BaseApi.jsonApi(hostUrl, HttpRestApi::class.java)
                .getStates(password, token)
                .flatMap {
                    db.initEntities(it, true)
                    Observable.just(it)
                }
                .flatMap {
                    db.import(config)
                    Observable.just(it)
                }
                .withNext {
                    disposable = null
                    cfg.set(HassConfig.Hass_HostUrl, hostUrl)
                    cfg.set(HassConfig.Hass_Password, password)
                    cfg.set(HassConfig.Hass_Token, token)
                    RxBus2.getDefault().post(HassConfiged())
                    act.setResult(Activity.RESULT_OK)
                    act.finish()
                }
                .error {
                    disposable = null
                    showError(it.localizedMessage ?: "未知错误", "重试") { attemptLogin() }
                    this.token.visibility = View.VISIBLE
                    this.passwordPanel.visibility = View.VISIBLE
                    if (token.isNullOrBlank()) Animations.HeightAnimation(this.token, 0, dip(44)).duration(300).start()
                    else Animations.HeightAnimation(this.passwordPanel, 0, dip(44)).duration(300).start()
                    act.hostUrl.isEnabled = true
                    act.password.isEnabled = true
                    this.token.isEnabled = true
                    act.connect.isEnabled = true
                    act.connect.setText("连接")
                    act.progressBar.visibility = View.INVISIBLE
                    act.progressText.visibility = View.INVISIBLE
                }
                .subscribeOnMain {
                    disposable = CompositeDisposable(it)
                }
    }

    private fun attemptLogin() {
        hideError()
        Utility.hideSoftKeyboard(act)
        Animations.HeightAnimation(this.tips, 0)
                .duration(300)
                .animationListener { tips.visibility = View.GONE }
                .start()
        var hostUrl = this.hostUrl.text.toString().trim { it <= ' ' }
        val password = this.password.text.toString()
        val token = this.token.text().trim()
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
                    doLogin(hostUrl, password, token, clickedView.id == R.id.ok)
                }
            })
        } else {
            doLogin(hostUrl, password, token, true)
        }
    }

    private fun doLogin(hostUrl: String, password: String, token: String, reset: Boolean) {
        this.connect.setText("正在连接中…")
        val token = if (token.isBlank()) null else "Bearer ${token}"
        val password = if (!token.isNullOrBlank() || password.isEmpty()) null else password
        val localUrl = this.lanHostUrl.text()
        this.hostUrl.isEnabled = false
        this.password.isEnabled = false
        this.token.isEnabled = false
        this.connect.isEnabled = false
        this.progressBar.visibility = View.VISIBLE
        this.progressText.visibility = View.VISIBLE
        disposable?.dispose()
        BaseApi.jsonApi(hostUrl, HttpRestApi::class.java)
                .getStates(password, token)
                .flatMap {
                    db.initEntities(it, reset)
                    Observable.just(it)
                }
                .withNext {
                    disposable = null
                    cfg.set(HassConfig.Hass_HostUrl, hostUrl)
                    cfg.set(HassConfig.Hass_Password, password)
                    cfg.set(HassConfig.Hass_Token, token)
                    cfg.set(HassConfig.Hass_LocalUrl, localUrl)
                    cfg.set(HassConfig.Hass_LocalBssid, bssids?.fold(StringBuilder(), {v, i-> v.append(i).append(",")}))
                    RxBus2.getDefault().post(HassConfiged())
                    act.setResult(Activity.RESULT_OK)
                    act.finish()
                }
                .error {
                    disposable = null
                    showError(it.localizedMessage ?: "未知错误", "重试") { attemptLogin() }
                    act.hostUrl.isEnabled = true
                    act.password.isEnabled = true
                    this.token.isEnabled = true
                    act.connect.isEnabled = true
                    act.connect.setText("连接")
                    act.progressBar.visibility = View.INVISIBLE
                    act.progressText.visibility = View.INVISIBLE
                }
                .subscribeOnMain {
                    disposable = CompositeDisposable(it)
                }
    }

    private var bssids: List<String>? = null
    @ActivityResult(requestCode = 100)
    private fun afterBssids(data: Intent?) {
        bssids = data?.getStringArrayExtra("bssids")?.toList()
        this.lanBssid.setText(bssids?.fold(StringBuilder(), { r, i-> r.append(i).append(" ")}))
        this.lanBssid.postDelayed({
            if (bssids?.size ?: 0 > 0 && lanHostUrl.visibility != View.VISIBLE) {
                lanHostUrl.visibility = View.VISIBLE
                Animations.HeightAnimation(lanHostUrl, dip(44)).duration(300).start()
            } else if (bssids?.size ?: 0 < 1 && lanHostUrl.visibility == View.VISIBLE) {
                Animations.HeightAnimation(lanHostUrl, 0).duration(300).animationListener { lanHostUrl.visibility = View.GONE }.start()
            }
        }, 500)
    }
}

