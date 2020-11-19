package cn.com.thinkwatch.ihass2.api.mqtt

import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.dto.StateChanged
import cn.com.thinkwatch.ihass2.model.JsonEntity
import com.google.gson.Gson
import com.yunsean.dynkotlins.extensions.nextOnMain
import io.reactivex.disposables.Disposable

class MqttService: MqttBase() {

    var entityChanged: ((JsonEntity)-> Unit)? = null
    var disposable: Disposable? = null
    fun start() {
        mqttClient.subscribe("/dylan/public", 1).toObservable()
                .nextOnMain {
                    try {
                        onEvent(decrypt(it.payload))
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
                .subscribe {
                    disposable = it
                }
    }
    fun stop() {
        disposable?.dispose()
        disposable = null
    }
    fun callService(domain: String, service: String, request: ServiceRequest?): String? {
        return try {
            MqttApi.instance.callService(domain, service, request).blockingFirst()
            null
        } catch (ex: Exception) {
            ex.localizedMessage
        }
    }

    private fun onEvent(body: String) {
        val event = Gson().fromJson<StateChanged>(body, StateChanged::class.java)
        if (event != null) {
            val newEntity = event.event?.data?.newState
            val entityId = newEntity?.entityId
            if (newEntity != null && entityId != null) {
                entityChanged?.invoke(newEntity)
            }
        }
    }
}