package com.octo4a

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraX
import androidx.camera.core.CameraXConfig
import com.bugsnag.android.Bugsnag
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class Octo4aApplication : Application(), CameraXConfig.Provider {
    override fun getCameraXConfig(): CameraXConfig = Camera2Config.defaultConfig()

    override fun onCreate() {
        super.onCreate()
        // Start Koin
        startKoin {
            androidLogger()
            androidContext(this@Octo4aApplication)
            modules(appModule)
        }

        Bugsnag.start(this)
    }
}