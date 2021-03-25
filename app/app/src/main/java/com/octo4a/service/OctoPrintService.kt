package com.octo4a.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.asLiveData
import com.octo4a.ui.MainActivity
import com.octo4a.R
import com.octo4a.repository.BootstrapRepository
import com.octo4a.repository.FIFOEventRepository
import com.octo4a.repository.OctoPrintHandlerRepository
import com.octo4a.repository.ServerStatus
import com.octo4a.serial.VirtualSerialDriver
import com.octo4a.utils.log
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject

// OctoprintService handles foreground service that OctoPrintManager resides in
class OctoPrintService() : LifecycleService() {
    private val handlerRepository: OctoPrintHandlerRepository by inject()
    private val bootstrapRepository: BootstrapRepository by inject()
    private val fifoEventRepository: FIFOEventRepository by inject()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    companion object {
        // Constants
        const val LOG_TAG = "Octo4a_Service"

        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "octo4a_notification_channel"

        const val BROADCAST_SERVICE_RECEIVE_ACTION = "com.octo4a.service_receive_event"
        const val BROADCAST_SERVICE_USB_GOT_ACCESS = "com.octo4a.usb_access_received"
        const val EVENT_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED"
        const val EVENT_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED"
        const val EXTRA_EVENTDATA = "EXTRA_EVENTDATA"
    }

    val virtualSerialDriver: VirtualSerialDriver by inject()

    // Prepares intent filter for broadcast receiver
    private val intentFilter by lazy {
        val filter = IntentFilter()
        filter.addAction(BROADCAST_SERVICE_RECEIVE_ACTION)
        filter.addAction(BROADCAST_SERVICE_USB_GOT_ACCESS)
        filter.addAction(EVENT_USB_ATTACHED)
        filter.addAction(EVENT_USB_DETACHED)

        filter
    }

    private val notificationBuilder by lazy {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OctoPrint")
            .setContentText("...")
            .setVibrate(null)
            .setOnlyAlertOnce(true)
            .setSmallIcon(R.drawable.ic_print_24px)
            .setContentIntent(pendingIntent)
    }

    private fun updateNotificationStatus(status: String) {
        notificationBuilder.setContentText(status)
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                EVENT_USB_ATTACHED -> {
                    Log.v(LOG_TAG, "USB Device attached :)")
                        virtualSerialDriver.updateDevicesList(BROADCAST_SERVICE_USB_GOT_ACCESS)?.apply {
                        handlerRepository.usbAttached(this)
                    }
                }

                EVENT_USB_DETACHED -> {
                    handlerRepository.usbDetached()
                }

                BROADCAST_SERVICE_USB_GOT_ACCESS -> {
                    virtualSerialDriver.updateDevicesList(BROADCAST_SERVICE_USB_GOT_ACCESS)?.apply {
                        handlerRepository.usbAttached(this)
                    }
                }
            }

        }
    }

    override fun onCreate() {
        registerReceiver(broadcastReceiver, intentFilter)
        bootstrapRepository.ensureHomeDirectory()
        virtualSerialDriver.initializeVSP()
        virtualSerialDriver.handlePtyThread()
        scope.launch {
            handlerRepository.beginInstallation()
        }

        handlerRepository.serverState.asLiveData().observe(this) {
            updateNotificationStatus(
                when (it) {
                    ServerStatus.Running -> resources.getString(R.string.notification_status_running)
                    ServerStatus.Stopped -> resources.getString(R.string.notification_status_stopped)
                    ServerStatus.InstallingDependencies, ServerStatus.InstallingBootstrap, ServerStatus.DownloadingOctoPrint -> resources.getString(R.string.notification_status_installing)
                    else -> resources.getString(R.string.notification_status_starting)
                }
            )
        }
        fifoEventRepository.eventState.observe(this) {
            when (it.eventType) {
                "stopServer" -> handlerRepository.stopOctoPrint()
                "restartServer" -> scope.launch {
                    log { "UH" }
                    handlerRepository.stopOctoPrint()
                    delay(5000)
                    handlerRepository.startOctoPrint()
                }
            }
        }
        super.onCreate()
    }

    override fun onDestroy() {
        unregisterReceiver(broadcastReceiver)
        virtualSerialDriver.stopPtyThread()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundNotification()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Octo4A Notification Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}