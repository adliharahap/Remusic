package com.example.remusic

import com.metrolist.innertube.YouTube
import kotlinx.coroutines.runBlocking
import org.junit.Test
import com.google.gson.Gson

class YouTubeTest {
    @Test
    fun testYouTubeFetch() = runBlocking {
        println("=== MENCARI LAGU: ===")
        val searchResult = YouTube.search("Love me harder", YouTube.SearchFilter.FILTER_SONG).getOrNull()
        
        val gson = Gson()
        
        if (searchResult != null) {
            println("=== JSON SEARCH METADATA ===")
            println(gson.toJson(searchResult))
            // Ambil ID video pertama dari hasil pencarian
            val firstItem = searchResult.items.firstOrNull()
            if (firstItem != null) {
                val videoId = firstItem.id
                println("\n=== MENGAMBIL DATA PLAYER UNTUK VIDEO ID: $videoId ===")
                
                // Coba beberapa client untuk menghindari cipher/signature
                val clientsToTry = listOf(
                    com.metrolist.innertube.models.YouTubeClient.IOS,
                    com.metrolist.innertube.models.YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
                    com.metrolist.innertube.models.YouTubeClient.ANDROID_VR_1_43_32,
                    com.metrolist.innertube.models.YouTubeClient.WEB_REMIX
                )
                
                var streamUrl: String? = null
                var successfulClient: String? = null
                
                for (client in clientsToTry) {
                    val playerResult = YouTube.player(videoId, client = client).getOrNull()
                    if (playerResult != null) {
                        // Cari format audio yang memiliki URL langsung (tidak dienkripsi)
                        val formats = playerResult.streamingData?.adaptiveFormats
                        streamUrl = formats?.find { !it.url.isNullOrEmpty() && it.isAudio }?.url
                        
                        if (streamUrl != null) {
                            successfulClient = client.clientName
                            break
                        }
                    }
                }
                
                if (streamUrl != null) {
                    println("Stream URL berhasil didapatkan dari client: $successfulClient")
                    println("URL: $streamUrl")
                } else {
                    println("Gagal mendapatkan stream URL yang valid dari semua client percobaan (mungkin butuh signature decryption).")
                }

                println("\n=== MENGAMBIL DATA NEXT & LYRICS UNTUK VIDEO ID: $videoId ===")
                val nextResult = YouTube.next(com.metrolist.innertube.models.WatchEndpoint(videoId = videoId)).getOrNull()
                if (nextResult != null) {
                    println("Rekomendasi Lagu Berikutnya (Total): ${nextResult.items.size}")
                    nextResult.items.take(3).forEach { song ->
                        println("- ${song.title} oleh ${song.artists?.joinToString { it.name }}")
                    }

                    // Ambil lirik jika endpoint-nya tersedia
                    val lyricsEndpoint = nextResult.lyricsEndpoint
                    if (lyricsEndpoint != null) {
                        println("\n=== MENGAMBIL LIRIK ===")
                        val lyricsResult = YouTube.lyrics(lyricsEndpoint).getOrNull()
                        if (lyricsResult != null) {
                            println("Lirik berhasil didapatkan (panjang: ${lyricsResult.length} karakter):")
                            println(lyricsResult.take(200) + "...\n(terpotong untuk preview)")
                        } else {
                            println("Gagal mendapatkan teks lirik.")
                        }
                    } else {
                        println("Lagu ini tidak menyediakan endpoint lirik dari YouTube Music.")
                    }
                } else {
                    println("Gagal mendapatkan rekomendasi next.")
                }
            }
        } else {
            println("Gagal mendapatkan search result.")
        }
    }
}
