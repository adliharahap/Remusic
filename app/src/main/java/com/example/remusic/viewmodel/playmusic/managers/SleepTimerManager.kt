package com.example.remusic.viewmodel.playmusic.managers

import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import android.os.Bundle
import timber.log.Timber
import com.example.remusic.viewmodel.playmusic.PlayerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class SleepTimerManager(
    private val getMediaController: () -> MediaController?,
    private val uiState: MutableStateFlow<PlayerUiState>
) {

    private val mediaController: MediaController?
        get() = getMediaController()

    fun setSleepTimer(minutes: Int) {
       Timber.tag("DEBUG_PLAYER").d( "⏰ setSleepTimer called: $minutes minutes")
        val controller = mediaController
        if (controller == null) {
           Timber.tag("DEBUG_PLAYER").e( "❌ ERROR: MediaController is NULL. Cannot set timer.")
            return
        }

        val durationMs = minutes * 60 * 1000L
        val command =
                androidx.media3.session.SessionCommand("START_SLEEP_TIMER", android.os.Bundle.EMPTY)

        val args = android.os.Bundle().apply { putLong("DURATION", durationMs) }

        val result = controller.sendCustomCommand(command, args)
       Timber.tag("DEBUG_PLAYER").d(
                
                "🚀 Command START_SLEEP_TIMER sent with duration: $durationMs ms"
        )

        uiState.update { it.copy(isSleepTimerActive = true) }
    }

    fun cancelSleepTimer() {
       Timber.tag("DEBUG_PLAYER").d( "⏰ cancelSleepTimer called")
        val controller = mediaController
        if (controller == null) {
           Timber.tag("DEBUG_PLAYER").e( "❌ ERROR: MediaController is NULL.")
            return
        }

        val command =
                androidx.media3.session.SessionCommand("STOP_SLEEP_TIMER", android.os.Bundle.EMPTY)
        controller.sendCustomCommand(command, android.os.Bundle.EMPTY)

        uiState.update { it.copy(isSleepTimerActive = false) }
       Timber.tag("DEBUG_PLAYER").d( "🚀 Command STOP_SLEEP_TIMER sent")
       Timber.tag("DEBUG_PLAYER").d( "🚀 Command STOP_SLEEP_TIMER sent")
    }
}
