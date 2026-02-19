package com.example.remusic.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.remusic.data.model.Artist
import com.example.remusic.data.model.Playlist
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.viewmodel.homeviewmodel.HomeUiState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Menyimpan dan memuat data HomeScreen ke/dari SharedPreferences sebagai JSON.
 * Digunakan agar UI HomeScreen tidak kosong saat offline.
 */
object HomeCacheManager {

    private val TAG = "HomeCacheManager"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private const val PREFS_NAME = "remusic_home_cache"
    private const val KEY_HOME_DATA = "home_data_v1"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ── Data class untuk serialisasi cache ──────────────────────────
    @Serializable
    data class CachedHomeData(
        val allSongsWithArtists: List<SongWithArtist> = emptyList(),
        val officialPlaylists: List<Playlist> = emptyList(),
        val recentlyPlayed: List<SongWithArtist> = emptyList(),
        val followedArtists: List<Artist> = emptyList(),
        val mostLoved: List<SongWithArtist> = emptyList(),
        val topTrending: List<SongWithArtist> = emptyList(),
        val quickPickSongs: List<SongWithArtist> = emptyList()
    )

    // ── Save ────────────────────────────────────────────────────────
    fun save(state: HomeUiState.Success) {
        try {
            val data = CachedHomeData(
                allSongsWithArtists = state.allSongsWithArtists,
                officialPlaylists = state.officialPlaylists,
                recentlyPlayed = state.recentlyPlayed,
                followedArtists = state.followedArtists,
                mostLoved = state.mostLoved,
                topTrending = state.topTrending,
                quickPickSongs = state.quickPickSongs
            )
            val raw = json.encodeToString(data)
            prefs?.edit()?.putString(KEY_HOME_DATA, raw)?.apply()
            Log.d(TAG, "💾 Home data tersimpan ke cache (${state.quickPickSongs.size} quick picks, ${state.officialPlaylists.size} playlists)")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Gagal simpan home cache: ${e.message}")
        }
    }

    // ── Load ────────────────────────────────────────────────────────
    fun load(): HomeUiState.Success? {
        val raw = prefs?.getString(KEY_HOME_DATA, null) ?: return null
        return try {
            val data = json.decodeFromString<CachedHomeData>(raw)
            Log.d(TAG, "📦 Home data dimuat dari cache (${data.quickPickSongs.size} quick picks)")
            HomeUiState.Success(
                allSongsWithArtists = data.allSongsWithArtists,
                officialPlaylists = data.officialPlaylists,
                recentlyPlayed = data.recentlyPlayed,
                followedArtists = data.followedArtists,
                mostLoved = data.mostLoved,
                topTrending = data.topTrending,
                quickPickSongs = data.quickPickSongs
            )
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Gagal parse home cache: ${e.message}")
            prefs?.edit()?.remove(KEY_HOME_DATA)?.apply()
            null
        }
    }

    fun clear() {
        prefs?.edit()?.remove(KEY_HOME_DATA)?.apply()
    }
}
