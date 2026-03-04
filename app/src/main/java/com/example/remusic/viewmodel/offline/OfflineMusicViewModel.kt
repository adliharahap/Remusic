package com.example.remusic.viewmodel.offline

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.remusic.data.model.Artist
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.SongWithArtist
import com.example.remusic.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "OFFLINE_PERF"

enum class OfflineSortOrder(val label: String) {
    DATE_ADDED_DESC("Terbaru"),
    DATE_ADDED_ASC("Terlama"),
    TITLE_ASC("Judul (A-Z)"),
    TITLE_DESC("Judul (Z-A)"),
    ARTIST_ASC("Artis (A-Z)"),
    DURATION_DESC("Terpanjang")
}

data class OfflinePlaylistUiState(
    val allSongs: List<SongWithArtist> = emptyList(),
    val songs: List<SongWithArtist> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val showOnlyRemusic: Boolean = true,
    val sortOrder: OfflineSortOrder = OfflineSortOrder.DATE_ADDED_DESC,
    val isEnriching: Boolean = false
)

class OfflineMusicViewModel(
    private val prefs: UserPreferencesRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(OfflinePlaylistUiState())
    val uiState: StateFlow<OfflinePlaylistUiState> = _uiState.asStateFlow()

    /** Factory for creating with UserPreferencesRepository */
    class Factory(private val prefs: UserPreferencesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            OfflineMusicViewModel(prefs) as T
    }

    /**
     * Two-phase loading:
     *   Phase 1 → Fast MediaStore scan (no file I/O) → emits to UI in ~100-200ms
     *   Phase 2 → File checks for canvas (.mp4) and lyrics (.lrc) → silent background update
     */
    fun loadOfflineMusic(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            Log.d(TAG, "════════════════════════════════════════")
            Log.d(TAG, "loadOfflineMusic() START")

            // Load persisted filter preference before loading songs
            val savedOnlyRemusic = prefs?.offlineShowOnlyRemusicFlow?.first() ?: true
            _uiState.update { it.copy(isLoading = true, errorMessage = null, showOnlyRemusic = savedOnlyRemusic) }
            Log.d(TAG, "[Prefs] showOnlyRemusic loaded: $savedOnlyRemusic")

            try {
                // ── PHASE 1: Fast MediaStore scan (zero file I/O) ──────────────
                Log.d(TAG, "[Phase1] Starting fast MediaStore scan (no file I/O)...")
                val t1 = System.currentTimeMillis()

                val fastSongs = fastScanFromMediaStore(context)

                Log.d(TAG, "[Phase1] Scan done: ${fastSongs.size} songs in ${System.currentTimeMillis() - t1}ms")
                Log.d(TAG, "[Phase1] Remusic songs: ${fastSongs.count { it.song.uploaderUserId == "remusic_offline" }}")

                val filtered1 = applyFilterAndSort(
                    fastSongs,
                    _uiState.value.showOnlyRemusic,
                    _uiState.value.sortOrder
                )
                Log.d(TAG, "[Phase1] After filter (showOnlyRemusic=${_uiState.value.showOnlyRemusic}): ${filtered1.size} songs")

                _uiState.update {
                    it.copy(
                        allSongs = fastSongs,
                        songs = filtered1,
                        isLoading = false,
                        isEnriching = true
                    )
                }
                Log.d(TAG, "[Phase1] ✅ UI updated. Time from START: ${System.currentTimeMillis() - t0}ms")

                // ── PHASE 2: Enrichment — canvas + lyrics file checks ──────────
                Log.d(TAG, "[Phase2] Starting background file enrichment for ${fastSongs.size} songs...")
                val t2 = System.currentTimeMillis()

                val enrichedSongs = enrichWithLocalFiles(fastSongs)
                val enrichedCanvas  = enrichedSongs.count { it.song.canvasUrl != null }
                val enrichedLyrics  = enrichedSongs.count { it.song.lyrics != null }

                Log.d(TAG, "[Phase2] Done in ${System.currentTimeMillis() - t2}ms | canvas=$enrichedCanvas | lyrics=$enrichedLyrics")

                // Only push update if anything actually changed
                val hasChanges = enrichedSongs.any { e ->
                    val original = fastSongs.find { it.song.id == e.song.id }
                    original?.song?.canvasUrl != e.song.canvasUrl || original?.song?.lyrics != e.song.lyrics
                }

                if (hasChanges) {
                    val filtered2 = applyFilterAndSort(enrichedSongs, _uiState.value.showOnlyRemusic, _uiState.value.sortOrder)
                    _uiState.update { it.copy(allSongs = enrichedSongs, songs = filtered2, isEnriching = false) }
                    Log.d(TAG, "[Phase2] ✅ Enriched data applied to UI (${enrichedSongs.size} songs).")
                } else {
                    _uiState.update { it.copy(isEnriching = false) }
                    Log.d(TAG, "[Phase2] ℹ️ No canvas/lyrics found — skipping UI update.")
                }

                Log.d(TAG, "loadOfflineMusic() COMPLETE. Total: ${System.currentTimeMillis() - t0}ms")
                Log.d(TAG, "════════════════════════════════════════")

            } catch (e: Exception) {
                Log.e(TAG, "loadOfflineMusic() ERROR: ${e.message}", e)
                _uiState.update {
                    it.copy(isLoading = false, isEnriching = false, errorMessage = e.message ?: "Gagal memuat musik offline")
                }
            }
        }
    }

    fun setSortOrder(order: OfflineSortOrder) {
        viewModelScope.launch(Dispatchers.Default) {
            val t = System.currentTimeMillis()
            val count = _uiState.value.allSongs.size
            Log.d(TAG, "setSortOrder($order) | allSongs=$count")
            val current = _uiState.value
            val filtered = applyFilterAndSort(current.allSongs, current.showOnlyRemusic, order)
            _uiState.update { it.copy(sortOrder = order, songs = filtered) }
            Log.d(TAG, "setSortOrder done in ${System.currentTimeMillis() - t}ms → ${filtered.size} songs visible")
        }
    }

    fun toggleShowOnlyRemusic(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.Default) {
            val t = System.currentTimeMillis()
            val count = _uiState.value.allSongs.size
            Log.d(TAG, "toggleShowOnlyRemusic($enabled) | allSongs=$count")
            val current = _uiState.value
            val filtered = applyFilterAndSort(current.allSongs, enabled, current.sortOrder)
            _uiState.update { it.copy(showOnlyRemusic = enabled, songs = filtered) }
            Log.d(TAG, "toggleShowOnlyRemusic done in ${System.currentTimeMillis() - t}ms → ${filtered.size} songs visible")
            
            // Persist to UserPreferences
            prefs?.saveOfflineShowOnlyRemusic(enabled)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Filter + Sort — always runs on Dispatchers.Default
    // ──────────────────────────────────────────────────────────────────────────
    private fun applyFilterAndSort(
        all: List<SongWithArtist>,
        onlyRemusic: Boolean,
        order: OfflineSortOrder
    ): List<SongWithArtist> {
        val filtered = if (onlyRemusic) {
            all.filter { it.song.uploaderUserId == "remusic_offline" }
        } else {
            all
        }
        return when (order) {
            OfflineSortOrder.DATE_ADDED_DESC -> filtered
            OfflineSortOrder.DATE_ADDED_ASC  -> filtered.reversed()
            OfflineSortOrder.TITLE_ASC       -> filtered.sortedBy { it.song.title.lowercase() }
            OfflineSortOrder.TITLE_DESC      -> filtered.sortedByDescending { it.song.title.lowercase() }
            OfflineSortOrder.ARTIST_ASC      -> filtered.sortedBy { it.artist?.name?.lowercase() ?: "" }
            OfflineSortOrder.DURATION_DESC   -> filtered.sortedByDescending { it.song.durationMs }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PHASE 1: Fast scan — no file I/O, no exists() calls
    // telegramDirectUrl is abused to carry the absolute path for Phase 2.
    // ──────────────────────────────────────────────────────────────────────────
    private suspend fun fastScanFromMediaStore(context: Context): List<SongWithArtist> = withContext(Dispatchers.IO) {
        val songList = mutableListOf<SongWithArtist>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA
        )

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol          = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val albumIdCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val artistCol      = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataCol        = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                Log.d(TAG, "[Phase1] Cursor opened: ${cursor.count} total audio files in MediaStore")

                while (cursor.moveToNext()) {
                    val id           = cursor.getLong(idCol)
                    val contentUri   = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val absolutePath = cursor.getString(dataCol) ?: ""
                    val displayName  = cursor.getString(displayNameCol) ?: ""
                    val durationMs   = cursor.getLong(durationCol).takeIf { it > 0 } ?: 0L

                    var title      = cursor.getString(titleCol) ?: "Unknown Title"
                    var artistName = (cursor.getString(artistCol) ?: "Unknown Artist")
                        .replace("<unknown>", "Unknown Artist", ignoreCase = true)
                        .replace("/", ", ")

                    if (title == displayName || title == "Unknown Title") {
                        val parts = displayName.substringBeforeLast(".").split(" - ")
                        if (parts.size >= 2) {
                            title      = parts[0].trim()
                            artistName = parts[1].trim()
                        } else {
                            title = displayName.substringBeforeLast(".")
                        }
                    }

                    val albumId     = cursor.getLong(albumIdCol)
                    val albumArtUri = android.net.Uri.parse("content://media/external/audio/albumart")
                    val coverUri    = ContentUris.withAppendedId(albumArtUri, albumId).toString()
                    val isRemusic   = absolutePath.contains("/Remusic/", ignoreCase = true)

                    val song = Song(
                        id              = "offline_$id",
                        title           = title,
                        artistId        = "offline_artist_$id",
                        audioUrl        = contentUri.toString(),
                        coverUrl        = coverUri,
                        durationMs      = durationMs,
                        lyrics          = null,         // filled in Phase 2
                        canvasUrl       = null,         // filled in Phase 2
                        uploaderUserId  = if (isRemusic) "remusic_offline" else "system",
                        createdAt       = System.currentTimeMillis().toString(),
                        // Temporarily carry absolutePath for Phase 2 enrichment
                        telegramDirectUrl = absolutePath
                    )

                    songList.add(SongWithArtist(song, Artist(
                        id = "offline_artist_$id", name = artistName,
                        photoUrl = null, description = null,
                        createdAt = System.currentTimeMillis().toString()
                    )))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Phase1] MediaStore scan error: ${e.message}", e)
        }
        songList
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PHASE 2: For each song, check if .mp4 (canvas) or .lrc (lyrics) exists
    // beside the MP3 on disk. This is the slow part (N × 2 file I/O).
    // ──────────────────────────────────────────────────────────────────────────
    private suspend fun enrichWithLocalFiles(songs: List<SongWithArtist>): List<SongWithArtist> = withContext(Dispatchers.IO) {
        songs.map { swa ->
            val absolutePath = swa.song.telegramDirectUrl ?: return@map swa  // path stored here in Phase 1
            if (absolutePath.isBlank()) return@map swa

            val mp3File       = File(absolutePath)
            val folder        = mp3File.parentFile ?: return@map swa
            if (!folder.exists()) return@map swa

            val mp4File       = File(folder, mp3File.nameWithoutExtension + ".mp4")
            val lrcFile       = File(folder, mp3File.nameWithoutExtension + ".lrc")

            val canvasUrl     = if (mp4File.exists()) "file://${mp4File.absolutePath}" else null
            val lyrics        = if (lrcFile.exists()) {
                try { lrcFile.readText() } catch (_: Exception) { null }
            } else null

            if (canvasUrl == null && lyrics == null) return@map swa

            // Replace song data, clear the temp absolutePath field
            swa.copy(song = swa.song.copy(
                canvasUrl = canvasUrl,
                lyrics    = lyrics,
                telegramDirectUrl = null  // clear temp field
            ))
        }
    }

    // Store the pending song to be deleted 
    var pendingDeleteSong: SongWithArtist? = null

    /**
     * Delete an offline song from MediaStore and device storage, requesting user permission on Android 11+.
     */
    fun requestDeleteSong(
        context: Context, 
        songWithArtist: SongWithArtist, 
        onIntentSenderRequired: (IntentSender?) -> Unit
    ) {
        val rawId = songWithArtist.song.id.removePrefix("offline_")
        val mediaId = rawId.toLongOrNull() ?: return
        val audioUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
        val urisToDelete = mutableListOf<Uri>(audioUri)

        // Find .lrc and .mp4 paths to include in the delete request
        val audioUrl = songWithArtist.song.audioUrl ?: ""
        val path = audioUrl.removePrefix("file://")
        val mp3File = File(path)
        val folder = mp3File.parentFile

        // Only process extra files if they are in the Remusic folder
        var isRemusicFolder = false
        if (folder != null && folder.absolutePath.contains("/Remusic/")) {
            isRemusicFolder = true
            val safeTitle = songWithArtist.song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val lrcPath = File(folder, "$safeTitle.lrc").absolutePath
            val mp4Path = File(folder, "$safeTitle.mp4").absolutePath
            
            // Query MediaStore to get URIs for these paths
            val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DATA)
            val selection = "${MediaStore.Files.FileColumns.DATA} IN (?, ?)"
            val selectionArgs = arrayOf(lrcPath, mp4Path)
            
            try {
                context.contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        urisToDelete.add(ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying extra files URIs: ${e.message}")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                // Android 11+: Request permission to delete ALL selected files
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, urisToDelete)
                pendingDeleteSong = songWithArtist
                onIntentSenderRequired(pendingIntent.intentSender)
            } catch (e: Exception) {
                // Fallback attempt
                deleteExtraFilesAndRefresh(context, songWithArtist)
                onIntentSenderRequired(null)
            }
        } else {
            try {
                // API 29 and below: Delete directly
                var allDeleted = true
                for (uri in urisToDelete) {
                    val deletedRows = context.contentResolver.delete(uri, null, null)
                    if (deletedRows <= 0) allDeleted = false
                }
                
                deleteExtraFilesAndRefresh(context, songWithArtist)
                onIntentSenderRequired(null)
            } catch (securityException: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val recoverableSecurityException = securityException as? android.app.RecoverableSecurityException
                    if (recoverableSecurityException != null) {
                        pendingDeleteSong = songWithArtist
                        onIntentSenderRequired(recoverableSecurityException.userAction.actionIntent.intentSender)
                    } else {
                        onIntentSenderRequired(null)
                    }
                } else {
                    onIntentSenderRequired(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during deletion: ${e.message}")
                onIntentSenderRequired(null)
            }
        }
    }

    /**
     * Cleans up additional files (.lrc, .mp4, and empty folder) manually if MediaStore missed them
     */
    fun deleteExtraFilesAndRefresh(context: Context, song: SongWithArtist? = pendingDeleteSong) {
        if (song == null) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val audioUrl = song.song.audioUrl ?: return@launch
                val path = audioUrl.removePrefix("file://")
                val mp3File = File(path)
                val folder = mp3File.parentFile
                
                // Only process folder deletion if it is a specific song folder inside Remusic
                if (folder != null && folder.exists() && folder.absolutePath.contains("/Remusic/") && folder.name != "Remusic") {
                    val safeTitle = song.song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    
                    val lrcFile = File(folder, "$safeTitle.lrc")
                    if (lrcFile.exists()) {
                        lrcFile.delete()
                        Log.d(TAG, "Manually deleted associated .lrc file")
                    }
                    
                    val mp4File = File(folder, "$safeTitle.mp4")
                    if (mp4File.exists()) {
                        mp4File.delete()
                        Log.d(TAG, "Manually deleted associated .mp4 file")
                    }
                    
                    if (mp3File.exists()) mp3File.delete()
                    
                    // Check if folder is empty, if so, delete it
                    val remainingFiles = folder.listFiles()
                    if (remainingFiles.isNullOrEmpty()) {
                        folder.delete()
                        Log.d(TAG, "Deleted empty song folder: ${folder.absolutePath}")
                    } else {
                        Log.d(TAG, "Folder not empty, skipping folder deletion")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up extra files", e)
            } finally {
                pendingDeleteSong = null
                loadOfflineMusic(context)
            }
        }
    }
}
