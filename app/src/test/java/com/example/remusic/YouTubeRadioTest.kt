package com.example.remusic

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

class YouTubeRadioTest {
    @Test
    fun testRadio() = runBlocking {
        val out = StringBuilder()
        out.appendLine("Fetching initial...")
        val endpoint = WatchEndpoint(videoId = "dQw4w9WgXcQ", playlistId = "RDAMVMdQw4w9WgXcQ")
        val res1 = YouTube.next(endpoint).getOrNull()
        if (res1 == null) {
            out.appendLine("res1 is null")
            File("radio_test.txt").writeText(out.toString())
            return@runBlocking
        }
        out.appendLine("res1 items: " + res1.items.size)
        res1.items.take(3).forEach { out.appendLine(" - " + it.title) }
        out.appendLine("continuation: " + res1.continuation)
        
        if (res1.continuation == null) {
            File("radio_test.txt").writeText(out.toString())
            return@runBlocking
        }
        
        out.appendLine("\nFetching continuation...")
        val res2 = YouTube.next(res1.endpoint, res1.continuation).getOrNull()
        if (res2 == null) {
            out.appendLine("res2 is null")
            File("radio_test.txt").writeText(out.toString())
            return@runBlocking
        }
        out.appendLine("res2 items: " + res2.items.size)
        res2.items.take(3).forEach { out.appendLine(" - " + it.title) }
        
        val intersect = res1.items.map { it.id }.intersect(res2.items.map { it.id }.toSet())
        out.appendLine("Intersection size: " + intersect.size)
        File("radio_test.txt").writeText(out.toString())
    }
}
