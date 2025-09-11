package com.example.remusic.data.model

import androidx.compose.runtime.Immutable
import java.util.regex.Pattern

@Immutable
data class LyricLine(val timestamp: Long, val text: String)

/**
 * Fungsi untuk mem-parsing string berformat .lrc menjadi List<LyricLine>.
 * Parser yang robust - bekerja untuk string single-line maupun multi-line.
 * Support juga untuk baris tanpa timestamp (credits, judul, dll).
 *
 * @param lrcString String mentah dari file .lrc.
 * @return Daftar LyricLine yang sudah diurutkan berdasarkan timestamp.
 */
fun parseLrc(lrcString: String): List<LyricLine> {
    val lyricLines = mutableListOf<LyricLine>()

    println("Parsing LRC string length: ${lrcString.length}")
    println("Contains newlines: ${lrcString.contains('\n')}")

    // Method 1: Coba parsing per baris dulu (untuk format standard multi-line)
    val lines = lrcString.lines()
    if (lines.size > 1) {
        println("Multi-line format detected, parsing ${lines.size} lines")

        val lineRegex = Pattern.compile("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")

        lines.forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) return@forEach

            val matcher = lineRegex.matcher(trimmedLine)
            if (matcher.matches()) {
                // Baris dengan timestamp
                try {
                    val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                    val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                    var millis = matcher.group(3)?.toLongOrNull() ?: 0L
                    val text = matcher.group(4)?.trim() ?: ""

                    // Skip metadata
                    if (isMetadata(text)) {
                        println("Skipping metadata: $trimmedLine")
                        return@forEach
                    }

                    // Convert 2-digit millis to 3-digit
                    if (matcher.group(3)?.length == 2) {
                        millis *= 10
                    }

                    val timestamp = (minutes * 60 + seconds) * 1000 + millis

                    if (text.isNotEmpty()) {
                        lyricLines.add(LyricLine(timestamp, text))
                        println("Parsed (line): [${formatTimestamp(timestamp)}] '$text'")
                    }

                } catch (e: Exception) {
                    println("Error parsing line: $trimmedLine - ${e.message}")
                }
            } else if (!trimmedLine.startsWith("[") || (!isMetadata(trimmedLine) && !trimmedLine.matches(Regex("""\[.*?\]""")))) {
                // Baris tanpa timestamp (credits, judul, dll) - timestamp = -1
                lyricLines.add(LyricLine(-1L, trimmedLine))
                println("Parsed (no timestamp): '$trimmedLine'")
            }
        }
    }

    // Method 2: Jika multi-line parsing gagal atau ini single-line, gunakan regex global
    if (lyricLines.isEmpty()) {
        println("Single-line format detected or multi-line parsing failed, using global regex")

        val globalPattern = Pattern.compile("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*?)(?=\[\d{2}:\d{2}\.\d{2,3}\]|$)""")
        val matcher = globalPattern.matcher(lrcString)

        // Untuk single-line, cari juga teks di awal yang tidak punya timestamp
        var lastEnd = 0
        val nonTimestampedParts = mutableListOf<String>()

        while (matcher.find()) {
            // Tangkap teks sebelum timestamp ini (jika ada)
            if (matcher.start() > lastEnd) {
                val beforeText = lrcString.substring(lastEnd, matcher.start()).trim()
                if (beforeText.isNotEmpty() && !isMetadata(beforeText) && !beforeText.matches(Regex("""\[.*?\]"""))) {
                    nonTimestampedParts.add(beforeText)
                }
            }

            try {
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                var millis = matcher.group(3)?.toLongOrNull() ?: 0L
                val text = matcher.group(4)?.trim() ?: ""

                // Skip metadata
                if (isMetadata(text)) {
                    println("Skipping metadata: ${matcher.group()}")
                    lastEnd = matcher.end()
                    continue
                }

                // Convert 2-digit millis to 3-digit
                if (matcher.group(3)?.length == 2) {
                    millis *= 10
                }

                val timestamp = (minutes * 60 + seconds) * 1000 + millis

                if (text.isNotEmpty()) {
                    lyricLines.add(LyricLine(timestamp, text))
                    println("Parsed (global): [${formatTimestamp(timestamp)}] '$text'")
                }

                lastEnd = matcher.end()

            } catch (e: Exception) {
                println("Error parsing match: ${matcher.group()} - ${e.message}")
            }
        }

        // Tangkap teks setelah timestamp terakhir (jika ada)
        if (lastEnd < lrcString.length) {
            val afterText = lrcString.substring(lastEnd).trim()
            if (afterText.isNotEmpty() && !isMetadata(afterText) && !afterText.matches(Regex("""\[.*?\]"""))) {
                nonTimestampedParts.add(afterText)
            }
        }

        // Tambahkan baris tanpa timestamp
        nonTimestampedParts.forEach { text ->
            lyricLines.add(LyricLine(-1L, text))
            println("Parsed (no timestamp): '$text'")
        }
    }

    // Sort: timestamp -1 (no timestamp) di akhir, yang lain diurutkan berdasarkan timestamp
    val sortedLyrics = lyricLines.sortedWith(compareBy<LyricLine> {
        if (it.timestamp == -1L) Long.MAX_VALUE else it.timestamp
    }.thenBy { it.text })

    println("Total parsed lyrics: ${sortedLyrics.size}")
    println("- With timestamp: ${sortedLyrics.count { it.timestamp != -1L }}")
    println("- Without timestamp: ${sortedLyrics.count { it.timestamp == -1L }}")

    // Debug: print first few lyrics
    sortedLyrics.take(3).forEach { lyric ->
        if (lyric.timestamp == -1L) {
            println("Final lyric: [NO TIME] '${lyric.text}'")
        } else {
            println("Final lyric: [${formatTimestamp(lyric.timestamp)}] '${lyric.text}'")
        }
    }

    return sortedLyrics
}

/**
 * Helper function to check if text contains metadata
 */
private fun isMetadata(text: String): Boolean {
    return text.contains("id:") || text.contains("ti:") ||
            text.contains("ar:") || text.contains("al:") ||
            text.contains("by:") || text.contains("offset:")
}

/**
 * Helper function to format timestamp for debugging
 */
private fun formatTimestamp(timestamp: Long): String {
    val minutes = timestamp / 60000
    val seconds = (timestamp % 60000) / 1000
    val ms = timestamp % 1000
    return String.format("%02d:%02d.%03d", minutes, seconds, ms)
}