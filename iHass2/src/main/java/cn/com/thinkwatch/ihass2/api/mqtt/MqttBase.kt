package cn.com.thinkwatch.ihass2.api.mqtt

import android.content.Context
import cn.com.thinkwatch.ihass2.api.BaseApi
import com.dylan.common.utils.Utility
import com.yunsean.dynkotlins.extensions.error
import com.yunsean.dynkotlins.extensions.nextOnMain
import com.yunsean.dynkotlins.extensions.toastex
import io.reactivex.ObservableEmitter
import java.lang.reflect.Type
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

open class MqttBaseApi(context: Context) {

    private var invokeIndex: Long = 0
    private val invokeItems = mutableMapOf<Long, InvokeItem>()
    private var mqttClient = MqttBase.instance
    private val deviceId: String
    init {
        deviceId = Utility.getFakeId(context)
        mqttClient.subscribe("/dylan/${deviceId}", 1).toObservable()
                .nextOnMain {
                    val body = decrypt(it.payload)
                    onEvent(body)
                }
                .error {
                    it.toastex()
                }
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
    private fun decrypt(aes: ByteArray?): String {
        val iv = "9999999999999999"
        val key = "1234567890123456"
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivspec = IvParameterSpec(iv.toByteArray())
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivspec)
        return String(cipher.doFinal(aes))
    }
    private fun callError(emitter: ObservableEmitter<Any?>, error: Throwable) = try { emitter.onError(error) } catch (_: Exception) { }
    private fun onEvent(body: String) {
        val response = BaseApi.gson.fromJson<ResponseBody>(body, ResponseBody::class.java)
        if (response == null) return
        if (response.index != null) {
            invokeItems.get(response.index)?.let {
                (it.emitter as ObservableEmitter<Any?>).apply {
                    if (!response.reason.isNullOrBlank()) {
                        callError(this, Exception(response.reason))
                    } else if (it.type == Unit::class.java) {
                        onComplete()
                    } else if (it.type == String::class.java) {
                        onNext(response.result ?: "")
                        onComplete()
                    } else if (response.result.isNullOrBlank()) {
                        callError(this, Exception("The result is null."))
                    } else {
                        try {
                            val result: Any? = BaseApi.gson.fromJson(response.result, it.type)
                            if (result != null) onNext(result)
                            onComplete()
                        } catch (ex: Exception) {
                            callError(this, ex)
                        }
                    }
                }
            }
        }
    }

    protected fun compositePath(path: String, queries: Map<String, Any?>): String {
        val sb = StringBuilder(path)
        var first = true
        queries.forEach { key, value ->
            if (value != null) {
                if (first) {
                    sb.append("?")
                    first = false
                } else {
                    sb.append("&")
                }
                sb.append(key).append("=")
                sb.append(URLEncoder.encode(value.toString(), "UTF-8"))
            }
        }
        return sb.toString()
    }

    @Synchronized private fun nextIndex(): Long = invokeIndex++
    protected fun request(emitter: ObservableEmitter<*>, method: String, path: String, body: Any? = null, type: Type) {
        val params = RequestParams(method, path, body, deviceId, nextIndex())
        val payload = encrypt(BaseApi.gson.toJson(params))
        mqttClient.publish("/dylan/hass", payload, 1, false).toObservable()
                .error {
                    emitter.onError(it)
                }
        invokeItems.set(params.index, InvokeItem(type, emitter))
    }

    protected data class RequestParams(val method: String,
                                       val path: String,
                                       val body: Any? = null,
                                       val deviceId: String,
                                       val index: Long)
    protected data class ResponseBody (val index: Long? = null,
                                          val result: String? = null,
                                          val reason: String? = null)
    protected data class InvokeItem(val type: Type,
                                    val emitter: ObservableEmitter<*>)
}