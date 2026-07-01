package com.example.remusic.data

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Global handler untuk sesi yang expired.
 * Ketika token sudah tidak bisa di-refresh lagi (refresh_token expired/revoked),
 * semua layar yang observe [sessionExpiredEvent] akan melakukan forced logout.
 *
 * Cara pakai:
 * - Emit: SessionExpiredHandler.notifySessionExpired()
 * - Observe: di MainActivity/NavGraph, collect [sessionExpiredEvent] dan navigate ke login
 */
object SessionExpiredHandler {

    private val _sessionExpiredEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpiredEvent: SharedFlow<Unit> = _sessionExpiredEvent.asSharedFlow()

    @Volatile
    private var hasNotified = false // Cegah emit berkali-kali dalam satu siklus

    fun notifySessionExpired() {
        if (!hasNotified) {
            hasNotified = true
            Log.w("SessionExpired", "🔐 Session expired! Meminta forced logout...")
            _sessionExpiredEvent.tryEmit(Unit)
        }
    }

    /** Reset state ini setelah user berhasil login ulang */
    fun reset() {
        hasNotified = false
    }
}
