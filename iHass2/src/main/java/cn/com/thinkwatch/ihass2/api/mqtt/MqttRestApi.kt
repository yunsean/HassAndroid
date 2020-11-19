package cn.com.thinkwatch.ihass2.api.mqtt

import android.content.Context
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.api.BaseApi
import cn.com.thinkwatch.ihass2.dto.ServiceRequest
import cn.com.thinkwatch.ihass2.model.JsonEntity
import com.dylan.common.utils.Utility
import com.google.gson.reflect.TypeToken
import com.yunsean.dynkotlins.extensions.logis
import com.yunsean.dynkotlins.extensions.nextOnMain
import com.yunsean.dynkotlins.extensions.toastex
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import net.eusashead.iot.mqtt.PublishMessage
import net.eusashead.iot.mqtt.paho.PahoObservableMqttClient
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MqttApi(context: Context) {

    private var invokeIndex: Long = 0
    private val invokeItems = mutableMapOf<Long, InvokeItem>()
    private var mqttClient: PahoObservableMqttClient
    init {
        val paho = MqttAsyncClient("tcp://jiwei.001xin.com:1883", Utility.getFakeId(context), MemoryPersistence())
        mqttClient = PahoObservableMqttClient.builder(paho).build()
        mqttClient.connect().subscribe({
            logis("mqtt connected")
            mqttClient.subscribe("/dylan/public", 1).toObservable()
                    .nextOnMain {
                        val body = decrypt(it.payload)
                        onEvent(body)
                    }
                    .error {
                        it.toastex()
                    }
        }, { it.toastex() })
    }

    private fun encrypt(message: String): ByteArray {
        val iv = "9999999999999999"
        val key = "1234567890123456"
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivspec = IvParameterSpec(iv.toByteArray())
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivspec)
        return cipher.doFinal(message.toByteArray())
    }
    private fun decrypt(aes: ByteArray): String {
        val iv = "9999999999999999"
        val key = "1234567890123456"
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivspec = IvParameterSpec(iv.toByteArray())
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivspec)
        return String(cipher.doFinal(aes))
    }
    private fun onEvent(body: String) {
        val response = BaseApi.gson.fromJson<ResponseBody>(body, ResponseBody::class.java)
        if (response == null) return
        if (response.index != null) {
            invokeItems.get(response.index)?.let {
                val result = BaseApi.gson.fromJson<Any>(response.body, it.type)
                (it.emitter as ObservableEmitter<Any?>).onNext(result)
            }
        }
    }

    @Synchronized fun nextIndex(): Long = invokeIndex++
    fun getStates(): Observable<ArrayList<JsonEntity>>? {
        return Observable.create<ArrayList<JsonEntity>> {
            val params = RequestParams(nextIndex(), "/api/states")
            val payload = encrypt(BaseApi.gson.toJson(params))
            val message = PublishMessage.create(payload, 1, false)
            mqttClient.publish("/dylan/hass", message)
            invokeItems.set(params.index, InvokeItem(object : TypeToken<ArrayList<JsonEntity>?>() {}.type, it))
        }.timeout(5, TimeUnit.SECONDS)
    }

    private data class RequestParams(val index: Long,
                                     val path: String,
                                     val domain: String? = null,
                                     val service: String? = null,
                                     val request: ServiceRequest? = null,
                                     val entity: JsonEntity? = null,
                                     val endTime: String? = null,
                                     val latitude: Double? = null,
                                     val longitude: Double? = null,
                                     val device: String? = null,
                                     val accuracy: String? = null,
                                     val provider: String? = null,
                                     val gpsPassword: String? = null)
    private data class ResponseBody(val index: Long? = null,
                                    val succeed: Boolean? = null,
                                    val body: String? = null)
    private data class InvokeItem(val type: Type,
                                  val emitter: ObservableEmitter<*>)

    companion object {
        var jsonApi: MqttApi = MqttApi(HassApplication.application)
    }
}