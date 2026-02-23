package com.example.remusic.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object DeezerRetrofit {
    private const val BASE_URL = "https://api.deezer.com/"

    val api: DeezerApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DeezerApi::class.java)
    }
}
