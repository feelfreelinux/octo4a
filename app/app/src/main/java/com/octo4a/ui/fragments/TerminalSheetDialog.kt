package com.octo4a.ui.fragments

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.asLiveData
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.octo4a.R
import com.octo4a.repository.LogEntry
import com.octo4a.repository.LogType
import com.octo4a.repository.LoggerRepository
import kotlinx.android.synthetic.main.fragment_terminal_sheet.*
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject


class TerminalSheetDialog: BottomSheetDialogFragment() {
    val logger: LoggerRepository by inject()
    var logCache = mutableListOf<LogEntry>()
    var shouldUpdateTermUi = true
    private val refreshRate = 500L

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logger.logHistoryFlow.asLiveData().observe(viewLifecycleOwner) {
            logCache.add(it)
        }

        // Update logs every 1/4 second, takes care of rapid logs
        CoroutineScope(Dispatchers.IO).launch {
            while(shouldUpdateTermUi) {
                if (logCache.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        if (terminalView == null) return@withContext
                        var textToDisplay: CharSequence? = terminalView.text

                        logCache.forEach {
                            // Display logs
                            var prefix = ""
                            var color = 0
                            when (it.type) {
                                LogType.BOOTSTRAP -> {
                                    color = Color.MAGENTA
                                    prefix = "B"
                                }
                                LogType.OCTOPRINT -> {
                                    color = Color.GREEN
                                    prefix = "O"
                                }
                                LogType.SYSTEM -> {
                                    color = Color.RED
                                    prefix = "S"
                                }
                                LogType.OTHER -> {
                                    color = Color.GRAY
                                    prefix = "?"
                                }
                            }

                            // Set prefix color via lovely span api
                            val fullText = SpannableString("$prefix: ${it.entry}\n")
                            fullText.setSpan(ForegroundColorSpan(color), 0, 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            textToDisplay = TextUtils.concat(textToDisplay, fullText)
                        }

                        terminalView?.setText(textToDisplay, TextView.BufferType.SPANNABLE)

                        // Auto scroll if enabled
                        if (enableAutoScroll.isChecked) {
                            scrollView.fullScroll(View.FOCUS_DOWN)
                        }
                    }

                    logCache.clear()
                }
                delay(refreshRate)
            }
        }


        closeDialog.setOnClickListener {
            dismiss()
        }

        shareLogs.setOnClickListener {
            val shareIntent: Intent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, terminalView.text.toString())
            startActivity(Intent.createChooser(shareIntent, "Share logs"))

        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_terminal_sheet, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        bottomSheetDialog.setOnShowListener { dialog: DialogInterface ->
            val dialogc = dialog as BottomSheetDialog
            val bottomSheet =
                dialogc.findViewById<FrameLayout>(R.id.design_bottom_sheet) as FrameLayout
            val bottomSheetBehavior: BottomSheetBehavior<*> = BottomSheetBehavior.from(bottomSheet)
            bottomSheetBehavior.peekHeight = Resources.getSystem().displayMetrics.heightPixels
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
        return bottomSheetDialog
    }

    override fun onDestroy() {
        super.onDestroy()
        shouldUpdateTermUi = false
    }
}