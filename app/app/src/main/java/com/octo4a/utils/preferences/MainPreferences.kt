package com.octo4a.utils.preferences

import android.content.Context

class MainPreferences(context: Context) : Preferences(context, true) {
    var enableCameraServer by booleanPref()
    var selectedCamera by stringPref(defaultValue = null)
    var selectedResolution by stringPref()
    var selectedVideoResolution by stringPref()
    var enableSSH by booleanPref(defaultValue = false)
    var disableAF by booleanPref(defaultValue = false)
    var manualAF by booleanPref(defaultValue = false)
    var manualAFValue by floatPref(defaultValue = 0f)
    var changeSSHPassword by stringPref()
    var sshPort by stringPref(defaultValue = "8022")
    var flashWhenObserved by booleanPref()
    var currentRelease by stringPref(defaultValue = "")
    var defaultPrinterPid by intPref()
    var defaultPrinterVid by intPref()
    var defaultPrinterCustomDriver by stringPref()
    var imageRotation by stringPref(defaultValue = "0")
    var fpsLimit by stringPref(defaultValue = "-1")
    var sshPasword by stringPref()
    var enableBugReporting by booleanPref(defaultValue = false)
    var hasAskedAboutReporting by booleanPref(defaultValue = false)
    var extensionSettings by stringPref(defaultValue = "[]")
    var updateDismissed by stringPref()
    var startOnBoot by booleanPref(defaultValue = false)
    var warnDisableBatteryOptimization by booleanPref(defaultValue = true)
}