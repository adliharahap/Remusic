package com.example.remusic.data.network
import com.example.remusic.BuildConfig

object TelegramRetrofit {
    // GANTI DENGAN TOKEN BOT KAMU
    private const val BOT_TOKEN = BuildConfig.TELEGRAM_TOKEN_BOT
    
    private const val BASE_URL = "https://api.telegram.org/bot$BOT_TOKEN/"

    val api: TelegramApi by lazy {
        retrofit2.Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(TelegramApi::class.java)
    }

    // Fungsi helper untuk bikin URL Stream Final
    fun getFinalDownloadUrl(filePath: String): String {
        return "https://api.telegram.org/file/bot$BOT_TOKEN/$filePath"
    }
}
