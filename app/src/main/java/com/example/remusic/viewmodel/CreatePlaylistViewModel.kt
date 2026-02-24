package com.example.remusic.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.UserManager
import com.example.remusic.data.model.Playlist
import com.example.remusic.ui.screen.PlaylistPrivacy
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import android.content.Context
import android.net.Uri
import java.util.UUID
import com.example.remusic.utils.ImageUtils
import com.example.remusic.utils.SupabaseStorageUploader
import com.example.remusic.data.local.MusicDatabase
import com.example.remusic.data.local.entity.CachedPlaylist
import kotlinx.datetime.Clock
import java.time.Instant

@Serializable
data class CreatePlaylistRequest(
    @SerialName("id")
    val id: String,
    
    @SerialName("title")
    val title: String,
    
    @SerialName("description")
    val description: String,
    
    @SerialName("owner_user_id")
    val ownerUserId: String,
    
    @SerialName("visibility")
    val visibility: String,
    
    @SerialName("is_official")
    val isOfficial: Boolean,
    
    @SerialName("cover_url")
    val coverUrl: String? = null
)

sealed class CreatePlaylistState {
    object Idle : CreatePlaylistState()
    object Loading : CreatePlaylistState()
    data class Success(val message: String) : CreatePlaylistState()
    data class Error(val message: String) : CreatePlaylistState()
}

class CreatePlaylistViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<CreatePlaylistState>(CreatePlaylistState.Idle)
    val uiState: StateFlow<CreatePlaylistState> = _uiState.asStateFlow()

    fun createPlaylist(context: Context, title: String, description: String, privacy: PlaylistPrivacy, coverUri: Uri?) {
        val currentUser = UserManager.currentUser
        if (currentUser == null) {
            _uiState.value = CreatePlaylistState.Error("User not logged in.")
            return
        }

        _uiState.value = CreatePlaylistState.Loading

        viewModelScope.launch {
            try {
                // Map privacy to text representation based on the text column in DB
                val visibilityText = when (privacy) {
                    PlaylistPrivacy.PUBLIC -> "public"
                    PlaylistPrivacy.PRIVATE -> "private"
                    PlaylistPrivacy.FRIENDS -> "friends"
                }

                val playlistId = UUID.randomUUID().toString()
                var finalCoverUrl: String? = null

                if (coverUri != null) {
                    val file = ImageUtils.getCompressedImageFile(context, coverUri)
                    if (file != null) {
                        val result = SupabaseStorageUploader.uploadPlaylistImage(file, playlistId)
                        if (result is SupabaseStorageUploader.UploadResult.Success) {
                            finalCoverUrl = result.publicUrl
                        } else if (result is SupabaseStorageUploader.UploadResult.Error) {
                            Log.e("CreatePlaylistVM", "Failed to upload image: ${result.message}")
                            // We can swallow error or bubble up. Let's proceed without cover if upload fails for now
                        }
                    }
                }

                // Create the playlist object using mapping to match Supabase expectations
                val playlistData = CreatePlaylistRequest(
                    id = playlistId,
                    title = title,
                    description = description,
                    ownerUserId = currentUser.uid,
                    visibility = visibilityText,
                    isOfficial = false,
                    coverUrl = finalCoverUrl
                )

                Log.d("CreatePlaylistVM", "Creating playlist: $playlistData")

                SupabaseManager.client.from("playlists").insert(playlistData)

                // Simpan ke local DB agar langsung muncul di PlaylistScreen tanpa perlu force refresh network
                val cachedPlaylist = CachedPlaylist(
                    id = playlistId,
                    title = title,
                    description = description,
                    coverUrl = finalCoverUrl,
                    ownerUserId = currentUser.uid,
                    isOfficial = false,
                    visibility = visibilityText,
                    createdAt = Instant.now().toString()
                )
                
                withContext(Dispatchers.IO) {
                    val musicDao = MusicDatabase.getDatabase(context).musicDao()
                    musicDao.insertPlaylists(listOf(cachedPlaylist))
                }

                Log.d("CreatePlaylistVM", "Playlist successfully created and cached locally.")
                _uiState.value = CreatePlaylistState.Success("Playlist created successfully!")

            } catch (e: Exception) {
                Log.e("CreatePlaylistVM", "Error creating playlist", e)
                _uiState.value = CreatePlaylistState.Error(e.message ?: "Failed to create playlist.")
            }
        }
    }

    fun resetState() {
        _uiState.value = CreatePlaylistState.Idle
    }
}
