package com.octo4a.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.LifecycleService
import com.octo4a.R
import java.time.Duration

class Octo4aWakeLock(val context: Context) {
    var wakeLock: PowerManager.WakeLock? = null
    var wifiLock: WifiManager.WifiLock? = null

    fun acquire() {
        if (wakeLock != null && wakeLock!!.isHeld  && wifiLock != null && wifiLock!!.isHeld) {
            return
        }

        val pm = context.getSystemService(LifecycleService.POWER_SERVICE) as PowerManager
        wakeLock =
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "octo4a:service-wakelock")
        wakeLock?.acquire(10*60*1000L /*10 minutes*/)


        // http://tools.android.com/tech-docs/lint-in-studio-2-3#TOC-WifiManager-Leak
        val wm = context.applicationContext.getSystemService(LifecycleService.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "octo4a:wifilock")
        wifiLock?.acquire()

        val packageName = context.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(packageName)) {
            val whitelist = Intent()
            whitelist.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            whitelist.data = Uri.parse("package:$packageName")
            whitelist.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try {
                context.startActivity(whitelist)
            } catch (e: ActivityNotFoundException) {
                log { "failed to open battery optimization dialog" }
            }
        }
    }

    fun remove() {
        wakeLock?.release()
        wifiLock?.release()
        wakeLock = null
        wifiLock = null
    }
}