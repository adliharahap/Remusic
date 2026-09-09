package com.example.remusic.services

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.math.sqrt

/**
 * Handles A/B Player crossfade logic strictly within the MusicService.
 */
class ServiceCrossfadeManager(
    private val playerA: ExoPlayer,
    private val playerB: ExoPlayer,
    private val serviceScope: CoroutineScope
) {
    private var mediaSession: MediaLibrarySession? = null
    var activePlayer: ExoPlayer = playerA
        private set

    private var crossfadeJob: Job? = null
    private var isCrossfading = false

    fun attachSession(session: MediaLibrarySession) {
        this.mediaSession = session
    }

    /**
     * Call this in a loop or periodically from MusicService to monitor position
     */
    fun checkAndTriggerCrossfade(crossfadeDurationMs: Long, curve: com.example.remusic.data.preferences.CrossfadeCurve = com.example.remusic.data.preferences.CrossfadeCurve.EQUAL_POWER) {
        if (isCrossfading || crossfadeDurationMs <= 0) return

        val player = activePlayer
        if (!player.isPlaying) return

        // Jangan crossfade jika mode adalah Repeat One (karena lagu akan mengulang sendiri)
        if (player.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
            return
        }

        val currentPos = player.currentPosition
        val duration = player.duration
        val currentIndex = player.currentMediaItemIndex

        if (duration != C.TIME_UNSET && duration - currentPos <= crossfadeDurationMs) {
            val nextIndex = if (currentIndex + 1 < player.mediaItemCount) {
                currentIndex + 1
            } else if (player.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ALL) {
                0
            } else {
                -1
            }

            if (nextIndex >= 0) {
                // Ensure the next item has a valid URI before starting crossfade
                val nextItem = player.getMediaItemAt(nextIndex)
                val uriStr = nextItem.localConfiguration?.uri?.toString()
                if (uriStr.isNullOrBlank()) {
                    Timber.tag("ServiceCrossfadeManager").w("⚠️ Cannot crossfade: Next item URI is empty/unresolved.")
                    return
                }

                startCrossfade(nextIndex, crossfadeDurationMs, curve)
            }
        }
    }

    private fun startCrossfade(nextIndex: Int, fadeDurationMs: Long, curve: com.example.remusic.data.preferences.CrossfadeCurve) {
        isCrossfading = true
        val inactivePlayer = if (activePlayer == playerA) playerB else playerA

        Timber.tag("ServiceCrossfadeManager").d("🔀 Memulai A/B Crossfade ke index $nextIndex | Durasi: ${fadeDurationMs}ms | Kurva: ${curve.name}")

        // 1. Copy the playlist to the inactive player
        val mediaItems = mutableListOf<MediaItem>()
        for (i in 0 until activePlayer.mediaItemCount) {
            mediaItems.add(activePlayer.getMediaItemAt(i))
        }
        
        inactivePlayer.setMediaItems(mediaItems, nextIndex, 0L)
        // Salin preferensi repeat mode agar UI tidak kaget dan mereset saat media session berganti
        inactivePlayer.repeatMode = activePlayer.repeatMode
        inactivePlayer.shuffleModeEnabled = activePlayer.shuffleModeEnabled
        inactivePlayer.volume = 0f
        // MATIKAN AUDIO FOCUS SEMENTARA UNTUK INACTIVE PLAYER
        // Agar ketika di-play, dia nggak merebut focus dari activePlayer (yang bikin activePlayer pause)
        inactivePlayer.setAudioAttributes(inactivePlayer.audioAttributes, false)
        
        inactivePlayer.prepare()
        inactivePlayer.play()

        val fadingOutPlayer = activePlayer
        val fadingInPlayer = inactivePlayer
        val activeStartVol = fadingOutPlayer.volume.coerceAtLeast(0.1f)
        val targetVolume = 1f // Asumsi volume penuh adalah 1f

        crossfadeJob = serviceScope.launch(Dispatchers.Main) {
            try {
                val stepIntervalMs = 50L
                val steps = (fadeDurationMs / stepIntervalMs).toInt().coerceAtLeast(1)
                val halfwayStep = steps / 2
                var swapped = false

                for (i in 1..steps) {
                    if (!isActive) break
                    
                    val t = i.toFloat() / steps
                    
                    val outVol: Float
                    val inVol: Float
                    
                    when (curve) {
                        com.example.remusic.data.preferences.CrossfadeCurve.LINEAR -> {
                            outVol = activeStartVol * (1f - t)
                            inVol = targetVolume * t
                        }
                        com.example.remusic.data.preferences.CrossfadeCurve.EQUAL_POWER -> {
                            outVol = activeStartVol * kotlin.math.cos(t * kotlin.math.PI / 2).toFloat()
                            inVol = targetVolume * kotlin.math.sin(t * kotlin.math.PI / 2).toFloat()
                        }
                        com.example.remusic.data.preferences.CrossfadeCurve.SMOOTH -> {
                            // Smoothstep curve
                            val smoothT = t * t * (3 - 2 * t)
                            outVol = activeStartVol * (1f - smoothT)
                            inVol = targetVolume * smoothT
                        }
                    }

                    fadingInPlayer.volume = inVol.coerceIn(0f, targetVolume)
                    fadingOutPlayer.volume = outVol.coerceIn(0f, activeStartVol)

                    // 2. Pertengahan Crossfade: SWAP UI / Session Player!
                    if (i >= halfwayStep && !swapped) {
                        swapped = true
                        Timber.tag("ServiceCrossfadeManager").d("🔄 Handoff A/B Player di pertengahan crossfade! UI ganti ke lagu baru.")
                        mediaSession?.player = fadingInPlayer
                        activePlayer = fadingInPlayer
                    }

                    delay(stepIntervalMs)
                }

                // Selesaikan fade out (fadingOutPlayer adalah lagu lama yang sekarang sudah selesai crossfade)
                
                // Matikan audio focus di fadingOutPlayer sebelum di-pause agar tidak abandon focus app
                fadingOutPlayer.setAudioAttributes(fadingOutPlayer.audioAttributes, false)
                fadingOutPlayer.pause()
                fadingOutPlayer.clearMediaItems()
                fadingOutPlayer.volume = targetVolume // Reset volume untuk antrean berikutnya

                // KEMBALIKAN AUDIO FOCUS KE ACTIVE PLAYER YANG BARU
                activePlayer.setAudioAttributes(activePlayer.audioAttributes, true)

                Timber.tag("ServiceCrossfadeManager").d("✅ Crossfade A/B Selesai!")
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Timber.tag("ServiceCrossfadeManager").e(e, "❌ Error saat crossfade A/B")
                }
            } finally {
                isCrossfading = false
            }
        }
    }
    
    fun cancelCrossfade() {
        crossfadeJob?.cancel()
        isCrossfading = false
    }
}
