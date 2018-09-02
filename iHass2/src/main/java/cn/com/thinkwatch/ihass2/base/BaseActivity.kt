package cn.com.thinkwatch.ihass2.base

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v4.content.res.ResourcesCompat
import android.support.v7.app.ActionBar
import android.text.SpannableStringBuilder
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.global.GlobalConfig

import com.dylan.common.application.SwipeToBackActivity
import com.dylan.common.data.StrUtil
import com.dylan.common.sketch.Sketch
import com.dylan.common.utils.Utility
import com.dylan.uiparts.activity.ActivityResultDispatch
import com.dylan.uiparts.activity.RequestPermissionResultDispatch
import com.dylan.uiparts.layout.LoadableLayout
import io.reactivex.disposables.CompositeDisposable

open class BaseActivity : SwipeToBackActivity() {

    protected var titlebarName: TextView? = null
    protected var titlebarLeft: Button? = null
    protected var titlebarRight: Button? = null

    protected var loadable: LoadableLayout? = null
    private var needCheckLogin = true
    protected var disposable: CompositeDisposable? = null

    ////////////////////////////////////////////////////////////////
    //Utility methods
    protected val rootView: View?
        get() {
            var decorView: View? = findViewById(android.R.id.content)
            if (decorView != null && decorView is ViewGroup) {
                val viewGroup = decorView as ViewGroup?
                if (viewGroup!!.childCount > 0) {
                    decorView = viewGroup.getChildAt(0)
                    return decorView
                }
            }
            return null
        }
    private var mHideSoftInputMode = AutoHideSoftInputMode.Never
    protected fun setNeedCheckLogin(checkLogin: Boolean) {
        needCheckLogin = checkLogin
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isSwipeEnabled = false
    }
    override fun onDestroy() {
        disposable?.dispose()
        super.onDestroy()
    }
    override fun setContentView(layoutResID: Int) {
        setContentView(layoutResID, R.layout.layout_hass_titlebar)
    }
    fun setContentView(layoutResID: Int, titlebarResId: Int) {
        super.setContentView(layoutResID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.colorPrimary))
        }
        supportActionBar?.apply {
            displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
            setCustomView(titlebarResId)
            initTitlebarAction(customView)
        }
    }
    fun setContentViewNoTitlebar(layoutResID: Int) {
        super.setContentView(layoutResID)
        initTitlebarAction(rootView!!)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.colorPrimary))
        }
        if (Utility.isLollipopOrLater()) {
            val window = window
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = ContextCompat.getColor(this, R.color.colorPrimaryDark)
        }
    }
    @JvmOverloads
    fun setContentViewWithLoadable(layoutResID: Int, titlebarResId: Int = R.layout.layout_hass_titlebar) {
        loadable = LoadableLayout(this, layoutResID).showLoading(GlobalConfig.LoadingConfig(), false)
        super.setContentView(loadable)
        val actionBar = supportActionBar
        actionBar!!.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
        actionBar.setCustomView(titlebarResId)
        initTitlebarAction(actionBar.customView)
    }
    fun setContentViewNoTitlebarWithLoadable(layoutResID: Int) {
        loadable = LoadableLayout(this, layoutResID).showLoading(GlobalConfig.LoadingConfig(), false)
        super.setContentView(loadable)
        initTitlebarAction(rootView!!)
    }

    private fun initTitlebarAction(customView: View) {
        titlebarName = customView.findViewById(R.id.titlebar_name) as TextView
        titlebarLeft = customView.findViewById(R.id.titlebar_left) as Button
        titlebarRight = customView.findViewById(R.id.titlebar_right) as Button
        Sketch.set_click(titlebarLeft) { v -> doLeft() }
        Sketch.set_click(titlebarRight) { v -> doRight() }
    }
    protected fun setTitle(title: String) {
        Sketch.set_tv(titlebarName, title)
        Sketch.set_visible(false, titlebarLeft, titlebarRight)
    }
    @JvmOverloads
    protected fun setTitle(title: String, showBack: Boolean, right: String? = null, rightResId: Int = 0) {
        Sketch.set_tv(titlebarName, title)
        Sketch.set_visible(titlebarRight, false)
        Sketch.set_visible(titlebarLeft, showBack)
        if (showBack) Sketch.set_leftDrawable(titlebarLeft, R.drawable.titlebar_back)
        if (StrUtil.isNotBlank(right) || rightResId != 0) {
            Sketch.set_visible(titlebarRight, true)
            Sketch.set_tv(titlebarRight, right)
            Sketch.set_rightDrawable(titlebarRight, rightResId)
        } else {
            Sketch.set_visible(titlebarRight, false)
        }
    }
    protected fun setTitle(title: String, left: String, leftResId: Int, right: String, rightResId: Int) {
        Sketch.set_tv(titlebarName, title)
        if (StrUtil.isNotBlank(left) || leftResId != 0) {
            Sketch.set_visible(titlebarLeft, true)
            Sketch.set_tv(titlebarLeft, left)
            Sketch.set_leftDrawable(titlebarLeft, leftResId)
        } else {
            Sketch.set_visible(titlebarLeft, false)
        }
        if (StrUtil.isNotBlank(right) || rightResId != 0) {
            Sketch.set_visible(titlebarRight, true)
            Sketch.set_tv(titlebarRight, right)
            Sketch.set_rightDrawable(titlebarRight, rightResId)
        } else {
            Sketch.set_visible(titlebarRight, false)
        }
    }

    ////////////////////////////////////////////////////////////////
    //Override
    protected open fun doLeft() {
        onBackPressed()
    }
    protected open fun doRight() {
    }
    override open fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.push_left_in, R.anim.push_right_out)
    }
    override fun startActivity(intent: Intent) {
        super.startActivity(intent)
        overridePendingTransition(R.anim.push_right_in, R.anim.push_left_out)
    }
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        super.startActivityForResult(intent, requestCode, options)
        overridePendingTransition(R.anim.push_right_in, R.anim.push_left_out)
    }

    ////////////////////////////////////////////////////////////////
    //Auto hide soft input
    protected enum class AutoHideSoftInputMode {
        Never, WhenTouch, WhenClick
    }
    protected fun setAutoHideSoftInput(mode: AutoHideSoftInputMode) {
        mHideSoftInputMode = mode
        if (AutoHideSoftInputMode.WhenClick == mode) {
            val decorView = rootView
            if (null != decorView) {
                decorView.isClickable = true
                decorView.setOnClickListener { v -> Utility.hideSoftKeyboard(this@BaseActivity) }
            }
        }
    }
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (mHideSoftInputMode == AutoHideSoftInputMode.WhenTouch && ev.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (isShouldHideInput(v, ev)) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v!!.windowToken, 0)
            }
            return super.dispatchTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isShouldHideInput(v: View?, event: MotionEvent): Boolean {
        if (v != null && v is EditText) {
            val leftTop = intArrayOf(0, 0)
            v.getLocationInWindow(leftTop)
            val left = leftTop[0]
            val top = leftTop[1]
            val bottom = top + v.height
            val right = left + v.width
            return if (event.x > left && event.x < right && event.y > top && event.y < bottom) {
                false
            } else {
                true
            }
        }
        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        ActivityResultDispatch.onActivityResult(this, requestCode, resultCode, data)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        RequestPermissionResultDispatch.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }

    private var snackbar: Snackbar? = null
    protected fun showError(message: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        val warningIcon = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_warning_white_18dp, null)
        val builder = SpannableStringBuilder()
        builder.append(message)
        snackbar = Snackbar.make(rootView!!, builder, Snackbar.LENGTH_LONG)
        if (!actionLabel.isNullOrBlank() && action != null) snackbar?.setAction(actionLabel, { action() })
        else snackbar?.setAction("关闭") { hideError() }
        snackbar?.view?.findViewById<TextView>(android.support.design.R.id.snackbar_text)?.apply {
            setCompoundDrawablesWithIntrinsicBounds(warningIcon, null, null, null)
            compoundDrawablePadding = getResources().getDimensionPixelOffset(R.dimen.icon_8dp)
        }
        snackbar?.view?.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.md_red_A200, null))
        snackbar?.show()
    }
    protected fun hideError(){
        snackbar?.dismiss()
        snackbar = null
    }
}
