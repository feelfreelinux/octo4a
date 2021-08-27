package com.octo4a.utils.preferences

import android.content.Context

class MainPreferences(context: Context) : Preferences(context, true) {
    var enableCameraServer by booleanPref()
    var selectedCamera by stringPref(defaultValue = null)
    var selectedResolution by stringPref()
    var enableSSH by booleanPref(defaultValue = false)
    var changeSSHPassword by stringPref()
    var sshPort by stringPref(defaultValue = "8022")
    var flashWhenObserved by booleanPref()
    var defaultPrinterPid by intPref()
    var defaultPrinterVid by intPref()
    var defaultPrinterCustomDriver by stringPref()
    var updateDismissed by stringPref()
}