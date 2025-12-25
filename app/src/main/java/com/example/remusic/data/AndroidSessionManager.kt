package com.example.remusic.data

import android.content.Context
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json
import androidx.core.content.edit

class AndroidSessionManager(context: Context) : SessionManager {

    private val prefs = context.getSharedPreferences("supa_auth_session", Context.MODE_PRIVATE)

    override suspend fun saveSession(session: UserSession) {
        // Simpan sesi sebagai JSON String
        val json = Json.encodeToString(session)
        prefs.edit { putString("session_key", json) }
    }

    override suspend fun loadSession(): UserSession? {
        // Baca sesi dari file
        val json = prefs.getString("session_key", null) ?: return null
        return try {
            Json.decodeFromString<UserSession>(json)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun deleteSession() {
        // Hapus sesi (Logout)
        prefs.edit { remove("session_key") }
    }
}