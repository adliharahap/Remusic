package com.example.remusic.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.ui.graphics.vector.ImageVector

// Sealed class untuk merepresentasikan jenis output audio
sealed class AudioOutputDevice(val icon: ImageVector, val label: String, val id: Int) {
    data class PhoneSpeaker(val deviceId: Int = 0) : AudioOutputDevice(Icons.Default.Speaker, "Speaker Ponsel", deviceId)
    data class Bluetooth(val name: String, val deviceId: Int = 0) : AudioOutputDevice(Icons.Default.Headphones, name, deviceId)
    data class WiredHeadphones(val deviceId: Int = 0) : AudioOutputDevice(Icons.Default.Headphones, "Headphone Kabel", deviceId)
    data class Unknown(val deviceId: Int = 0) : AudioOutputDevice(Icons.Default.Speaker, "Perangkat Audio", deviceId)
}
