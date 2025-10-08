package com.example.remusic.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.ui.graphics.vector.ImageVector

// Sealed class untuk merepresentasikan jenis output audio
sealed class AudioOutputDevice(val icon: ImageVector, val label: String) {
    data object PhoneSpeaker : AudioOutputDevice(Icons.Default.Speaker, "Speaker Ponsel")
    data class Bluetooth(val name: String) : AudioOutputDevice(Icons.Default.Headphones, name)
    data object WiredHeadphones : AudioOutputDevice(Icons.Default.Headphones, "Headphone Kabel")
    data object Unknown : AudioOutputDevice(Icons.Default.Speaker, "Perangkat Audio")
}
