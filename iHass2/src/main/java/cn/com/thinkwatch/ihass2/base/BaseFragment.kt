package cn.com.thinkwatch.ihass2.base

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v4.content.res.ResourcesCompat
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.global.GlobalConfig
import com.dylan.common.sketch.Sketch
import com.dylan.uiparts.activity.ActivityResultDispatch
import com.dylan.uiparts.layout.LoadableLayout
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.layout_hass_titlebar.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx

abstract class BaseFragment : Fragment() {

    protected abstract val layoutResId: Int
    protected lateinit var fragment: View
    protected var disposable: CompositeDisposable? = null

    protected fun loadable(): Boolean = false
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragment = if (loadable()) LoadableLayout(ctx, layoutResId).showLoading(GlobalConfig.LoadingConfig())
        else inflater.inflate(layoutResId, container, false)
        return fragment
    }
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fragment.titlebar_left?.onClick { doLeft() }
        fragment.titlebar_right?.onClick { doRight() }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        ActivityResultDispatch.onActivityResult(this, requestCode, resultCode, data)
    }
    override fun onDestroy() {
        disposable?.dispose()
        super.onDestroy()
    }

    protected fun setTitle(title: String) {
        setTitle(title, false)
    }
    protected fun setTitle(title: String, showBack: Boolean, right: String? = null, rightResId: Int = 0) {
        fragment.titlebar_name?.setText(title)
        fragment.titlebar_left?.visibility = if (showBack) View.VISIBLE else View.GONE
        fragment.titlebar_right?.visibility = if (right.isNullOrBlank() && rightResId == 0) View.GONE else View.VISIBLE
        if (!right.isNullOrBlank()) fragment.titlebar_right?.setText(right)
        if (rightResId != 0) Sketch.set_rightDrawable(fragment, R.id.titlebar_right, rightResId)
    }
    protected fun setTitle(title: String, left: String?, leftResId: Int, right: String?, rightResId: Int) {
        fragment.titlebar_name?.setText(title)
        fragment.titlebar_left?.visibility = if (left.isNullOrBlank() && leftResId == 0) View.GONE else View.VISIBLE
        if (!left.isNullOrBlank()) fragment.titlebar_left?.setText(left)
        if (leftResId != 0) Sketch.set_leftDrawable(fragment, R.id.titlebar_left, leftResId)
        fragment.titlebar_right?.visibility = if (right.isNullOrBlank() && rightResId == 0) View.GONE else View.VISIBLE
        if (!right.isNullOrBlank()) fragment.titlebar_right?.setText(right)
        if (rightResId != 0) Sketch.set_rightDrawable(fragment, R.id.titlebar_right, rightResId)
    }

    protected fun doLeft() {    }
    protected fun doRight() {    }

    private var snackbar: Snackbar? = null
    protected fun showError(message: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        showSnackbar(message, actionLabel, action, ResourcesCompat.getColor(getResources(), R.color.md_red_200, null))
    }
    protected fun showInfo(message: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        showSnackbar(message, actionLabel, action, 0xff039BE5.toInt(), icon = R.drawable.ic_info_white_18dp)
    }
    protected fun showSnackbar(message: String, actionLabel: String? = null, action: (() -> Unit)? = null, color: Int = 0xffFF5252.toInt(), icon: Int = R.drawable.ic_warning_white_18dp) {
        val warningIcon = ResourcesCompat.getDrawable(getResources(), icon, null)
        val builder = SpannableStringBuilder()
        builder.append(message)
        snackbar = Snackbar.make(fragment, builder, Snackbar.LENGTH_LONG)
        if (!actionLabel.isNullOrBlank() && action != null) snackbar?.setAction(actionLabel, { action() })
        else snackbar?.setAction("关闭") { hideError() }
        snackbar?.view?.findViewById<TextView>(android.support.design.R.id.snackbar_text)?.apply {
            setCompoundDrawablesWithIntrinsicBounds(warningIcon, null, null, null)
            compoundDrawablePadding = getResources().getDimensionPixelOffset(R.dimen.icon_8dp)
        }
        snackbar?.view?.setBackgroundColor(color)
        snackbar?.show()
    }
    protected fun hideError(){
        snackbar?.dismiss()
        snackbar = null
    }

    companion object {
        fun <T : Fragment> newInstance(clazz: Class<T>, args: Intent? = null): T {
            var fragment: T? = null
            try {
                fragment = clazz.newInstance() as T
                fragment.arguments = args?.extras
            } catch (e: java.lang.InstantiationException) {
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
            return fragment!!
        }
    }
}
