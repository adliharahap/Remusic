package com.example.remusic.data

import android.content.Context
import com.example.remusic.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.plugins.HttpTimeout
import io.github.jan.supabase.annotations.SupabaseInternal

object SupabaseManager {

    // Gunakan lateinit karena kita butuh Context dulu baru bisa inisialisasi
    lateinit var client: SupabaseClient

    // Fungsi ini wajib dipanggil sekali saat aplikasi pertama kali jalan
    @OptIn(SupabaseInternal::class)
    fun initialize(context: Context) {
        client = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                // INI KUNCINYA: Pasang Session Manager yang tadi kita buat
                sessionManager = AndroidSessionManager(context)
            }
            install(Postgrest)
            install(Storage)
            httpConfig {
                install(HttpTimeout) {
                    requestTimeoutMillis = 25000 // 25 Detik
                    connectTimeoutMillis = 25000 // 25 Detik
                    socketTimeoutMillis = 25000  // 25 Detik
                }
            }
        }
    }
}