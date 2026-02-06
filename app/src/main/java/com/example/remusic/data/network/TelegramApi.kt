package com.example.remusic.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface TelegramApi {
    // Endpoint bawaan Telegram untuk minta info file
    @GET("getFile")
    suspend fun getFile(
        @Query("file_id") fileId: String
    ): Response<TelegramFileResponse>
}
