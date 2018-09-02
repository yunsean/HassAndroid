package cn.com.thinkwatch.ihass2.https

import cn.com.thinkwatch.ihass2.HassApplication
import com.yunsean.dynkotlins.extensions.readPref
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.*


object SSLSocketClient {

    fun sslSocketFactory(trustManager: Array<TrustManager>): SSLSocketFactory {
        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustManager, null)
            return sslContext.getSocketFactory()
        } catch (e:Exception) {
            throw RuntimeException(e)
        }
    }

    fun hostnameVerifier(): HostnameVerifier {
        return object: HostnameVerifier {
            override fun verify(p0: String?, p1: SSLSession?): Boolean {
                return true
            }
        }
    }

    fun trustAllManager(): SSLSocketFactory {
        val trustManagers = arrayOf(object: X509TrustManager {
            override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {
            }
            override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf()
            }
        })
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustManagers, null)
        return sslContext.getSocketFactory()
    }

    fun hassClientManger(): SSLSocketFactory {
        val context = HassApplication.application
        val certs = context.readPref(arrayOf("clientFile", "clientPwd", "trustFile", "trustPwd", "trustType"))
        val clientFile = certs[0]
        val clientPwd = certs[1] ?: ""
        val trustFile = certs[2]
        val trustPwd = certs[3] ?: ""
        val trustType = certs[4]
        var keyStream: InputStream? = null
        var trustStream: InputStream? = null
        try {
            if (clientFile.isNullOrBlank() || trustFile.isNullOrBlank()) return trustAllManager()
            val keyStore = KeyStore.getInstance("BKS")
            keyStream = context.openFileInput(clientFile)
            keyStore.load(keyStream, clientPwd.toCharArray())

            val trustStore: KeyStore
            if (trustType == "BKS") {
                trustStore = KeyStore.getInstance("BKS")
                trustStream = context.openFileInput(trustFile)
                trustStore.load(trustStream, trustPwd.toCharArray())
            } else {
                val factory = CertificateFactory.getInstance("X.509")
                trustStream = context.openFileInput(trustFile)
                val ca = factory.generateCertificate(trustStream)
                val trustStoreType = KeyStore.getDefaultType()
                trustStore = KeyStore.getInstance(trustStoreType)
                trustStore.load(null, null)
                trustStore.setCertificateEntry("ca", ca)
            }

            val trustManagerFactory = TrustManagerFactory.getInstance("X509")
            val keyManagerFactory = KeyManagerFactory.getInstance("X509")
            trustManagerFactory.init(trustStore)
            keyManagerFactory.init(keyStore, clientPwd.toCharArray())
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null)
            return sslContext.getSocketFactory()
        } catch (ex: Exception) {
            ex.printStackTrace()
            return trustAllManager()
        } finally {
            keyStream?.close()
            trustStream?.close()
        }
    }
}