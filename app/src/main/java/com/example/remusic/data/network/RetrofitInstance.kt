package com.example.remusic.data.network

import com.example.remusic.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor // Opsional: Buat liat log di Logcat
import java.util.concurrent.TimeUnit

// 1. Interface API (UPDATED)
interface RemusicApi {
    @GET("get-stream-url")
    suspend fun getStreamUrl(
        @Query("song_id") songId: String,  // ✅ BARU: Wajib ada buat cache
        @Query("file_id") fileId: String
    ): StreamResponse
}

// 2. Model Response (UPDATED)
data class StreamResponse(
    val success: Boolean,
    val url: String?,       // Link lagu (Telegram/Cache)
    val source: String?,    // Info: "DATABASE" atau "API" (Buat debug aja)
    val expires_at: String?, // Kapan link ini basi
    val error: String?
)

// 3. Object Singleton (UPDATED WITH INTERCEPTOR)
object RetrofitInstance {
    private const val BASE_URL = "https://remusic-admin.vercel.app/api/"

    // ⚠️ PASTE PASSWORD PANJANG KAMU DISINI
    private val APP_SECRET = BuildConfig.APP_SECRET
    private val client = OkHttpClient.Builder()
        // --- A. SECURITY INTERCEPTOR (PENTING!) ---
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("x-remusic-secret", APP_SECRET) // 🔑 Kunci Masuk Server
                .method(original.method, original.body)

            chain.proceed(requestBuilder.build())
        }
        // ------------------------------------------

        // --- B. LOGGING (Biar kelihatan di Logcat kalau error) ---
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })

        // --- C. TIMEOUTS (Settingan Lama Kamu) ---
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS) // Penting buat Vercel
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: RemusicApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RemusicApi::class.java)
    }
}