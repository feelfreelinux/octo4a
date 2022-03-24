package com.octo4a.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.asLiveData
import com.octo4a.R
import com.octo4a.repository.ExtensionsRepository
import com.octo4a.repository.OctoPrintHandlerRepository
import com.octo4a.ui.views.ExtensionView
import kotlinx.android.synthetic.main.fragment_extensions.*
import org.koin.android.ext.android.inject

class ExtensionsFragment : Fragment() {
    val octoPrintHandlerRepository: OctoPrintHandlerRepository by inject()
    val extensionsRepository: ExtensionsRepository by inject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_extensions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        extensionsRepository.getValidExtensions()
        extensionsRepository.extensionsState.asLiveData().observe(viewLifecycleOwner) {
            extensionsList.removeAllViews()
            it.forEach {
                val extensionView = ExtensionView(requireContext(), extensionsRepository)
                extensionsList.addView(extensionView)
                extensionView.setExtensionDescription(it)
            }
        }

        extensionsCard.setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse("https://github.com/feelfreelinux/octo4a/wiki/Extensions-system")
            startActivity(i)
        }
    }
}