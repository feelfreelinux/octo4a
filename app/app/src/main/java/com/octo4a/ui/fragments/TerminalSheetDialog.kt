package com.octo4a.ui.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.asLiveData
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.octo4a.R
import com.octo4a.repository.LogType
import com.octo4a.repository.LoggerRepository
import kotlinx.android.synthetic.main.fragment_terminal_sheet.*
import org.koin.android.ext.android.inject


class TerminalSheetDialog: BottomSheetDialogFragment() {
    val logger: LoggerRepository by inject()

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logger.logHistoryFlow.asLiveData().observe(viewLifecycleOwner) {
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
            terminalView.setText(TextUtils.concat(terminalView.text, fullText), TextView.BufferType.SPANNABLE)

            // Auto scroll if enabled
            if (enableAutoScroll.isChecked) {
                scrollView.fullScroll(View.FOCUS_DOWN)
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
}