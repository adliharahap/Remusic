package com.example.remusic.viewmodel.playmusic

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.remusic.data.model.Song
import com.example.remusic.data.model.Artist
import com.example.remusic.data.model.Playlist
import com.example.remusic.data.model.SongWithArtist
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import timber.log.Timber
import com.metrolist.innertube.models.SongItem

fun PlayMusicViewModel.fetchYouTubePlaylist(playlistId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update {
            it.copy(
                isLoadingArtistDetails = true,
                isArtistSongsLoading = true,
                currentPlaylistDetails = null,
                playlistOwner = null,
                artistSongs = emptyList() // We use artistSongs to hold the fetched list
            )
        }
        try {
            Timber.tag("DEBUG_PLAYER").d("Fetching YouTube Playlist: $playlistId")
            val result = YouTube.playlist(playlistId).getOrNull()
            if (result == null) {
                Timber.tag("DEBUG_PLAYER").e("YouTube Playlist returned null")
                _uiState.update { it.copy(isLoadingArtistDetails = false, isArtistSongsLoading = false) }
                return@launch
            }
            
            // Map to SongWithArtist
            val mappedSongs = result.songs.mapNotNull { songItem: SongItem ->
                val song = Song(
                    id = songItem.id,
                    title = songItem.title,
                    artistId = songItem.artists?.firstOrNull()?.id,
                    coverUrl = songItem.thumbnail,
                    durationMs = (songItem.duration ?: 0) * 1000L
                )
                val artist = songItem.artists?.firstOrNull()?.let { a ->
                    Artist(id = a.id ?: "", name = a.name)
                }
                SongWithArtist(song, artist)
            }
            
            val mappedPlaylist = Playlist(
                id = playlistId,
                title = result.playlist.title,
                coverUrl = result.playlist.thumbnail ?: "",
                ownerUserId = result.playlist.author?.id ?: "youtube",
                isOfficial = true,
                visibility = "public",
                createdAt = null
            )
            
            _uiState.update {
                it.copy(
                    isLoadingArtistDetails = false,
                    isArtistSongsLoading = false,
                    currentPlaylistDetails = mappedPlaylist,
                    artistSongs = mappedSongs // Put it here so the screen can render it
                )
            }
        } catch (e: Exception) {
            Timber.tag("DEBUG_PLAYER").e(e, "Failed to fetch YouTube Playlist")
            _uiState.update { it.copy(isLoadingArtistDetails = false, isArtistSongsLoading = false) }
        }
    }
}

fun PlayMusicViewModel.fetchYouTubeAlbum(albumId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update {
            it.copy(
                isLoadingArtistDetails = true,
                isArtistSongsLoading = true,
                currentPlaylistDetails = null,
                playlistOwner = null,
                artistSongs = emptyList()
            )
        }
        try {
            Timber.tag("DEBUG_PLAYER").d("Fetching YouTube Album: $albumId")
            val result = YouTube.album(albumId).getOrNull()
            if (result == null) {
                Timber.tag("DEBUG_PLAYER").e("YouTube Album returned null")
                _uiState.update { it.copy(isLoadingArtistDetails = false, isArtistSongsLoading = false) }
                return@launch
            }
            
            // Map to SongWithArtist
            val mappedSongs = result.songs.mapNotNull { songItem: SongItem ->
                val song = Song(
                    id = songItem.id,
                    title = songItem.title,
                    artistId = songItem.artists?.firstOrNull()?.id,
                    coverUrl = songItem.thumbnail,
                    durationMs = (songItem.duration ?: 0) * 1000L
                )
                val artist = songItem.artists?.firstOrNull()?.let { a ->
                    Artist(id = a.id ?: "", name = a.name)
                }
                SongWithArtist(song, artist)
            }
            
            val mappedPlaylist = Playlist(
                id = albumId,
                title = result.album.title,
                coverUrl = result.album.thumbnail,
                ownerUserId = result.album.artists?.firstOrNull()?.id ?: "youtube",
                isOfficial = true,
                visibility = "public",
                createdAt = null
            )
            
            _uiState.update {
                it.copy(
                    isLoadingArtistDetails = false,
                    isArtistSongsLoading = false,
                    currentPlaylistDetails = mappedPlaylist,
                    artistSongs = mappedSongs
                )
            }
        } catch (e: Exception) {
            Timber.tag("DEBUG_PLAYER").e(e, "Failed to fetch YouTube Album")
            _uiState.update { it.copy(isLoadingArtistDetails = false, isArtistSongsLoading = false) }
        }
    }
}
