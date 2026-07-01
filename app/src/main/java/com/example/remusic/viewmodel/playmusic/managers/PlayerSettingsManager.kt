package com.example.remusic.viewmodel.playmusic.managers

import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import com.example.remusic.data.preferences.PlayerBackgroundStyle
import com.example.remusic.data.preferences.UserPreferencesRepository
import com.example.remusic.ui.screen.playmusic.InactiveLyricsColor
import com.example.remusic.viewmodel.playmusic.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class PlayerSettingsManager(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<PlayerUiState>,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    // Reference to the active media controller
    var mediaController: Player? = null

    fun setPlaybackSpeed(speed: Float) {
        uiState.update { it.copy(playbackSpeed = speed) }
        mediaController?.playbackParameters = PlaybackParameters(speed, speed)
    }

    fun setPlayerBackgroundStyle(style: PlayerBackgroundStyle) {
        scope.launch {
            uiState.update { it.copy(playerBackgroundStyle = style) }
            userPreferencesRepository.savePlayerBackgroundStyle(style)

            if (style == PlayerBackgroundStyle.BLURRED_COVER) {
                if (uiState.value.lyricsConfig.inactiveLyricsColor == InactiveLyricsColor.BLACK) {
                    uiState.update { it.copy(isThemeAppliedToLyrics = false) }
                    userPreferencesRepository.saveThemeApplyToLyrics(false)
                }
            }
        }
    }

    fun setThemeAppliedToQueue(enabled: Boolean) {
        scope.launch {
            uiState.update { it.copy(isThemeAppliedToQueue = enabled) }
            userPreferencesRepository.saveThemeApplyToQueue(enabled)
        }
    }

    fun setThemeAppliedToNowPlaying(enabled: Boolean) {
        scope.launch {
            uiState.update { it.copy(isThemeAppliedToNowPlaying = enabled) }
            userPreferencesRepository.saveThemeApplyToNowPlaying(enabled)
        }
    }

    fun setThemeAppliedToLyrics(enabled: Boolean) {
        scope.launch {
            uiState.update { it.copy(isThemeAppliedToLyrics = enabled) }
            userPreferencesRepository.saveThemeApplyToLyrics(enabled)
        }
    }

    // --- GAME MODE ---
    fun getTargetVolume(): Float {
        return if (uiState.value.isGameModeActive) uiState.value.gameModeVolume else 1.0f
    }

    fun toggleGameMode() {
        val newState = !uiState.value.isGameModeActive
        uiState.update { it.copy(isGameModeActive = newState) }
        
        mediaController?.volume = getTargetVolume()
        
        if (newState) {
            Timber.tag("DEBUG_PLAYER").d("🎮 Game Mode ON: Volume diturunkan ke ${uiState.value.gameModeVolume}")
        } else {
            Timber.tag("DEBUG_PLAYER").d("🎮 Game Mode OFF: Volume kembali normal (1.0f)")
        }
    }

    fun setGameModeVolume(volume: Float) {
        uiState.update { it.copy(gameModeVolume = volume) }
        if (uiState.value.isGameModeActive) {
            mediaController?.volume = volume
            Timber.tag("DEBUG_PLAYER").d("🎮 Game Mode Volume diubah ke $volume")
        }
    }
}
