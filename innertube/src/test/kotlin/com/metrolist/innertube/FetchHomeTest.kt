package com.metrolist.innertube

import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.metrolist.innertube.pages.HomePage

class FetchHomeTest {
    @Test
    fun testFetchHome() = runBlocking {
        println("Fetching YouTube Home...")
        val result = YouTube.home().getOrNull()
        if (result != null) {
            val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
            val jsonString = gson.toJson(result)
            File("home_data.json").writeText(jsonString)
            println("Successfully wrote data to home_data.json. Found ${result.sections.size} sections.")
        } else {
            println("YouTube.home() returned null or Error.")
        }
    }
}
