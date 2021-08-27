package com.octo4a

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.octo4a.ui.InitialActivity
import com.octo4a.utils.preferences.MainPreferences


class BootReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val preferences = MainPreferences(context)

        if (preferences.startOnBoot) {
            restartApp(context)
        }
    }

    // I'm guessing android made it impossible to straight up startActivity at boot in newer sdk versions
    // BUT HEY OFC THERE'S A WEIRD WORKAROUND THAT BYPASSES IT ON STACKOVERFLOW
    private fun restartApp(context: Context) {
        try {
            val restartTime = (1000 * 5).toLong()
            val intents = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val restartIntent = PendingIntent.getActivity(context, 0, intents, PendingIntent.FLAG_ONE_SHOT)
            val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mgr.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + restartTime,
                    restartIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mgr.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + restartTime, restartIntent)
            }
        } catch (e: Exception) {
        }
    }
}