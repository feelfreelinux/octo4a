package com.octo4a

import androidx.multidex.MultiDexApplication
import com.bugsnag.android.Bugsnag
import com.octo4a.utils.TLSSocketFactory
import org.koin.android.ext.koin.androidLogger
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory


class Octo4aApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        initializeSSLContext()
        // Start Koin
        startKoin {
            androidLogger()
            androidContext(this@Octo4aApplication)
            modules(appModule)
        }

        Bugsnag.start(this)
    }

    fun initializeSSLContext() {
        val noSSLv3Factory: TLSSocketFactory = TLSSocketFactory()

        HttpsURLConnection.setDefaultSSLSocketFactory(noSSLv3Factory)
    }
}