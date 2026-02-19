package com.example.remusic.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.remusic.data.model.User
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object UserManager {

    private val TAG = "RemusicAuth"
    private val json = Json { ignoreUnknownKeys = true }

    private const val PREFS_NAME = "remusic_user_cache"
    private const val KEY_USER = "cached_user"

    private var prefs: SharedPreferences? = null

    /** Harus dipanggil sekali saat app start (dari Application atau MainActivity) */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Load cached user ke memory saat inisialisasi
        loadFromCache()
    }

    var currentUser by mutableStateOf<User?>(null)
        private set

    // ──────────────────────────────────────────────────
    // Cache operations (SharedPreferences)
    // ──────────────────────────────────────────────────

    private fun loadFromCache() {
        val raw = prefs?.getString(KEY_USER, null) ?: return
        try {
            currentUser = json.decodeFromString<User>(raw)
            Log.d(TAG, "📦 [UserManager] User dimuat dari cache: ${currentUser?.displayName}")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ [UserManager] Gagal parse cache user: ${e.message}")
            prefs?.edit()?.remove(KEY_USER)?.apply()
        }
    }

    private fun saveToCache(user: User) {
        try {
            val raw = json.encodeToString(user)
            prefs?.edit()?.putString(KEY_USER, raw)?.apply()
            Log.d(TAG, "💾 [UserManager] User disimpan ke cache: ${user.displayName}")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ [UserManager] Gagal simpan cache user: ${e.message}")
        }
    }

    private fun clearCache() {
        prefs?.edit()?.remove(KEY_USER)?.apply()
        Log.d(TAG, "🗑️ [UserManager] Cache user dibersihkan.")
    }

    // ──────────────────────────────────────────────────
    // Network operations
    // ──────────────────────────────────────────────────

    /**
     * Fetch user dari Supabase dengan retry logic.
     * Jika berhasil, simpan ke cache lokal.
     */
    suspend fun fetchCurrentUser(uid: String) {
        Log.d(TAG, "================================================================")
        Log.d(TAG, "🔍 [UserManager] [USER FETCH] Request data user untuk ID: $uid")

        var attempt = 1
        val maxAttempts = 4

        while (attempt <= maxAttempts) {
            try {
                if (attempt > 1) Log.d(TAG, "🔄 [UserManager] RETRY USER: Percobaan ke-$attempt...")

                val user = SupabaseManager.client
                    .from("users")
                    .select(columns = Columns.ALL) {
                        filter { eq("id", uid) }
                    }
                    .decodeSingle<User>()

                // SUKSES — update memory + cache
                currentUser = user
                saveToCache(user)
                Log.d(TAG, "✅ [UserManager] [SUCCESS] Profil dimuat: ${user.displayName}")
                Log.d(TAG, "================================================================")
                return

            } catch (e: Exception) {
                Log.e(TAG, "❌ [UserManager] [USER ERROR] Percobaan $attempt Gagal: ${e.message}")
                if (attempt >= maxAttempts) {
                    Log.e(TAG, "💀 [UserManager] [GIVE UP] Gagal total setelah $maxAttempts percobaan.")
                    throw e // throw biar caller bisa handle
                }
            }

            if (attempt < maxAttempts) {
                Log.d(TAG, "⏳ [UserManager] Menunggu 1 detik sebelum coba lagi...")
                delay(1000)
            }
            attempt++
        }
    }

    /**
     * Pastikan user data tersedia.
     * Cek cache dulu → jika ada, langsung return (offline-safe).
     * Jika tidak ada, fetch dari network.
     */
    suspend fun ensureUserLoaded(): User? {
        // Cache hit → langsung return, tidak perlu network
        if (currentUser != null) {
            Log.d(TAG, "✅ [UserManager] [CACHE HIT] User sudah ada: ${currentUser?.displayName}")
            return currentUser
        }

        // Coba fetch dari network
        val session = SupabaseManager.client.auth.currentSessionOrNull()
        if (session != null) {
            Log.d(TAG, "🔄 [UserManager] [ENSURE] User null, fetching dari session...")
            try {
                fetchCurrentUser(session.user?.id ?: return null)
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ [UserManager] [ENSURE] Gagal fetch (mungkin offline): ${e.message}")
                // Tidak throw — return null agar caller bisa handle gracefully
            }
        } else {
            Log.w(TAG, "⚠️ [UserManager] [ENSURE FAIL] Tidak ada session dan user null.")
        }

        return currentUser
    }

    fun clearUser() {
        currentUser = null
        clearCache()
        Log.d(TAG, "🗑️ [UserManager] Data user dibersihkan (logout).")
    }
}