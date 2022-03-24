package com.octo4a.ui

import android.app.Activity
import android.app.AlertDialog
import com.octo4a.Octo4aApplication
import com.octo4a.R
import com.octo4a.utils.preferences.MainPreferences

fun Activity.showBugReportingDialog(prefs: MainPreferences) {
    if (!prefs.hasAskedAboutReporting) {
        val builder = AlertDialog.Builder(this)
        builder.apply {
            setTitle(getString(R.string.bugreport_dialog_title))
            setMessage(getString(R.string.bugreport_dialog_msg))
            setNegativeButton(getString(R.string.bugreport_dialog_dismiss)) { dialog, id ->
                prefs.hasAskedAboutReporting = true
                prefs.enableBugReporting = false
            }
            setPositiveButton(getString(R.string.bugreport_dialog_enable)) { dialog, id ->
                prefs.hasAskedAboutReporting = true
                prefs.enableBugReporting = true
                (application as Octo4aApplication).startBugsnag()
            }
        }
        val dialog = builder.create()
        dialog.show()
    }
}