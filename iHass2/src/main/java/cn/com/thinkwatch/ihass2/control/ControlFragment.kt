package cn.com.thinkwatch.ihass2.control

import android.annotation.TargetApi
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import cn.com.thinkwatch.ihass2.bus.EntityChanged
import cn.com.thinkwatch.ihass2.model.JsonEntity
import com.dylan.common.rx.RxBus2
import com.google.gson.Gson
import fr.tvbarthel.lib.blurdialogfragment.BlurDialogEngine
import io.reactivex.disposables.Disposable

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
abstract open class ControlFragment : DialogFragment() {
    protected var entity: JsonEntity? = null
    private var blurEngine: BlurDialogEngine? = null
    private var disposable: Disposable? = null
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        blurEngine = BlurDialogEngine(activity)
        blurEngine?.apply {
            setBlurRadius(5)
            setDownScaleFactor(6f)
            debug(false)
            setBlurActionBar(false)
            setUseRenderScript(true)
        }
        disposable = RxBus2.getDefault().register(EntityChanged::class.java) {
            if (it.entity.entityId.equals(entity?.entityId)) {
                entity = it.entity
                onChange()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entity = try { Gson().fromJson(arguments?.getString("entity"), JsonEntity::class.java) } catch (_: Exception) { null }
    }
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        blurEngine?.onDismiss()
    }
    override fun onResume() {
        super.onResume()
        blurEngine?.onResume(retainInstance)
    }
    override fun onDestroy() {
        super.onDestroy()
        disposable?.dispose()
        blurEngine?.onDetach()
    }
    override fun onDestroyView() {
        dialog?.setDismissMessage(null)
        super.onDestroyView()
    }
    abstract fun onChange()
    fun show(manager: FragmentManager) {
        super.show(manager, "detail")
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
