package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import cn.com.thinkwatch.ihass2.bus.ControlDismissed
import cn.com.thinkwatch.ihass2.bus.EntityChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.JsonEntity
import cn.com.thinkwatch.ihass2.utils.Gsons
import com.dylan.common.rx.RxBus2
import com.dylan.uiparts.activity.ActivityResultDispatch
import com.dylan.uiparts.activity.RequestPermissionResultDispatch
import io.reactivex.disposables.CompositeDisposable
import java.util.*

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
abstract open class ControlFragment : DialogFragment() {
    protected var entity: JsonEntity? = null
    protected var disposable: CompositeDisposable? = null
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        disposable = RxBus2.getDefault().register(EntityChanged::class.java, {
            if (it.entityId.equals(entity?.entityId)) {
                entity = db.getEntity(it.entityId)
                onChange()
            }
        }, disposable)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entity = try { Gsons.gson.fromJson(arguments?.getString("entity"), JsonEntity::class.java) } catch (_: Exception) { null }
    }
    protected open var dismissWhenPause: Boolean = true
    override fun onPause() {
        super.onPause()
        if (dismissWhenPause) dismiss()
    }
    override fun onDestroy() {
        super.onDestroy()
        disposable?.dispose()
    }

    override fun onDismiss(dialog: DialogInterface?) {
        super.onDismiss(dialog)
        RxBus2.getDefault().post(ControlDismissed())
    }
    override fun onDestroyView() {
        dialog?.setDismissMessage(null)
        super.onDestroyView()
    }
    abstract fun onChange()
    fun show(manager: FragmentManager) {
        val transaction = manager.beginTransaction()
        transaction.add(this, tag)
        transaction.commitAllowingStateLoss()
    }

    protected fun isSimilarDate(lhs: Date?, rhs: Date?): Boolean {
        if (lhs == null && rhs == null) return true
        if (lhs == null || rhs == null) return false
        return entity?.attributes?.ihassDetailReduce?.let {
            Math.abs(lhs.time - rhs.time) < it * 1000
        } ?: false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        ActivityResultDispatch.onActivityResult(this, requestCode, resultCode, data)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        RequestPermissionResultDispatch.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }
    companion object {
        fun <T: ControlFragment> newInstance(entity: JsonEntity, clazz: Class<T>): T {
            val fragment = clazz.newInstance()
            val args = Bundle()
            args.putString("entity", Gsons.gson.toJson(entity))
            fragment.arguments = args
            return fragment
        }
    }
}
