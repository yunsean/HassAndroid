package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import cn.com.thinkwatch.ihass2.bus.ControlDismissed
import cn.com.thinkwatch.ihass2.bus.EntityChanged
import cn.com.thinkwatch.ihass2.model.JsonEntity
import com.dylan.common.rx.RxBus2
import com.google.gson.Gson
import io.reactivex.disposables.CompositeDisposable
import java.util.*

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
abstract open class ControlFragment : DialogFragment() {
    protected var entity: JsonEntity? = null
    protected var disposable: CompositeDisposable? = null
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        disposable = RxBus2.getDefault().register(EntityChanged::class.java, {
            if (it.entity.entityId.equals(entity?.entityId)) {
                entity = it.entity
                onChange()
            }
        }, disposable)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entity = try { Gson().fromJson(arguments?.getString("entity"), JsonEntity::class.java) } catch (_: Exception) { null }
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
        if (Math.abs(lhs.time - rhs.time) < 10_000) return true
        else return false
    }
    companion object {
        fun <T: ControlFragment> newInstance(entity: JsonEntity, clazz: Class<T>): T {
            val fragment = clazz.newInstance()
            val args = Bundle()
            args.putString("entity", Gson().toJson(entity))
            fragment.arguments = args
            return fragment
        }
    }
}
