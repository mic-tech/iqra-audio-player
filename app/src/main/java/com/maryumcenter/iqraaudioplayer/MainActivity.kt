package com.maryumcenter.iqraaudioplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maryumcenter.iqraaudioplayer.ui.IqraAudioPlayerTheme
import com.maryumcenter.iqraaudioplayer.ui.PlayerScreen

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
            if (treeUri != null) viewModel.setRoot(treeUri)
        }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askForNotificationPermission()

        setContent {
            IqraAudioPlayerTheme {
                val state by viewModel.ui.collectAsStateWithLifecycle()
                PlayerScreen(
                    state = state,
                    progress = viewModel.progress,
                    onPickFolder = { pickFolder.launch(null) },
                    onRescan = viewModel::rescan,
                    onPlayTrack = viewModel::playTrack,
                    onTogglePlay = viewModel::togglePlayPause,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                    onSeekBack = viewModel::seekBack,
                    onSeekForward = viewModel::seekForward,
                    onSeekTo = viewModel::seekTo,
                    onToggleRepeat = viewModel::toggleRepeat,
                )
            }
        }
    }

    /** Without this the playback notification is silently dropped on Android 13+. */
    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
