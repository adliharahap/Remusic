package com.example.remusic.utils

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import timber.log.Timber

object LyricsTranslator {

    // Regex untuk memisahkan timestamp dan lirik: [00:15.22] Hello
    private val lrcRegex = Regex("""(\[\d{2}:\d{2}\.\d{2,3}])\s*(.*)""")

    suspend fun translateLrc(lrcText: String, onProgress: (String) -> Unit): String? {
        return try {
            val pureText = extractPureText(lrcText)
            if (pureText.isBlank()) return lrcText

            val languageIdentifier = LanguageIdentification.getClient()
            var sourceLang = languageIdentifier.identifyLanguage(pureText).await()
            Timber.tag("LyricsTranslator").d("Detected language = $sourceLang")

            // Override for mixed language songs
            if (Regex("[\\uAC00-\\uD7A3]").containsMatchIn(pureText)) {
                sourceLang = TranslateLanguage.KOREAN
                Timber.tag("LyricsTranslator").d("Hangul detected, overriding to = $sourceLang")
            } else if (Regex("[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FAF]").containsMatchIn(pureText)) {
                sourceLang = TranslateLanguage.JAPANESE
                Timber.tag("LyricsTranslator").d("Japanese detected, overriding to = $sourceLang")
            } else if (Regex("[\\u4E00-\\u9FFF]").containsMatchIn(pureText)) {
                sourceLang = TranslateLanguage.CHINESE
                Timber.tag("LyricsTranslator").d("Chinese detected, overriding to = $sourceLang")
            }

            if (sourceLang == "und" || sourceLang == TranslateLanguage.INDONESIAN || sourceLang == "id" || sourceLang == "in") {
                return lrcText + "\n[tool:MLKit]"
            }

            sourceLang = TranslateLanguage.fromLanguageTag(sourceLang) ?: sourceLang

            val lines = lrcText.lines()
            val translatedLines = mutableListOf<String>()

            if (sourceLang == TranslateLanguage.ENGLISH) {
                onProgress("Mengunduh model (English -> Indo)...")
                val enIdTranslator = getTranslator(TranslateLanguage.ENGLISH, TranslateLanguage.INDONESIAN)
                enIdTranslator.downloadModelIfNeeded().await()
                
                onProgress("Menerjemahkan lirik...")
                for (line in lines) {
                    translatedLines.add(processLine(line, enIdTranslator))
                }
                enIdTranslator.close()
            } else {
                onProgress("Mengunduh model ($sourceLang -> English)...")
                val srcEnTranslator = getTranslator(sourceLang, TranslateLanguage.ENGLISH)
                srcEnTranslator.downloadModelIfNeeded().await()

                onProgress("Mengunduh model (English -> Indo)...")
                val enIdTranslator = getTranslator(TranslateLanguage.ENGLISH, TranslateLanguage.INDONESIAN)
                enIdTranslator.downloadModelIfNeeded().await()

                onProgress("Menerjemahkan lirik (Bridge)...")
                for (line in lines) {
                    val matchResult = lrcRegex.find(line)
                    if (matchResult != null) {
                        val timestamp = matchResult.groupValues[1]
                        val originalText = matchResult.groupValues[2].trim()
                        if (originalText.isNotEmpty() && !originalText.startsWith("[")) {
                            val englishText = srcEnTranslator.translate(originalText).await()
                            val indoText = enIdTranslator.translate(englishText).await()
                            translatedLines.add("$timestamp $originalText | ${postProcessTranslation(indoText)}")
                        } else {
                            translatedLines.add(line)
                        }
                    } else {
                        // Handle plain text lyrics (no timestamps)
                        if (line.isNotBlank() && !line.startsWith("[")) {
                            val englishText = srcEnTranslator.translate(line.trim()).await()
                            val indoText = enIdTranslator.translate(englishText).await()
                            translatedLines.add("${line.trim()} | ${postProcessTranslation(indoText)}")
                        } else {
                            translatedLines.add(line)
                        }
                    }
                }
                srcEnTranslator.close()
                enIdTranslator.close()
            }

            val finalTranslated = translatedLines.joinToString("\n") + "\n[tool:MLKit]"
            Timber.tag("LyricsTranslator").d("=== HASIL TRANSLATE LYRICS ===\n$finalTranslated\n==============================")
            finalTranslated
        } catch (e: Exception) {
            Timber.tag("LyricsTranslator").e(e, "Error translating lyrics")
            null
        }
    }

    private suspend fun processLine(line: String, translator: Translator): String {
        val matchResult = lrcRegex.find(line)
        if (matchResult != null) {
            val timestamp = matchResult.groupValues[1]
            val originalText = matchResult.groupValues[2].trim()
            if (originalText.isNotEmpty() && !originalText.startsWith("[")) {
                val translated = translator.translate(originalText).await()
                return "$timestamp $originalText | ${postProcessTranslation(translated)}"
            }
        } else {
            // Handle plain text lyrics
            if (line.isNotBlank() && !line.startsWith("[")) {
                val translated = translator.translate(line.trim()).await()
                return "${line.trim()} | ${postProcessTranslation(translated)}"
            }
        }
        return line
    }

    private fun postProcessTranslation(text: String): String {
        return text
            .replace(Regex("\\bsaya\\b", RegexOption.IGNORE_CASE)) {
                if (it.value.first().isUpperCase()) "Aku" else "aku"
            }
            .replace(Regex("\\banda\\b", RegexOption.IGNORE_CASE)) {
                if (it.value.first().isUpperCase()) "Kamu" else "kamu"
            }
            .replace(Regex("\\bbayi\\b", RegexOption.IGNORE_CASE)) {
                if (it.value.first().isUpperCase()) "Sayang" else "sayang"
            }
    }

    private fun extractPureText(lrc: String): String {
        val lines = lrc.lines()
        val pureLines = mutableListOf<String>()
        for (line in lines) {
            val matchResult = lrcRegex.find(line)
            if (matchResult != null) {
                val text = matchResult.groupValues[2].trim()
                if (text.isNotEmpty() && !text.startsWith("[")) {
                    pureLines.add(text)
                }
            } else {
                if (line.isNotBlank() && !line.startsWith("[")) {
                    pureLines.add(line.trim())
                }
            }
        }
        return pureLines.joinToString(" ").take(1000)
    }

    private fun getTranslator(source: String, target: String): Translator {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
        return Translation.getClient(options)
    }
}
