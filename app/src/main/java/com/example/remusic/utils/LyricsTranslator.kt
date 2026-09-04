package com.example.remusic.utils

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object LyricsTranslator {

    private val lrcRegex = Regex("""(\[\d{2}:\d{2}\.\d{2,3}])\s*(.*)""")

    private fun getNetworkStatus(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "Unknown"
        val activeNetwork = cm.activeNetwork ?: return "OFFLINE (Tidak ada koneksi internet)"
        val capabilities = cm.getNetworkCapabilities(activeNetwork)
            ?: return "OFFLINE (Tidak ada koneksi internet)"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR (Data Seluler)"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "ONLINE (Lainnya)"
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun getLanguageDisplayName(code: String): String {
        return when (code.lowercase()) {
            "en" -> "Bahasa Inggris"
            "id", "in" -> "Bahasa Indonesia"
            "ko" -> "Bahasa Korea"
            "ja" -> "Bahasa Jepang"
            "zh" -> "Bahasa Mandarin"
            "es" -> "Bahasa Spanyol"
            "fr" -> "Bahasa Prancis"
            "de" -> "Bahasa Jerman"
            "ar" -> "Bahasa Arab"
            "ru" -> "Bahasa Rusia"
            "it" -> "Bahasa Italia"
            "pt" -> "Bahasa Portugis"
            "th" -> "Bahasa Thailand"
            "vi" -> "Bahasa Vietnam"
            else -> {
                val locale = java.util.Locale.forLanguageTag(code)
                val name = locale.getDisplayLanguage(java.util.Locale("id"))
                if (name.isNotBlank() && !name.equals(code, ignoreCase = true)) "Bahasa $name" else "Bahasa ${code.uppercase()}"
            }
        }
    }

    /**
     * Mengunduh file model secara langsung dari server Google CDN (redirector.gvt1.com / dl.google.com)
     * dan mengekstraknya ke direktori model internal ML Kit.
     * Ini adalah penyelamat jika Android DownloadManager / JobScheduler sistem macet (misal karena thermal restriction di MIUI).
     */
    private fun downloadAndExtractModelDirectly(
        context: Context,
        source: String,
        target: String,
        version: String = "r29",
        languageDisplayName: String = "paket bahasa",
        onProgress: (String) -> Unit
    ): Boolean {
        val langs = arrayOf(source.lowercase(), target.lowercase()).apply { sort() }
        val pair = "${langs[0]}_${langs[1]}"

        val urls = listOf(
            "https://redirector.gvt1.com/edgedl/translate/offline/v5/high/$version/$pair.zip",
            "https://dl.google.com/translate/offline/v5/high/$version/$pair.zip"
        )
        val targetDir = File(context.noBackupFilesDir, "com.google.mlkit.translate.models/$pair")
        targetDir.mkdirs()

        val tempZip = File(context.cacheDir, "${pair}_temp.zip")

        for (urlStr in urls) {
            try {
                Timber.tag("MLKitDebug").d("Mencoba unduh langsung model $pair dari: $urlStr")
                var currentUrl = urlStr
                var connection: java.net.HttpURLConnection
                var redirectCount = 0

                while (true) {
                    val u = java.net.URL(currentUrl)
                    connection = u.openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    connection.instanceFollowRedirects = true

                    val code = connection.responseCode
                    if (code in 300..399 && redirectCount < 5) {
                        val loc = connection.getHeaderField("Location")
                        if (!loc.isNullOrBlank()) {
                            currentUrl = loc
                            redirectCount++
                            connection.disconnect()
                            continue
                        }
                    }
                    break
                }

                if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    Timber.tag("MLKitDebug").w("HTTP response code: ${connection.responseCode} dari $currentUrl")
                    continue
                }

                val contentLength = connection.contentLengthLong.takeIf { it > 0 } ?: (36L * 1024 * 1024)
                var bytesDownloaded = 0L

                tempZip.outputStream().use { fos ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(16384)
                        var read: Int
                        var lastUpdate = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                            bytesDownloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 400) {
                                lastUpdate = now
                                val dlMb = String.format("%.1f", bytesDownloaded / (1024f * 1024f))
                                val totMb = String.format("%.1f", contentLength / (1024f * 1024f))
                                val pct = (bytesDownloaded * 100 / contentLength).toInt().coerceIn(0, 100)
                                onProgress("Mengunduh $languageDisplayName ($dlMb / $totMb MB • $pct%)...")
                            }
                        }
                    }
                }
                connection.disconnect()

                Timber.tag("MLKitDebug").d("Unduhan langsung selesai ($bytesDownloaded bytes). Mengekstrak ke ${targetDir.absolutePath}...")
                onProgress("Memasang paket $languageDisplayName...")

                java.util.zip.ZipInputStream(tempZip.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val relativePath = if (name.contains("/")) {
                            name.substringAfter("/")
                        } else {
                            name
                        }

                        if (relativePath.isNotEmpty()) {
                            val outFile = File(targetDir, relativePath)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { out ->
                                    zis.copyTo(out)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                tempZip.delete()
                Timber.tag("MLKitDebug").d("✅ Model $pair berhasil diunduh dan diekstrak langsung ke ${targetDir.absolutePath}!")
                return true
            } catch (e: Exception) {
                Timber.tag("MLKitDebug").e(e, "Gagal mengunduh langsung dari $urlStr")
                tempZip.delete()
            }
        }
        return false
    }

    /**
     * Membersihkan task unduhan ML Kit yang macet (zombie download id) di SharedPreferences internal ML Kit.
     * Jika task unduhan sebelumnya terputus/gagal, ML Kit menyimpan ID-nya di com.google.mlkit.internal
     * dan menolak memulai unduhan baru ("New model is already in downloading, do nothing").
     */
    private fun cleanStuckDownloads(context: Context, force: Boolean = false) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            val mlKitPrefs = context.getSharedPreferences("com.google.mlkit.internal", Context.MODE_PRIVATE)
            val editor = mlKitPrefs.edit()
            var cleaned = false

            for ((key, value) in mlKitPrefs.all) {
                if (key.startsWith("downloading_model_id_")) {
                    val downloadId = when (value) {
                        is Long -> value
                        is Int -> value.toLong()
                        is Number -> value.toLong()
                        else -> null
                    }
                    if (downloadId != null && dm != null) {
                        try {
                            if (force) {
                                dm.remove(downloadId)
                                editor.remove(key)
                                cleaned = true
                                Timber.tag("MLKitDebug").d("Force-remove stuck download ID $downloadId")
                            } else {
                                val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                                var isRunning = false
                                if (cursor != null && cursor.moveToFirst()) {
                                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                                    if (status == DownloadManager.STATUS_RUNNING) {
                                        isRunning = true
                                    }
                                    cursor.close()
                                }
                                if (!isRunning) {
                                    dm.remove(downloadId)
                                    editor.remove(key)
                                    cleaned = true
                                    Timber.tag("MLKitDebug").d("Menghapus stuck download ID $downloadId dari DownloadManager & MLKit internal prefs")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag("MLKitDebug").w("Gagal mengecek/menghapus download ID $downloadId: ${e.message}")
                            editor.remove(key)
                            cleaned = true
                        }
                    } else {
                        editor.remove(key)
                        cleaned = true
                    }
                } else if (key.startsWith("downloading_model_hash_") || key.startsWith("downloading_begin_time_")) {
                    editor.remove(key)
                    cleaned = true
                }
            }

            // Bersihkan juga download manager prefs jika ada URI tersangkut
            val dmPrefs = context.getSharedPreferences("com.google.mlkit.translate.download_manager", Context.MODE_PRIVATE)
            if (dmPrefs.all.isNotEmpty()) {
                dmPrefs.edit().clear().apply()
                Timber.tag("MLKitDebug").d("Membersihkan cache com.google.mlkit.translate.download_manager")
            }

            if (cleaned) {
                editor.apply()
                Timber.tag("MLKitDebug").d("Cache internal unduhan ML Kit yang macet telah dibersihkan.")
            }
        } catch (e: Exception) {
            Timber.tag("MLKitDebug").w("Error saat cleanStuckDownloads: ${e.message}")
        }
    }

    private fun getActiveDownloadingId(context: Context): Long? {
        val prefs = context.getSharedPreferences("com.google.mlkit.internal", Context.MODE_PRIVATE)
        for ((key, value) in prefs.all) {
            if (key.startsWith("downloading_model_id_")) {
                return when (value) {
                    is Long -> value
                    is Int -> value.toLong()
                    is Number -> value.toLong()
                    else -> null
                }
            }
        }
        return null
    }

    /**
     * Memastikan model terjemahan terunduh.
     * Menggunakan DownloadConditions tanpa requireWifi() agar bisa mengunduh
     * baik lewat Wi-Fi maupun Paket Data Seluler (4G/5G).
     */
    private fun ensureTranslatorReady(
        context: Context,
        source: String,
        target: String,
        onProgress: (String) -> Unit
    ): Translator {
        val sourceName = source.uppercase()
        val targetName = target.uppercase()

        // Diagnostik: Jaringan & Google Play Services
        val netStatus = getNetworkStatus(context)
        val gps = GoogleApiAvailability.getInstance()
        val gpsStatus = gps.isGooglePlayServicesAvailable(context)
        Timber.tag("MLKitDebug").d("Diagnostik HP -> Jaringan: $netStatus | Google Play Services status: $gpsStatus (0=SUCCESS)")

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
        val translator = Translation.getClient(options)

        // Cek apakah model sudah terunduh menggunakan RemoteModelManager
        val modelManager = RemoteModelManager.getInstance()
        val sourceModel = TranslateRemoteModel.Builder(source).build()
        val targetModel = TranslateRemoteModel.Builder(target).build()

        var sourceReady = try { Tasks.await(modelManager.isModelDownloaded(sourceModel), 5, TimeUnit.SECONDS) } catch (e: Exception) { false }
        var targetReady = try { Tasks.await(modelManager.isModelDownloaded(targetModel), 5, TimeUnit.SECONDS) } catch (e: Exception) { false }

        Timber.tag("MLKitDebug").d("Status model lokal -> $sourceName: $sourceReady, $targetName: $targetReady")

        if (sourceReady && targetReady) {
            Timber.tag("MLKitDebug").d("Kedua model sudah siap di penyimpanan perangkat. Langsung terjemahkan.")
            return translator
        }

        // Cek koneksi internet sebelum mencoba mengunduh
        if (!isNetworkAvailable(context)) {
            Timber.tag("MLKitDebug").e("Perangkat offline! Tidak dapat mengunduh model $sourceName / $targetName")
            translator.close()
            throw Exception("Tidak ada koneksi internet. Harap aktifkan Wi-Fi atau Data Seluler untuk mengunduh model terjemahan.")
        }

        // Ada model yang belum terunduh — bersihkan status unduhan macet dulu
        cleanStuckDownloads(context, force = true)

        val missingLangDisplay = if (!sourceReady && !targetReady) {
            "${getLanguageDisplayName(source)} & ${getLanguageDisplayName(target)}"
        } else if (!sourceReady) {
            getLanguageDisplayName(source)
        } else {
            getLanguageDisplayName(target)
        }

        val missingLangCode = if (!sourceReady && !targetReady) "$sourceName & $targetName"
            else if (!sourceReady) sourceName else targetName

        onProgress("Menyiapkan $missingLangDisplay...")
        NotificationUtils.showModelDownloadNotification(context, missingLangDisplay)
        Timber.tag("MLKitDebug").d("Mulai mengunduh model: $missingLangCode di jaringan: $netStatus")

        val conditions = DownloadConditions.Builder()
            .build()

        try {
            Timber.tag("MLKitDebug").d("Memanggil downloadModelIfNeeded untuk $missingLangCode (WiFi & Data Seluler diizinkan)...")
            val downloadTask = translator.downloadModelIfNeeded(conditions)

            downloadTask.addOnSuccessListener {
                Timber.tag("MLKitDebug").d("✅ Callback SUCCESS: downloadModelIfNeeded sukses untuk $missingLangCode!")
            }.addOnFailureListener { ex ->
                Timber.tag("MLKitDebug").e(ex, "❌ Callback FAILURE: downloadModelIfNeeded gagal untuk $missingLangCode")
            }

            // Loop pemantau progres DownloadManager secara real-time
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            val startTime = System.currentTimeMillis()
            val maxWaitMillis = 3 * 60 * 1000L // 3 menit maksimal
            var pendingCount = 0
            var nullCount = 0

            while (!downloadTask.isComplete && (System.currentTimeMillis() - startTime) < maxWaitMillis) {
                try {
                    val downloadId = getActiveDownloadingId(context)
                    if (downloadId != null && dm != null) {
                        nullCount = 0
                        val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                        if (cursor != null && cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            cursor.close()

                            val statusStr = when (status) {
                                DownloadManager.STATUS_PENDING -> "PENDING (Menunggu antrian)"
                                DownloadManager.STATUS_RUNNING -> "RUNNING (Sedang mengunduh)"
                                DownloadManager.STATUS_PAUSED -> "PAUSED (Jeda, reason: $reason)"
                                DownloadManager.STATUS_SUCCESSFUL -> "SUCCESSFUL (Selesai)"
                                DownloadManager.STATUS_FAILED -> "FAILED (Gagal, reason: $reason)"
                                else -> "STATUS_$status"
                            }

                            if (total > 0) {
                                val downloadedMb = String.format("%.1f", bytes / (1024f * 1024f))
                                val totalMb = String.format("%.1f", total / (1024f * 1024f))
                                val percent = (bytes * 100 / total).toInt()
                                onProgress("Mengunduh $missingLangDisplay ($downloadedMb / $totalMb MB • $percent%)...")
                                Timber.tag("MLKitDebug").d("Progress DownloadManager [ID $downloadId]: $statusStr, $downloadedMb MB / $totalMb MB ($percent%)")
                            } else {
                                onProgress("Menyiapkan $missingLangDisplay...")
                                Timber.tag("MLKitDebug").d("Status DownloadManager [ID $downloadId]: $statusStr")
                            }

                            // Jika DownloadManager tertahan PENDING (karena thermal restriction OS / antrian background)
                            if (status == DownloadManager.STATUS_PENDING) {
                                pendingCount++
                                if (pendingCount >= 4) {
                                    Timber.tag("MLKitDebug").w("DownloadManager tertahan antrian sistem (PENDING >= 4 detik). Beralih ke unduhan langsung Google CDN...")
                                    try { dm.remove(downloadId) } catch (_: Exception) {}
                                    cleanStuckDownloads(context, force = true)
                                    try { translator.close() } catch (_: Exception) {}
                                    val success = downloadAndExtractModelDirectly(
                                        context, source, target,
                                        languageDisplayName = missingLangDisplay,
                                        onProgress = onProgress
                                    )
                                    if (success) {
                                        NotificationUtils.showModelDownloadNotification(context, missingLangDisplay, isFinished = true)
                                        return Translation.getClient(options)
                                    }
                                }
                            } else {
                                pendingCount = 0
                            }

                            if (status == DownloadManager.STATUS_FAILED) {
                                Timber.tag("MLKitDebug").e("DownloadManager melaporkan status GAGAL untuk ID $downloadId (reason=$reason). Beralih ke unduhan langsung...")
                                try { dm.remove(downloadId) } catch (_: Exception) {}
                                cleanStuckDownloads(context, force = true)
                                try { translator.close() } catch (_: Exception) {}
                                val success = downloadAndExtractModelDirectly(
                                    context, source, target,
                                    languageDisplayName = missingLangDisplay,
                                    onProgress = onProgress
                                )
                                if (success) {
                                    NotificationUtils.showModelDownloadNotification(context, missingLangDisplay, isFinished = true)
                                    return Translation.getClient(options)
                                }
                                break
                            }
                        } else {
                            cursor?.close()
                        }
                    } else {
                        nullCount++
                        if (nullCount >= 5) {
                            Timber.tag("MLKitDebug").w("DownloadManager tidak merespons (ID null >= 5 detik). Beralih ke unduhan langsung...")
                            cleanStuckDownloads(context, force = true)
                            try { translator.close() } catch (_: Exception) {}
                            val success = downloadAndExtractModelDirectly(
                                context, source, target,
                                languageDisplayName = missingLangDisplay,
                                onProgress = onProgress
                            )
                            if (success) {
                                NotificationUtils.showModelDownloadNotification(context, missingLangDisplay, isFinished = true)
                                return Translation.getClient(options)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("MLKitDebug").w("Gagal memantau DownloadManager: ${e.message}")
                }
                Thread.sleep(1000)
            }

            Tasks.await(downloadTask, 10, TimeUnit.SECONDS)
            Timber.tag("MLKitDebug").d("✅ Model $missingLangCode BERHASIL diunduh dan siap digunakan!")
            NotificationUtils.showModelDownloadNotification(context, missingLangDisplay, isFinished = true)
        } catch (e: Exception) {
            Timber.tag("MLKitDebug").w("downloadModelIfNeeded terhenti/gagal (${e.message}). Mencoba unduh langsung via Google CDN...")
            cleanStuckDownloads(context, force = true)
            try { translator.close() } catch (_: Exception) {}

            val directSuccess = downloadAndExtractModelDirectly(
                context, source, target,
                languageDisplayName = missingLangDisplay,
                onProgress = onProgress
            )
            if (directSuccess) {
                NotificationUtils.showModelDownloadNotification(context, missingLangDisplay, isFinished = true)
                return Translation.getClient(options)
            }

            // Verifikasi ulang apakah file sudah tersimpan
            sourceReady = try { Tasks.await(modelManager.isModelDownloaded(sourceModel), 5, TimeUnit.SECONDS) } catch (_: Exception) { false }
            targetReady = try { Tasks.await(modelManager.isModelDownloaded(targetModel), 5, TimeUnit.SECONDS) } catch (_: Exception) { false }
            Timber.tag("MLKitDebug").d("Verifikasi akhir status model -> $sourceName: $sourceReady, $targetName: $targetReady")

            if (!sourceReady || !targetReady) {
                NotificationUtils.showModelDownloadNotification(context, missingLangDisplay, isFailed = true)
                translator.close()
                throw Exception(
                    "Gagal menyiapkan $missingLangDisplay. " +
                    "Jaringan: $netStatus. Pastikan koneksi internet aktif."
                )
            }
            NotificationUtils.showModelDownloadNotification(context, missingLangDisplay, isFinished = true)
        }

        return translator
    }

    suspend fun translateLrc(
        context: Context,
        lrcText: String,
        onProgress: (String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val pureText = extractPureText(lrcText)
            if (pureText.isBlank()) return@withContext lrcText

            val languageIdentifier = LanguageIdentification.getClient()
            var sourceLang = languageIdentifier.identifyLanguage(pureText).await()
            Timber.tag("LyricsTranslator").d("Detected language = $sourceLang")

            if (Regex("[\\uAC00-\\uD7A3]").containsMatchIn(pureText)) {
                sourceLang = TranslateLanguage.KOREAN
            } else if (Regex("[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FAF]").containsMatchIn(pureText)) {
                sourceLang = TranslateLanguage.JAPANESE
            } else if (Regex("[\\u4E00-\\u9FFF]").containsMatchIn(pureText)) {
                sourceLang = TranslateLanguage.CHINESE
            }

            if (sourceLang == "und" || sourceLang == TranslateLanguage.INDONESIAN || sourceLang == "id" || sourceLang == "in") {
                return@withContext lrcText + "\n[tool:MLKit]"
            }

            sourceLang = TranslateLanguage.fromLanguageTag(sourceLang) ?: sourceLang

            val lines = lrcText.lines()
            val translatedLines = mutableListOf<String>()

            if (sourceLang == TranslateLanguage.ENGLISH) {
                // Langsung buat translator EN->ID dan unduh model-nya
                onProgress("Menyiapkan Bahasa Inggris → Indonesia...")
                val enIdTranslator = ensureTranslatorReady(
                    context, TranslateLanguage.ENGLISH, TranslateLanguage.INDONESIAN, onProgress
                )

                onProgress("Menerjemahkan lirik lagu...")
                for (line in lines) {
                    translatedLines.add(processLine(line, enIdTranslator))
                }
                enIdTranslator.close()
            } else {
                // Bridge translation: Source -> English -> Indonesian
                val srcName = getLanguageDisplayName(sourceLang)
                onProgress("Menyiapkan $srcName...")
                val srcEnTranslator = ensureTranslatorReady(
                    context, sourceLang, TranslateLanguage.ENGLISH, onProgress
                )

                onProgress("Menyiapkan terjemahan ke Indonesia...")
                val enIdTranslator = ensureTranslatorReady(
                    context, TranslateLanguage.ENGLISH, TranslateLanguage.INDONESIAN, onProgress
                )

                onProgress("Menerjemahkan lirik lagu...")
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
}
