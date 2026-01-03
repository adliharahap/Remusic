package com.example.remusic.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import okhttp3.OkHttpClient // Import ini
import java.util.concurrent.TimeUnit // Import ini

// 1. Interface API
interface RemusicApi {
    @GET("get-stream-url")
    suspend fun getStreamUrl(
        @Query("file_id") fileId: String
    ): StreamResponse
}

// 2. Model Response
data class StreamResponse(
    val success: Boolean,
    val url: String?,
    val error: String?
)

// 3. Object Singleton
object RetrofitInstance {
    private const val BASE_URL = "https://remusic-admin.vercel.app/api/"

    // --- [SETTING TIMEOUT DISINI] ---
    private val client = OkHttpClient.Builder()
        // Waktu maksimal buat nyambung ke server (Connect)
        .connectTimeout(30, TimeUnit.SECONDS)

        // Waktu maksimal nunggu server ngirim balasan (Read - Penting buat Vercel)
        .readTimeout(30, TimeUnit.SECONDS)

        // Waktu maksimal kita ngirim data ke server (Write)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: RemusicApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client) // <--- JANGAN LUPA PASANG CLIENT NYA DISINI
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RemusicApi::class.java)
    }
}