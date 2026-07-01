package com.example.remusic

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.net.URLDecoder

class RadioTest2 {
    @Test
    fun testRadio() = runBlocking {
        var endpoint = WatchEndpoint(videoId = "TfdFUMxRzqU", playlistId = "RDAMVMTfdFUMxRzqU")
        var continuation = "CDISNRILSUR5UU8wUXV2S1kiEVJEQU1WTVRmZEZVTXhSenFVODH6ARBENjI1QUI0MDI5NEQzODFEGAo%3D"
        
        println("=== FETCH 2 (from log) ===")
        var res = YouTube.next(endpoint, URLDecoder.decode(continuation, "UTF-8")).getOrNull()
        if (res == null) { println("Null"); return@runBlocking }
        println("Items: " + res.items.size)
        println("Next cont: " + res.continuation)
        
        endpoint = res.endpoint
        continuation = res.continuation ?: return@runBlocking
        
        println("=== FETCH 3 ===")
        res = YouTube.next(endpoint, continuation).getOrNull()
        if (res == null) { println("Null"); return@runBlocking }
        println("Items: " + res.items.size)
        println("Next cont: " + res.continuation)
        
        endpoint = res.endpoint
        continuation = res.continuation ?: return@runBlocking
        
        println("=== FETCH 4 ===")
        res = YouTube.next(endpoint, continuation).getOrNull()
        if (res == null) { println("Null"); return@runBlocking }
        println("Items: " + res.items.size)
    }
}
