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
    var isCrossfading = false
        private set

    private var fadingOutPlayerRef: ExoPlayer? = null
    private var fadingInPlayerRef: ExoPlayer? = null
    private var currentTargetVolume: Float = 1f
    private var isHandoffCompleted = false

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
        val fadingOutPlayer = activePlayer
        val fadingInPlayer = if (activePlayer == playerA) playerB else playerA

        // Pertahankan volume pemutaran saat ini (misal: Game Mode volume) agar tidak reset ke 100%
        val targetVolume = fadingOutPlayer.volume.coerceIn(0f, 1f)
        val activeStartVol = targetVolume

        isCrossfading = true
        isHandoffCompleted = false
        currentTargetVolume = targetVolume
        fadingOutPlayerRef = fadingOutPlayer
        fadingInPlayerRef = fadingInPlayer

        Timber.tag("ServiceCrossfadeManager").d("🔀 Memulai A/B Crossfade ke index $nextIndex | Durasi: ${fadeDurationMs}ms | Kurva: ${curve.name} | Volume: $targetVolume")

        // 1. Copy the playlist to the inactive player
        val mediaItems = mutableListOf<MediaItem>()
        for (i in 0 until activePlayer.mediaItemCount) {
            mediaItems.add(activePlayer.getMediaItemAt(i))
        }
        
        fadingInPlayer.setMediaItems(mediaItems, nextIndex, 0L)
        // Salin preferensi repeat mode agar UI tidak kaget dan mereset saat media session berganti
        fadingInPlayer.repeatMode = activePlayer.repeatMode
        fadingInPlayer.shuffleModeEnabled = activePlayer.shuffleModeEnabled
        fadingInPlayer.volume = 0f
        // MATIKAN AUDIO FOCUS SEMENTARA UNTUK INACTIVE PLAYER
        // Agar ketika di-play, dia nggak merebut focus dari activePlayer (yang bikin activePlayer pause)
        fadingInPlayer.setAudioAttributes(fadingInPlayer.audioAttributes, false)
        
        fadingInPlayer.prepare()
        fadingInPlayer.play()

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
                        isHandoffCompleted = true
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
                activePlayer.volume = targetVolume

                Timber.tag("ServiceCrossfadeManager").d("✅ Crossfade A/B Selesai! Target volume: $targetVolume")
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Timber.tag("ServiceCrossfadeManager").e(e, "❌ Error saat crossfade A/B")
                }
            } finally {
                isCrossfading = false
                fadingOutPlayerRef = null
                fadingInPlayerRef = null
            }
        }
    }
    
    fun cancelCrossfade() {
        if (!isCrossfading) return
        crossfadeJob?.cancel()
        crossfadeJob = null

        val fadingOut = fadingOutPlayerRef
        val fadingIn = fadingInPlayerRef
        val targetVol = currentTargetVolume

        if (isHandoffCompleted) {
            // Handoff sudah terjadi: activePlayer adalah fadingIn
            activePlayer.volume = targetVol
            fadingOut?.setAudioAttributes(fadingOut.audioAttributes, false)
            fadingOut?.pause()
            fadingOut?.clearMediaItems()
            fadingOut?.volume = targetVol
        } else {
            // Handoff belum terjadi: activePlayer masih fadingOut
            activePlayer.volume = targetVol
            fadingIn?.setAudioAttributes(fadingIn.audioAttributes, false)
            fadingIn?.pause()
            fadingIn?.clearMediaItems()
            fadingIn?.volume = targetVol
        }

        isCrossfading = false
        fadingOutPlayerRef = null
        fadingInPlayerRef = null
    }
}
