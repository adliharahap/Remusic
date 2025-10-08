package com.example.remusic.data.model

import androidx.compose.runtime.Immutable
import java.util.regex.Pattern

@Immutable
data class LyricLine(
    val timestamp: Long,
    val originalText: String,
    val translatedText: String? // Nullable untuk lirik tanpa terjemahan
)

/**
 * Fungsi untuk mem-parsing string berformat .lrc menjadi List<LyricLine>.
 * Mendukung format lirik tunggal dan lirik ganda (dengan terjemahan).
 * Format terjemahan: [mm:ss.xx]Lirik Asli|Terjemahan
 *
 * @param lrcString String mentah dari file .lrc.
 * @return Daftar LyricLine yang sudah diurutkan berdasarkan timestamp.
 */
fun parseLrc(lrcString: String?): List<LyricLine> {
    if (lrcString.isNullOrBlank()) return emptyList()

    val lyricLines = mutableListOf<LyricLine>()

    // --- METODE 1: PARSING PER BARIS (untuk format standar) ---
    val lines = lrcString.lines()
    if (lines.size > 1) {
        val lineRegex = Pattern.compile("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")
        lines.forEach { line ->
            val matcher = lineRegex.matcher(line.trim())
            if (matcher.matches()) {
                try {
                    val fullText = matcher.group(4)?.trim() ?: ""
                    if (isMetadata(fullText, line.trim())) return@forEach

                    val timestamp = parseTimestamp(matcher.group(1), matcher.group(2), matcher.group(3))
                    addLyricLine(lyricLines, timestamp, fullText)
                } catch (e: Exception) { /* ignore malformed line */ }
            }
        }
    }

    // --- METODE 2: FALLBACK DENGAN REGEX GLOBAL (untuk format satu baris/aneh) ---
    if (lyricLines.isEmpty()) {
        val globalRegex = Pattern.compile("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*?)(?=\[\d{2}:\d{2}\.\d{2,3}\]|$)""")
        val matcher = globalRegex.matcher(lrcString)
        while (matcher.find()) {
            try {
                val fullText = matcher.group(4)?.trim() ?: ""
                if (isMetadata(fullText, matcher.group(0))) continue

                val timestamp = parseTimestamp(matcher.group(1), matcher.group(2), matcher.group(3))
                addLyricLine(lyricLines, timestamp, fullText)
            } catch (e: Exception) { /* ignore malformed match */ }
        }
    }

    // Menambahkan baris tanpa timestamp (jika ada)
    if (lyricLines.isEmpty() && !lrcString.contains("[")) {
        lyricLines.add(LyricLine(-1, lrcString.trim(), null))
    }

    return lyricLines.sortedBy { if (it.timestamp == -1L) Long.MAX_VALUE else it.timestamp }
}

/**
 * Fungsi bantuan untuk memproses teks lirik dan menambahkannya ke daftar.
 * Di sinilah logika pemisahan terjemahan berada.
 */
private fun addLyricLine(list: MutableList<LyricLine>, timestamp: Long, fullText: String) {
    if (fullText.isEmpty()) return

    if (fullText.contains('|')) {
        val parts = fullText.split('|', limit = 2)
        val original = parts[0].trim()
        val translation = parts.getOrNull(1)?.trim()
        list.add(LyricLine(timestamp, original, translation))
    } else {
        list.add(LyricLine(timestamp, fullText, null))
    }
}

/**
 * Fungsi bantuan untuk menghitung timestamp dari grup regex.
 */
private fun parseTimestamp(minutesStr: String?, secondsStr: String?, millisStr: String?): Long {
    val minutes = minutesStr?.toLongOrNull() ?: 0L
    val seconds = secondsStr?.toLongOrNull() ?: 0L
    var millis = millisStr?.toLongOrNull() ?: 0L
    if (millisStr?.length == 2) {
        millis *= 10
    }
    return (minutes * 60 + seconds) * 1000 + millis
}

/**
 * Helper function untuk mengecek apakah baris adalah metadata.
 */
private fun isMetadata(text: String, fullLine: String): Boolean {
    // Cek konten umum metadata
    val contentMeta = text.contains("ti:") || text.contains("ar:") || text.contains("al:") || text.contains("by:") || text.contains("length:")
    // Cek format umum metadata [key:value]
    val formatMeta = fullLine.trim().let { it.startsWith("[") && it.endsWith("]") && it.contains(":") }

    // Jika tidak ada timestamp (Metode 1), maka cek formatnya
    if (!fullLine.matches(Regex("""^\[\d{2}:\d{2}\.\d{2,3}\].*"""))) {
        return formatMeta
    }

    // Jika ada timestamp, cek kontennya
    return contentMeta
}