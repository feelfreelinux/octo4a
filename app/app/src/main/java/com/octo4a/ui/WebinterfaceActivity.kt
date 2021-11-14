package com.octo4a.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.octo4a.R
import kotlinx.android.synthetic.main.activity_webinterface.*

class WebinterfaceActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webinterface)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)
        webview.visibility = View.GONE
        webview.webViewClient = WebinterfaceClient(this, intent.data.authority)
        webview.settings.loadsImagesAutomatically = true
        webview.settings.javaScriptEnabled = true
        webview.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        webview.loadUrl(intent.data.toString())
    }

    private fun webviewFinished() {
        webview.visibility = View.VISIBLE
        loadingIndicator.visibility = View.GONE
    }

    private class WebinterfaceClient(val activity: WebinterfaceActivity, val startAuthority: String): WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            activity.webviewFinished()
            super.onPageFinished(view, url)
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            return request?.url?.authority != startAuthority
        }
    }


}
