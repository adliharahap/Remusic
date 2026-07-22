package com.metrolist.innertube

import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.metrolist.innertube.pages.HomePage
import com.google.gson.GsonBuilder

class FetchHomeMultipleTest {
    @Test
    fun testFetchHomeMultiple() = runBlocking {
        println("Fetching YouTube Home (First Page)...")
        var result = YouTube.home().getOrNull()
        if (result != null) {
            val sections = mutableListOf<com.metrolist.innertube.models.Section>()
            sections.addAll(result.sections)
            var continuation = result.continuation
            
            for (i in 1..2) {
                if (continuation != null) {
                    println("Fetching YouTube Home (Continuation $i)...")
                    val nextResult = YouTube.home(continuation = continuation).getOrNull()
                    if (nextResult != null) {
                        sections.addAll(nextResult.sections)
                        continuation = nextResult.continuation
                    } else {
                        break
                    }
                }
            }
            
            val mergedResult = result.copy(sections = sections)
            val gson = GsonBuilder().setPrettyPrinting().create()
            val jsonString = gson.toJson(mergedResult)
            File("home_data_multiple.json").writeText(jsonString)
            println("Successfully wrote data to home_data_multiple.json. Found ${mergedResult.sections.size} sections.")
        } else {
            println("YouTube.home() returned null or Error.")
        }
    }
}
