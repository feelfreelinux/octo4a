package com.octo4a.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import com.octo4a.R
import com.octo4a.repository.ServerStatus
import com.octo4a.utils.log
import com.octo4a.viewmodel.InstallationViewModel
import com.octo4a.viewmodel.StatusViewModel
import kotlinx.android.synthetic.main.fragment_server.*
import kotlinx.android.synthetic.main.view_status_card.view.*
import org.koin.androidx.viewmodel.ext.android.sharedViewModel

class ServerFragment : Fragment() {
    private val statusViewModel: StatusViewModel by sharedViewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_server, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statusViewModel.serverStatus.observe(viewLifecycleOwner) {
            when (it) {
                ServerStatus.Running -> {
                    serverStatus.setDrawableAndColor(R.drawable.ic_stop_24px, android.R.color.holo_red_light)
                    serverStatus.title = resources.getString(R.string.status_running)
                    serverStatus.subtitle = "192.168.254.103:5000"
                    serverStatus.onActionClicked = {
                        statusViewModel.stopServer()
                    }
                }

                ServerStatus.BootingUp -> {
                    serverStatus.title = resources.getString(R.string.status_starting)
                    serverStatus.subtitle = resources.getString(R.string.status_starting_subtitle)
                }

                ServerStatus.Stopped -> {
                    serverStatus.setDrawableAndColor(R.drawable.ic_play_arrow_24px, R.color.iconGreen)
                    serverStatus.title = resources.getString(R.string.status_stopped)
                    serverStatus.subtitle = resources.getString(R.string.status_stopped_start)
                    serverStatus.onActionClicked = {
                        statusViewModel.startServer()
                    }
                }
                else -> {}
            }
            serverStatus.actionProgressbar.isGone = it != ServerStatus.BootingUp
            serverStatus.actionButton.isGone = it == ServerStatus.BootingUp
        }
    }
}