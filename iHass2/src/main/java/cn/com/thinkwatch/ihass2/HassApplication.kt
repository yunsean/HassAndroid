package cn.com.thinkwatch.ihass2

import android.content.Context
import android.support.v4.app.Fragment
import com.dylan.common.application.Application
import com.facebook.stetho.Stetho
import com.yunsean.dynkotlins.extensions.readPref
import com.yunsean.dynkotlins.extensions.savePref
import org.xutils.x

open class HassApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        application = this
        x.Ext.init(this)
        Stetho.initialize(Stetho.newInitializerBuilder(this)
                .enableWebKitInspector(Stetho.defaultInspectorModulesProvider(this))
                .build())
    }

    var haHostUrl: String = ""
        get() {
            if (field.isNotBlank()) return field
            val value = readPref("Hass_HostUrl")
            if (value.isNullOrBlank()) return ""
            field = value!!
            return field
        }
        set(value) {
            field = value
            savePref("Hass_HostUrl", field)
        }
    var haPassword: String = ""
        get() {
            if (field.isNotBlank()) return field
            val value = readPref("Hass_Password")
            if (value.isNullOrBlank()) return ""
            field = value!!
            return field
        }
        set(value) {
            field = value
            savePref("Hass_Password", field)
        }

    companion object {
        lateinit var application: HassApplication
            private set
    }
}


inline val Context.app: HassApplication
    get() = HassApplication.application
inline val Fragment.app: HassApplication
    get() = HassApplication.application