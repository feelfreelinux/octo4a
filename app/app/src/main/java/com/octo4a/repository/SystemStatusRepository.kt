//package com.octo4a.repository
//
//package com.octo4a.repository
//
//import android.content.Context
//import com.octo4a.octoprint.BootstrapUtils
//import com.octo4a.utils.*
//import com.octo4a.utils.preferences.MainPreferences
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlin.math.roundToInt
//
//enum class ServerStatus(val value: Int) {
//    InstallingBootstrap(0),
//    DownloadingOctoPrint(1),
//    InstallingDependencies(2),
//    BootingUp(3),
//    Running(4),
//    Stopped(5)
//}
//
//data class UsbDeviceStatus(val isAttached: Boolean, val port: String = "")
//
//fun ServerStatus.getInstallationProgress(): Int {
//    return ((value.toDouble() / 4) * 100).roundToInt()
//}
//
//fun ServerStatus.isInstallationFinished(): Boolean {
//    return value == ServerStatus.Running.value
//}
//
//interface SystemStatusRepository {
//    val serverState: StateFlow<ServerStatus>
//    val usbDeviceStatus: StateFlow<UsbDeviceStatus>
//
//    fun setServerStatus(status: ServerStatus)
//    fun setSSHEnabled(isSSHEnabled: Boolean)
//    fun usbDevicePluggedIn(port: String)
//    fun usbDeviceDetached()
//    fun setCameraServerRunning(isRunning: Boolean)
//}
//
//class SystemStatusRepositoryImpl() : SystemStatusRepository {
//    private var _serverState = MutableStateFlow(ServerStatus.InstallingBootstrap)
//    private var _usbDeviceStatus = MutableStateFlow(UsbDeviceStatus(false))
//
//}