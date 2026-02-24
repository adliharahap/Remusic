package com.example.remusic.data.preferences

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class GradientStyle(val displayName: String) {
    PRIMARY("Primary"),
    VIBRANT("Vibrant"),
    MUTED("Muted"),
    DARK("Dark"),
    LIGHT("Light")
}


// Buat instance DataStore sebagai extension property pada Context
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(private val context: Context) {

    // 1. Tambahkan key untuk mode hemat data
    private val DATA_SAVER_MODE_KEY = booleanPreferencesKey("data_saver_mode")
    private val SHUFFLE_ENABLED_KEY = booleanPreferencesKey("shuffle_mode_enabled")
    private val REPEAT_MODE_KEY = intPreferencesKey("repeat_mode")
    private val TRANSLATE_LYRICS_KEY = booleanPreferencesKey("translate_lyrics_enabled")
    private val LAST_SONG_COLOR_KEY = stringPreferencesKey("last_song_primary_color")

    // 2. Buat Flow untuk membaca state shuffle.
    // Jika data belum ada, akan mengembalikan `false` sebagai default.
    val shuffleEnabledFlow: Flow<Boolean> =
            context.dataStore.data.map { preferences -> preferences[SHUFFLE_ENABLED_KEY] ?: false }

    val repeatModeFlow: Flow<Int> =
            context.dataStore.data.map { preferences ->
                preferences[REPEAT_MODE_KEY] ?: Player.REPEAT_MODE_ALL
            }

    val translateLyricsFlow: Flow<Boolean> =
            context.dataStore.data.map { preferences -> preferences[TRANSLATE_LYRICS_KEY] ?: false }

    val lastSongColorFlow: Flow<Color> =
            context.dataStore.data.map { preferences ->
                val colorString = preferences[LAST_SONG_COLOR_KEY]
                if (colorString != null) {
                    try {
                        Color(android.graphics.Color.parseColor(colorString))
                    } catch (e: Exception) {
                        Color(0xFFB71C1C) // Default maroon on parse error
                    }
                } else {
                    Color(0xFFB71C1C) // Default maroon
                }
            }

    // 3. Buat suspend function untuk menulis/menyimpan state shuffle.
    suspend fun setShuffleEnabled(isEnabled: Boolean) {
        context.dataStore.edit { settings -> settings[SHUFFLE_ENABLED_KEY] = isEnabled }
    }

    suspend fun setRepeatMode(mode: Int) {
        context.dataStore.edit { settings -> settings[REPEAT_MODE_KEY] = mode }
    }

    suspend fun setTranslateLyrics(isEnabled: Boolean) {
        context.dataStore.edit { settings -> settings[TRANSLATE_LYRICS_KEY] = isEnabled }
    }

    suspend fun saveLastSongColor(color: Color) {
        val argb = color.toArgb()
        val hexColor = "#${argb.toUInt().toString(16).padStart(8, '0')}"
        context.dataStore.edit { settings -> settings[LAST_SONG_COLOR_KEY] = hexColor }
    }

    val isDataSaverModeEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences -> preferences[DATA_SAVER_MODE_KEY] ?: false }

    suspend fun saveDataSaverMode(isEnabled: Boolean) {
        context.dataStore.edit { settings -> settings[DATA_SAVER_MODE_KEY] = isEnabled }
    }

    // --- LYRICS CONFIG PERSISTENCE ---
    private val LYRICS_FONT_FAMILY_KEY = stringPreferencesKey("lyrics_font_family")
    private val LYRICS_FONT_SIZE_KEY = intPreferencesKey("lyrics_font_size")
    private val LYRICS_ALIGN_KEY = stringPreferencesKey("lyrics_align")
    private val LYRICS_AUTO_SCALE_KEY = booleanPreferencesKey("lyrics_auto_scale")
    private val LYRICS_SCALE_FACTOR_KEY = intPreferencesKey("lyrics_scale_factor")
    private val LYRICS_MARK_PASSED_KEY = booleanPreferencesKey("lyrics_mark_passed")
    private val LYRICS_TRANSLATE_FONT_SIZE_KEY = floatPreferencesKey("lyrics_translate_font_size")

    val lyricsConfigFlow: Flow<com.example.remusic.ui.screen.playmusic.LyricsConfig> =
            context.dataStore.data.map { preferences ->
                val fontFamilyName =
                        preferences[LYRICS_FONT_FAMILY_KEY]
                                ?: com.example.remusic.ui.screen.playmusic.LyricsFontFamily.POPPINS
                                        .name
                val fontSize = preferences[LYRICS_FONT_SIZE_KEY] ?: 21
                val alignName =
                        preferences[LYRICS_ALIGN_KEY]
                                ?: com.example.remusic.ui.screen.playmusic.LyricsAlign.LEFT.name
                val autoScale = preferences[LYRICS_AUTO_SCALE_KEY] ?: false
                val scaleFactor = preferences[LYRICS_SCALE_FACTOR_KEY] ?: 1
                val markPassed = preferences[LYRICS_MARK_PASSED_KEY] ?: false
                val translateFontSize = preferences[LYRICS_TRANSLATE_FONT_SIZE_KEY] ?: 15.5f

                val fontFamily =
                        try {
                            com.example.remusic.ui.screen.playmusic.LyricsFontFamily.valueOf(
                                    fontFamilyName
                            )
                        } catch (e: IllegalArgumentException) {
                            com.example.remusic.ui.screen.playmusic.LyricsFontFamily.POPPINS
                        }

                val align =
                        try {
                            com.example.remusic.ui.screen.playmusic.LyricsAlign.valueOf(alignName)
                        } catch (e: IllegalArgumentException) {
                            com.example.remusic.ui.screen.playmusic.LyricsAlign.LEFT
                        }

                com.example.remusic.ui.screen.playmusic.LyricsConfig(
                        fontFamily = fontFamily,
                        fontSize = fontSize,
                        align = align,
                        autoScaleIfNoTranslation = autoScale,
                        scaleFactor = scaleFactor,
                        markPassedLyrics = markPassed,
                        translateFontSize = translateFontSize
                )
            }

    suspend fun saveLyricsConfig(config: com.example.remusic.ui.screen.playmusic.LyricsConfig) {
        context.dataStore.edit { settings ->
            settings[LYRICS_FONT_FAMILY_KEY] = config.fontFamily.name
            settings[LYRICS_FONT_SIZE_KEY] = config.fontSize
            settings[LYRICS_ALIGN_KEY] = config.align.name
            settings[LYRICS_AUTO_SCALE_KEY] = config.autoScaleIfNoTranslation
            settings[LYRICS_SCALE_FACTOR_KEY] = config.scaleFactor
            settings[LYRICS_MARK_PASSED_KEY] = config.markPassedLyrics
            settings[LYRICS_TRANSLATE_FONT_SIZE_KEY] = config.translateFontSize
        }
    }

    // --- PLAYBACK PERSISTENCE ---
    private val LAST_SONG_ID_KEY = stringPreferencesKey("last_song_id")
    private val LAST_POSITION_KEY = longPreferencesKey("last_position_ms")
    private val LAST_PLAYLIST_NAME_KEY = stringPreferencesKey("last_playlist_name")
    private val GRADIENT_STYLE_KEY = stringPreferencesKey("gradient_style")
    private val GRADIENT_TOP_COLOR_INDEX_KEY = intPreferencesKey("gradient_top_color_index")
    private val GRADIENT_BOTTOM_COLOR_INDEX_KEY = intPreferencesKey("gradient_bottom_color_index")

    val gradientStyleFlow: Flow<GradientStyle> =
        context.dataStore.data.map { preferences ->
            val styleName = preferences[GRADIENT_STYLE_KEY] ?: GradientStyle.PRIMARY.name
            try {
                GradientStyle.valueOf(styleName)
            } catch (e: IllegalArgumentException) {
                GradientStyle.PRIMARY
            }
        }

    suspend fun saveGradientStyle(style: GradientStyle) {
        context.dataStore.edit { settings -> settings[GRADIENT_STYLE_KEY] = style.name }
    }

    val gradientTopColorIndexFlow: Flow<Int> =
        context.dataStore.data.map { preferences -> preferences[GRADIENT_TOP_COLOR_INDEX_KEY] ?: 0 }

    val gradientBottomColorIndexFlow: Flow<Int> =
        context.dataStore.data.map { preferences -> preferences[GRADIENT_BOTTOM_COLOR_INDEX_KEY] ?: 1 }

    suspend fun saveGradientTopColorIndex(index: Int) {
        context.dataStore.edit { settings -> settings[GRADIENT_TOP_COLOR_INDEX_KEY] = index }
    }

    suspend fun saveGradientBottomColorIndex(index: Int) {
        context.dataStore.edit { settings -> settings[GRADIENT_BOTTOM_COLOR_INDEX_KEY] = index }
    }

    val lastSongIdFlow: Flow<String?> =
            context.dataStore.data.map { preferences -> preferences[LAST_SONG_ID_KEY] }

    val lastPositionFlow: Flow<Long> =
            context.dataStore.data.map { preferences -> preferences[LAST_POSITION_KEY] ?: 0L }

    val lastPlaylistNameFlow: Flow<String> =
            context.dataStore.data.map { preferences ->
                preferences[LAST_PLAYLIST_NAME_KEY] ?: "Unknown Playlist"
            }

    suspend fun saveLastSongId(songId: String) {
        context.dataStore.edit { settings -> settings[LAST_SONG_ID_KEY] = songId }
    }

    suspend fun saveLastPosition(positionMs: Long) {
        context.dataStore.edit { settings -> settings[LAST_POSITION_KEY] = positionMs }
    }

    suspend fun saveLastPlaylistName(playlistName: String) {
        context.dataStore.edit { settings -> settings[LAST_PLAYLIST_NAME_KEY] = playlistName }
    }
}
