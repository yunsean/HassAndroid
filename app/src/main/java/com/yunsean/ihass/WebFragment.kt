package com.yunsean.ihass

import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.view.View
import android.webkit.*
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.utils.cfg
import kotlinx.android.synthetic.main.fragment_web.*
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.onRefresh

class WebFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.fragment_web
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle("Home Assistant", false)
        val header = mutableMapOf<String, String>()
        cfg.haPassword.let { if (it.isNotBlank()) header.put("x-ha-access", it) }
        cfg.haToken.let { if (it.isNotBlank()) header.put("Authorization", it) }
        this.webView.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN)
            setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK)
            setAllowFileAccess(true)
            setNeedInitialFocus(true)
            setJavaScriptCanOpenWindowsAutomatically(true)
            setLoadsImagesAutomatically(true)
            setDefaultTextEncodingName("utf-8")
            setDefaultFontSize(20)
            setMinimumFontSize(12)
            setDomStorageEnabled(true)
            setDatabaseEnabled(true)
            setAppCacheEnabled(true)
            setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW)
        }
        this.webView.apply {
            requestFocusFromTouch()
            loadUrl(cfg.haHostUrl, header)
        }
        this.webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, true, false)
                super.onGeolocationPermissionsShowPrompt(origin, callback)
            }
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.setWebView(view)
                resultMsg.sendToTarget()
                return true
            }
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                act.pullable.isRefreshing = newProgress != 100
                super.onProgressChanged(view, newProgress);
            }
        }
        this.webView.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed()
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                view?.loadUrl(url)
                return true
            }
        }
        this.pullable.onRefresh {
            act.pullable.isRefreshing = false
            act.webView.reload()
        }
        this.pullable.setColorSchemeResources(R.color.colorAccent, R.color.colorPrimaryDark);
    }
    override fun onResume() {
        super.onResume()
        this.webView.onResume()
        this.webView.resumeTimers()
    }
    override fun onPause() {
        this.webView.onPause()
        this.webView.pauseTimers()
        super.onPause()
    }
    override fun onBackPressed(): Boolean {
        if (this.webView.canGoBack()) {
            this.webView.goBack()
            return true
        } else {
            return false
        }
    }
}

