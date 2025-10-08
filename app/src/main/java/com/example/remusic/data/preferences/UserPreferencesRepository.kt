package com.example.remusic.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Buat instance DataStore sebagai extension property pada Context
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(private val context: Context) {

    // 1. Definisikan key untuk menyimpan state shuffle
    private val SHUFFLE_ENABLED_KEY = booleanPreferencesKey("shuffle_mode_enabled")
    private val REPEAT_MODE_KEY = intPreferencesKey("repeat_mode")
    private val TRANSLATE_LYRICS_KEY = booleanPreferencesKey("translate_lyrics_enabled")

    // 2. Buat Flow untuk membaca state shuffle.
    // Jika data belum ada, akan mengembalikan `false` sebagai default.
    val shuffleEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SHUFFLE_ENABLED_KEY] ?: false
        }

    val repeatModeFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[REPEAT_MODE_KEY] ?: Player.REPEAT_MODE_ALL
        }

    val translateLyricsFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[TRANSLATE_LYRICS_KEY] ?: false
        }

    // 3. Buat suspend function untuk menulis/menyimpan state shuffle.
    suspend fun setShuffleEnabled(isEnabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[SHUFFLE_ENABLED_KEY] = isEnabled
        }
    }

    suspend fun setRepeatMode(mode: Int) {
        context.dataStore.edit { settings ->
            settings[REPEAT_MODE_KEY] = mode
        }
    }

    suspend fun setTranslateLyrics(isEnabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[TRANSLATE_LYRICS_KEY] = isEnabled
        }
    }
}