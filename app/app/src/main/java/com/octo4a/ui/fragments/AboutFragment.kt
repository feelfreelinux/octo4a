package com.octo4a.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.octo4a.R
import kotlinx.android.synthetic.main.fragment_about.*


class AboutFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        val version = pInfo.versionName

        versionTextString.text = "Build: $version"
    }
}