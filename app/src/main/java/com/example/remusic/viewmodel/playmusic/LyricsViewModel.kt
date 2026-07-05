package com.example.remusic.viewmodel.playmusic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.remusic.data.model.LyricLine
import com.example.remusic.data.model.parseLrc
import com.example.remusic.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.remusic.BuildConfig
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.model.LyricsHub
import io.github.jan.supabase.postgrest.from
import timber.log.Timber
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class LyricsViewModel(application: Application) : AndroidViewModel(application) {

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    private val userPreferencesRepository = UserPreferencesRepository(getApplication())
    val lyrics = _lyrics.asStateFlow()

    // posisi playback aktual (ms)
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _lyricsOffset = MutableStateFlow(0L)
    val lyricsOffset = _lyricsOffset.asStateFlow()

    // Cari index terakhir di `lyrics` yang timestamp <= posisi sekarang
    // DEBUG FIX: Tambah offset 150ms agar lirik muncul sedikit lebih cepat (kompensasi lag debug build)
    val activeLyricIndex = combine(lyrics, currentPosition, lyricsOffset) { list, pos, offset ->
        if (list.isEmpty()) return@combine -1
        
        // OFFSET: +50ms (internal lag compensation) + manual user offset
        val adjustedPos = pos + 50 + offset
        
        var idx = -1
        for (i in list.indices) {
            val ts = list[i].timestamp
            if (ts != -1L && ts <= adjustedPos) {
                idx = i
            } else if (ts != -1L && ts > adjustedPos) {
                break
            }
        }
        idx
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    private val _isAutoTranslated = MutableStateFlow(false)
    val isAutoTranslated = _isAutoTranslated.asStateFlow()

    fun loadLyrics(lrcString: String) {
        val parsed = parseLrc(lrcString)
        _lyrics.value = parsed
        _isAutoTranslated.value = lrcString.contains("[tool:MLKit]")
    }

    // dipanggil dari UI tiap kali currentPosition berubah
    fun updatePlaybackPosition(position: Long, offset: Long = 0L) {
        _currentPosition.value = position
        _lyricsOffset.value = offset
    }

    val isTranslateLyrics: StateFlow<Boolean> =
        userPreferencesRepository.translateLyricsFlow.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false
        )

    // STATE LIRIK GLOBAL (Agar tidak reset saat swipe screen di Pager)
    private val _hasAutoScrolledToTop = MutableStateFlow(false)
    val hasAutoScrolledToTop = _hasAutoScrolledToTop.asStateFlow()

    fun setAutoScrolledToTop(hasScrolled: Boolean) {
        _hasAutoScrolledToTop.value = hasScrolled
    }

    // Reset state saat ganti lagu
    fun resetScrollState() {
        _hasAutoScrolledToTop.value = false
    }

    fun toggleTranslateLyrics() {
        viewModelScope.launch {
            val current = isTranslateLyrics.value
            userPreferencesRepository.setTranslateLyrics(!current)
        }
    }

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating = _isTranslating.asStateFlow()

    private val _translationProgress = MutableStateFlow("")
    val translationProgress = _translationProgress.asStateFlow()

    fun translateCurrentLyrics(songId: String, rawLrc: String, musicDao: com.example.remusic.data.local.MusicDao) {
        if (_isTranslating.value) return
        _isTranslating.value = true
        _translationProgress.value = "Menyiapkan terjemahan..."

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val translatedLrc = com.example.remusic.utils.LyricsTranslator.translateLrc(rawLrc) { progress ->
                    _translationProgress.value = progress
                }

                if (translatedLrc != null) {
                    // Update database
                    musicDao.updateLyrics(songId, translatedLrc)
                    
                    // Update current parsed lyrics in UI
                    val parsed = parseLrc(translatedLrc)
                    _lyrics.value = parsed
                    _isAutoTranslated.value = true
                    
                    // Auto-enable translation view if not enabled
                    if (!isTranslateLyrics.value) {
                        userPreferencesRepository.setTranslateLyrics(true)
                    }
                } else {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(getApplication(), "Gagal menerjemahkan lirik.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                _isTranslating.value = false
            }
        }
    }

    fun translateWithGemini(rawLrc: String, songId: String, modelName: String, songTitle: String, artistName: String, musicDao: com.example.remusic.data.local.MusicDao) {
        if (_isTranslating.value) return
        _isTranslating.value = true
        _translationProgress.value = "Menghubungkan ke Gemini ($modelName)..."

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _translationProgress.value = "Menerjemahkan dengan AI..."
                val prompt = """
                    You are an expert song translator and lyricist specializing in translating English songs to Indonesian. 
                    Your goal is to provide a translation that is **natural, emotive, and flows like a subtitle**, capturing the deep meaning without being overly formal or excessively poetic/cringey.

                    **CRITICAL: EMPTY LINES IN LRC**
                    - If an input line is just a timestamp with NO lyrics (e.g. "[09:00.00]"), you MUST output it EXACTLY as is: "[09:00.00]".
                    - DO NOT add a "|" or any text to empty lines. This will break the app's lyric parser.

                    Instructions:
                    1. **Format:** Keep the format EXACTLY as: 
                       [timestamp] original lyrics | translated lyrics
                       (Unless the line is empty, then just return the empty timestamp line).

                    2. **Style & Flow:**
                       - **Full & Natural Sentences:** Use complete Indonesian sentences. Do NOT use overly abbreviated slang words (e.g., NEVER use "tuk" for "untuk", "kumau" for "aku mau" - spell them out naturally but casually).
                       - **Not Too Poetic:** Do not use overly dramatic or ancient poetic words. Use modern, relatable conversational Indonesian.

                    3. **Pronouns & Tone (CRITICAL):**
                       - For relaxed, romantic, chill, or everyday songs, use **"kamu"** instead of "kau". Use "kau" ONLY if the song is extremely aggressive or deeply dramatic.
                       - Use **"aku"** instead of "ku".

                    4. **Idioms & Slang:**
                       - Translate the VIBE and ATTITUDE, not just literal words. 

                    5. **Output:** Return ONLY the formatted lyrics, without any markdown formatting like ``` or other explanations.
                    
                    Here are the lyrics:
                    $rawLrc
                """.trimIndent()

                val client = okhttp3.OkHttpClient()
                val json = org.json.JSONObject()
                val contents = org.json.JSONArray()
                val contentObj = org.json.JSONObject()
                val parts = org.json.JSONArray()
                val partObj = org.json.JSONObject()

                partObj.put("text", prompt)
                parts.put(partObj)
                contentObj.put("parts", parts)
                contents.put(contentObj)
                json.put("contents", contents)

                val mediaType = "application/json".toMediaType()
                val requestBody = json.toString().toRequestBody(mediaType)

                val request = okhttp3.Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=${BuildConfig.GOOGLE_AI_API_KEY}")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                var rawTranslatedText: String? = null
                if (response.isSuccessful && responseBody != null) {
                    val responseJson = org.json.JSONObject(responseBody)
                    val candidates = responseJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val responseParts = content?.optJSONArray("parts")
                        if (responseParts != null && responseParts.length() > 0) {
                            rawTranslatedText = responseParts.getJSONObject(0).optString("text")
                        }
                    }
                } else {
                    Timber.tag("LyricsViewModel").e("Gemini Error: ${response.code} - $responseBody")
                }
                
                val translatedText = rawTranslatedText?.replace("```markdown", "")?.replace("```", "")?.trim()

                if (translatedText != null && translatedText.contains("[")) {
                    _translationProgress.value = "Menyimpan terjemahan..."
                    
                    // 1. Update local DB
                    musicDao.updateLyrics(songId, translatedText)
                    
                    // 2. Update UI
                    val parsed = parseLrc(translatedText)
                    _lyrics.value = parsed
                    // Do not set _isAutoTranslated to true because user does not want the message for AI translated lyrics
                    if (!isTranslateLyrics.value) {
                        userPreferencesRepository.setTranslateLyrics(true)
                    }

                    // 3. Upload to Supabase lyrics_hub
                    try {
                        val hubData = LyricsHub(
                            id = songId,
                            title = songTitle,
                            artist = artistName,
                            lyrics = translatedText,
                            source = "gemini"
                        )
                        SupabaseManager.client.from("lyrics_hub").upsert(hubData)
                        Timber.tag("LyricsViewModel").d("Successfully uploaded translation to lyrics_hub")
                    } catch (e: Exception) {
                        Timber.tag("LyricsViewModel").e(e, "Failed to upload to lyrics_hub")
                    }
                } else {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(getApplication(), "Gagal menerjemahkan dengan Gemini.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Timber.tag("LyricsViewModel").e(e, "Gemini translation failed")
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Error Gemini: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isTranslating.value = false
            }
        }
    }

    fun regenerateTranslation(songId: String, musicDao: com.example.remusic.data.local.MusicDao) {
        if (_isTranslating.value) return
        val currentLyrics = _lyrics.value
        if (currentLyrics.isEmpty()) return
        
        // Reconstruct raw LRC (strip translation and MLKit tag)
        val rawLrcBuilder = java.lang.StringBuilder()
        for (line in currentLyrics) {
            if (line.originalText == "[tool:MLKit]" || line.originalText == "[tool:Gemini]") continue
            
            if (line.timestamp == -1L) {
                rawLrcBuilder.append(line.originalText).append("\n")
            } else {
                val totalMs = line.timestamp
                val min = totalMs / 60000
                val sec = (totalMs % 60000) / 1000
                val ms = (totalMs % 1000) / 10
                val tsString = String.format("[%02d:%02d.%02d]", min, sec, ms)
                rawLrcBuilder.append(tsString).append(line.originalText).append("\n")
            }
        }
        
        translateCurrentLyrics(songId, rawLrcBuilder.toString().trim(), musicDao)
    }
}
