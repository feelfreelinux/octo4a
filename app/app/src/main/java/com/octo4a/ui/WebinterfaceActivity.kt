package com.octo4a.ui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.octo4a.R
import kotlinx.android.synthetic.main.activity_webinterface.*

const val WEBINTERFACE_ADDRESS = "127.0.0.1:5000"

class WebinterfaceActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webinterface)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)
        webview.visibility = View.GONE
        webview.webViewClient = WebinterfaceClient(this)
        webview.settings.loadsImagesAutomatically = true
        webview.settings.javaScriptEnabled = true
        webview.settings.domStorageEnabled = true
        webview.settings.userAgentString = "TouchUI"
        webview.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        webview.loadUrl("http://$WEBINTERFACE_ADDRESS")
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onResume() {
        if (!isPinned()) {
            startLockTask()
        }
        super.onResume()
    }

    @Suppress("DEPRECATION")
    fun isPinned(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (activityManager.lockTaskModeState
                    != ActivityManager.LOCK_TASK_MODE_NONE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            activityManager.isInLockTaskMode
        } else {
            false
        }
    }

    override fun onBackPressed() {
        if (!isPinned()){
            super.onBackPressed()
        } else if (webview.canGoBack()) {
            webview.goBack()
        }
    }

    private fun webviewFinished() {
        webview.visibility = View.VISIBLE
        loadingIndicator.visibility = View.GONE
    }

    private class WebinterfaceClient(val activity: WebinterfaceActivity): WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            activity.webviewFinished()
            super.onPageFinished(view, url)
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            return request?.url?.authority != WEBINTERFACE_ADDRESS
        }
    }


}
