package com.example.remusic.viewmodel.playmusic.managers

import android.app.Application
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.mediarouter.media.MediaRouter
import com.example.remusic.data.model.AudioOutputDevice
import com.example.remusic.viewmodel.playmusic.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.media3.session.MediaController

class AudioRouteManager(
    private val application: Application,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<PlayerUiState>,
    private val getMediaController: () -> MediaController?,
    private val onFadeInAndPlay: () -> Unit
) {

    private val mediaController: MediaController?
        get() = getMediaController()

    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            updateCurrentAudioDevice()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            updateCurrentAudioDevice()
        }
    }

    private val mediaRouter = MediaRouter.getInstance(application)

    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateCurrentAudioDevice()
        }
        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateCurrentAudioDevice()
        }
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateCurrentAudioDevice()
        }
    }


    private fun updateCurrentAudioDevice() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        
        // Buat daftar perangkat yang tersedia untuk Bottom Sheet
        val availableList = devices.filter { 
            it.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO && it.type != AudioDeviceInfo.TYPE_BLE_HEADSET
        }.mapNotNull { device ->
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                    val name = device.productName?.toString()
                    AudioOutputDevice.Bluetooth(if (name.isNullOrBlank()) "Bluetooth Audio" else name, device.id)
                }
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET -> {
                    AudioOutputDevice.WiredHeadphones(device.id)
                }
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                    AudioOutputDevice.PhoneSpeaker(device.id)
                }
                else -> null
            }
        }.distinctBy { it.id }

        // --- DETEKSI PERANGKAT AKTIF SECARA REAL-TIME ---
        // Kita gunakan MediaRouter untuk tahu apa yang dipilih sistem saat ini
        val selectedRoute = mediaRouter.selectedRoute
        val selectedRouteName = selectedRoute.name
        Timber.tag("DEBUG_PLAYER").d("🎧 [AudioRoute] Current system route: $selectedRouteName")
        
        val currentDevice = devices.firstOrNull { device ->
            // Cocokkan nama untuk Bluetooth (Gunakan trim & ignoreCase untuk keamanan)
            if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                val dName = device.productName?.toString()?.trim() ?: ""
                val rName = selectedRouteName.trim()
                dName.equals(rName, ignoreCase = true) || dName.contains(rName, ignoreCase = true) || rName.contains(dName, ignoreCase = true)
            } else false
        } ?: devices.firstOrNull { device ->
            // Cocokkan tipe dasar untuk Non-Bluetooth
            val rName = selectedRouteName.lowercase()
            when {
                rName.contains("speaker") || rName.contains("ponsel") || rName.contains("phone") -> 
                    device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                rName.contains("headphone") || rName.contains("headset") || rName.contains("wired") || rName.contains("kabel") -> 
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || device.type == AudioDeviceInfo.TYPE_USB_HEADSET
                else -> false
            }
        } ?: devices.firstOrNull { 
            // Fallback: Prioritas Manual (Bluetooth > Wired > Speaker)
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        } ?: devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        } ?: devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER 
        }

        val audioOutput = when (currentDevice?.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET -> {
                val name = currentDevice.productName?.toString()
                AudioOutputDevice.Bluetooth(if (name.isNullOrBlank()) "Bluetooth Audio" else name, currentDevice.id)
            }
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET -> {
                AudioOutputDevice.WiredHeadphones(currentDevice.id)
            }
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                AudioOutputDevice.PhoneSpeaker(currentDevice.id)
            }
            else -> AudioOutputDevice.Unknown(currentDevice?.id ?: 0)
        }

        uiState.update { it.copy(currentAudioDevice = audioOutput, availableAudioDevices = availableList) }
    }

    fun setAudioOutputDevice(device: AudioOutputDevice) {
        val player = mediaController ?: return
        val wasPlaying = player.isPlaying

        scope.launch {
            if (wasPlaying) {
                // 1. Pause secara cepat (tanpa fade out panjang)
                player.pause()
                uiState.update { it.copy(isPlaying = false) }
            }

            // 2. Ganti output audio
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val targetDevice = devices.firstOrNull { it.id == device.id }
            
            Timber.tag("DEBUG_PLAYER").d("🎧 [AudioRoute] Setting device: ${device.label} (ID: ${device.id}), found: ${targetDevice != null}")
            
            targetDevice?.let {
                try {
                    val args = android.os.Bundle().apply {
                        putInt("DEVICE_ID", it.id)
                    }
                    val command = androidx.media3.session.SessionCommand("SET_PREFERRED_AUDIO_DEVICE", android.os.Bundle.EMPTY)
                    player.sendCustomCommand(command, args)

                    uiState.update { state -> state.copy(currentAudioDevice = device) }

                    // 3. Tunggu sebentar agar hardware berpindah stabil
                    delay(600)
                } catch (e: Exception) {
                    Timber.tag("DEBUG_PLAYER").e(e, "Failed to send custom command for audio device")
                }
            }

            if (wasPlaying) {
                // 4. Resume dengan fade in yang smooth (pakai fungsi yang sudah ada)
                onFadeInAndPlay()
            }
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        mediaRouter.addCallback(
            androidx.mediarouter.media.MediaRouteSelector.Builder()
                .addControlCategory(androidx.mediarouter.media.MediaControlIntent.CATEGORY_LIVE_AUDIO)
                .build(),
            mediaRouterCallback,
            MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
        )
        updateCurrentAudioDevice()
    }

    fun cleanup() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        mediaRouter.removeCallback(mediaRouterCallback)
    }
}
