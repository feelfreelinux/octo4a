package io.feelfreelinux.octo4a.octo4a

import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.system.Os
import android.util.Log
import android.util.Pair
import androidx.annotation.NonNull;
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.GeneratedPluginRegistrant
import java.io.*
import java.net.URL
import java.util.ArrayList
import java.util.zip.ZipInputStream
import io.flutter.plugin.common.EventChannel
import androidx.lifecycle.LifecycleRegistry
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import org.json.JSONObject


class MainActivity: FlutterActivity() {
    private val PLATFORM_CHANNEL = "io.feelfreelinux.octo4a/methods"
    private val STATUS_EVENT_CHANNEL = "io.feelfreelinux.octo4a/status"

    var eventSink: EventChannel.EventSink? = null

    companion object {
        val BROADCAST_RECEIVE_ACTION = "io.feelfreelinux.octo4a.event"
    }


    private val intentFilter by lazy {
        val filter = IntentFilter()
        filter.addAction(BROADCAST_RECEIVE_ACTION)
        filter
    }

    val sharedPreferences by lazy { getSharedPreferences(packageName, Context.MODE_PRIVATE) }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            intent?.getStringExtra(OctoPrintService.EXTRA_EVENTDATA)?.let {
                eventSink?.success(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver(broadcastReceiver, intentFilter)

        startOctoService()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    private fun startOctoService() {
        val i = Intent(context, OctoPrintService::class.java)
        startService(i)
    }


    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        GeneratedPluginRegistrant.registerWith(flutterEngine)

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, STATUS_EVENT_CHANNEL).setStreamHandler(
                object : EventChannel.StreamHandler {
                    override fun onListen(args: Any?, events: EventChannel.EventSink) {
                        eventSink = events
                    }

                    override fun onCancel(args: Any?) {
                        eventSink = null
                    }
                }
        )

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, PLATFORM_CHANNEL).setMethodCallHandler {
            call, result ->
            when (call.method) {
                "verifyInstallation" -> {
                    result.success(BootstrapUtils.isBootstrapInstalled)
                }

                "startServer" -> {
                    startOctoService()
                }

                "setResolution" -> {
                    sharedPreferences.edit().putString(OctoPrintService.PREF_NAME_CAMERA_RESOLUTION, call.argument("resolution")).apply()
                }

                "beginInstallation", "queryServerStatus", "queryUsbDevices", "stopServer", "startCameraServer", "stopCameraServer", "queryCameraResolutions" -> {
                    result.success(null)
                    notifyService(call.method)
                }

                "selectUsbDevice" -> {
                    val port = call.argument<Int>("baud")
                    val json = JSONObject()
                    json.put("eventType", OctoPrintService.EVENT_TYPE_SELECT_USB_DEVICE)
                    json.put("baud", port)
                    val intent = Intent(OctoPrintService.BROADCAST_SERVICE_RECEIVE_ACTION)
                    intent.putExtra(OctoPrintService.EXTRA_EVENTDATA, json.toString())
                    sendBroadcast(intent)
                }

            }
        }
    }

    private fun notifyService(eventType: String) {
        val json = JSONObject()
        json.put("eventType", eventType)
        val intent = Intent(OctoPrintService.BROADCAST_SERVICE_RECEIVE_ACTION)
        intent.putExtra(OctoPrintService.EXTRA_EVENTDATA, json.toString())
        sendBroadcast(intent)
    }
}


