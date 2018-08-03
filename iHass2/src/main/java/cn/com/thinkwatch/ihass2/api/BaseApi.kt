package cn.com.thinkwatch.ihass2.api

import android.util.Log
import cn.com.thinkwatch.ihass2.BuildConfig
import cn.com.thinkwatch.ihass2.model.service.Field
import cn.com.thinkwatch.ihass2.model.service.Fields
import cn.com.thinkwatch.ihass2.model.service.Service
import cn.com.thinkwatch.ihass2.model.service.Services
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.lang.reflect.Type
import java.nio.charset.Charset
import java.security.cert.CertificateException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object BaseApi {
    private val CONNECTION_TIMEOUT_IN_SEC = 5
    private val DEFAULT_READ_TIMEOUT_IN_SEC = 15
    private var okHttpClient: OkHttpClient? = null
    private var wsOkHttpClient: OkHttpClient? = null
    private fun getOkHttpClientInstance(readTimeoutInSec: Int): OkHttpClient? {
        if (okHttpClient == null) okHttpClient = getOkHttpClientInstance(CONNECTION_TIMEOUT_IN_SEC, readTimeoutInSec)
        return okHttpClient
    }

    fun getWebSocketOkHttpClientInstance(): OkHttpClient {
        if (wsOkHttpClient == null) wsOkHttpClient = getOkHttpClientInstance(CONNECTION_TIMEOUT_IN_SEC, DEFAULT_READ_TIMEOUT_IN_SEC, false)
        return wsOkHttpClient!!
    }
    private fun getOkHttpClientInstance(connectTimeoutInSec: Int, readTimeoutInSec: Int, addInterceptor: Boolean = true): OkHttpClient? {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                @Throws(CertificateException::class)
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                }

                @Throws(CertificateException::class)
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                }

                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                    return arrayOf()
                }
            })
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            val sslSocketFactory = sslContext.socketFactory
            val builder = OkHttpClient.Builder()
                    .connectTimeout(connectTimeoutInSec.toLong(), TimeUnit.SECONDS)
                    .readTimeout(readTimeoutInSec.toLong(), TimeUnit.SECONDS)
                    .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                    .hostnameVerifier { hostname, session -> true }
            if (BuildConfig.BUILD_TYPE != "release") {
                val interceptor = HttpLoggingInterceptor()
                interceptor.level = HttpLoggingInterceptor.Level.BODY
                builder.addInterceptor(interceptor)
            }
            if (addInterceptor) builder.addInterceptor { chain ->
                val request = chain.request()
                Log.i("dylan", "url:${request.url()}, method:${request.method()}, body:${request.body()}")
                val startNs = System.nanoTime()
                val response: okhttp3.Response = try {
                    chain.proceed(request)
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw Exception("访问服务器错误")
                }
                if (response.code() == 401) throw Exception(Exception("密码错误"))
                else if (response.code() == 404) throw Exception(Exception("HA服务器错误"))
                val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
                val source = response.body()?.source()
                source?.request(java.lang.Long.MAX_VALUE)
                val buffer = source?.buffer()
                Log.i("dylan", "consumed:${tookMs}ms, header:${response.headers()}, body: " + buffer?.clone()?.readString(Charset.forName("UTF-8")))
                response
            }
            return builder.build()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun <T> build(clazz: Class<T>, baseUrl: String, factory: Converter.Factory): T {
        return Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(factory)
                .client(getOkHttpClientInstance(DEFAULT_READ_TIMEOUT_IN_SEC))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(io.reactivex.schedulers.Schedulers.io()))
                .build()
                .create(clazz)
    }

    val dfLong = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZZZZZ", Locale.ENGLISH)
    val dfShort = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.ENGLISH)
    fun api(baseUrl: String): RestApi = build(RestApi::class.java, baseUrl, GsonConverterFactory.create(GsonBuilder()
            .registerTypeAdapter(Date::class.java, object : JsonDeserializer<Date> {
                override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Date? {
                    try {
                        val value = json.toString().trim('"')
                        if (value.length == 25) {
                            return dfShort.parse(value)
                        } else {
                            return dfLong.parse(value)
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        return null
                    }
                }
            })
            .registerTypeAdapter(Services::class.java, object : JsonDeserializer<Services> {
                override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Services {
                    val services = mutableMapOf<String, Service>()
                    json?.let {
                        val jsonObject = it.asJsonObject
                        for (entry in jsonObject.entrySet()) {
                            val service = context?.deserialize<Service>(entry.value, Service::class.java)
                            if (service != null) services.put(entry.key, service)
                        }
                    }
                    return Services(services)
                }
            })
            .registerTypeAdapter(Fields::class.java, object : JsonDeserializer<Fields> {
                override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Fields {
                    val fields = mutableMapOf<String, Field>()
                    json?.let {
                        val jsonObject = it.asJsonObject
                        for (entry in jsonObject.entrySet()) {
                            val field = context?.deserialize<Field>(entry.value, Field::class.java)
                            if (field != null) fields.put(entry.key, field)
                        }
                    }
                    return Fields(fields)
                }
            })
            .create()))
    fun rawApi(baseUrl: String): RawApi = build(RawApi::class.java, baseUrl, ScalarsConverterFactory.create())
}
