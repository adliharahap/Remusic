package com.example.remusic.viewmodel.requestsong

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.UserManager
import com.example.remusic.data.model.SongRequestInsert
import com.example.remusic.data.network.DeezerRetrofit
import com.example.remusic.data.network.DeezerTrack
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class RequestSongViewModel(application: Application) : AndroidViewModel(application) {
    
    private val exoPlayer = ExoPlayer.Builder(application).build()
    
    // HTTP Client untuk webhook telegram
    private val httpClient = HttpClient(OkHttp)
    
    private val _searchResults = MutableStateFlow<List<DeezerTrack>>(emptyList())
    val searchResults = _searchResults.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    
    private val _currentlyPlayingTrackId = MutableStateFlow<String?>(null)
    val currentlyPlayingTrackId = _currentlyPlayingTrackId.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()
    
    private var progressJob: Job? = null
    
    // Status for request: Idle, Loading, Success, Error
    private val _requestStatus = MutableStateFlow<RequestStatus>(RequestStatus.Idle)
    val requestStatus = _requestStatus.asStateFlow()
    
    sealed class RequestStatus {
        object Idle : RequestStatus()
        object Loading : RequestStatus()
        data class Success(val message: String) : RequestStatus()
        data class Error(val message: String) : RequestStatus()
    }

    private val _requestedTrackIds = MutableStateFlow<Set<Long>>(emptySet())
    val requestedTrackIds = _requestedTrackIds.asStateFlow()
    
    // --- My Requests State ---
    @Serializable
    data class MySongRequest(
        val id: String,
        @SerialName("song_title") val songTitle: String,
        @SerialName("artist_name") val artistName: String,
        @SerialName("preview_url") val previewUrl: String? = null,
        @SerialName("cover_url") val coverUrl: String? = null,
        @SerialName("artist_photo_url") val artistPhotoUrl: String? = null,
        val status: String? = "pending", // pending, approved, rejected, fulfilled
        @SerialName("rejection_reason") val rejectionReason: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null
    )
    
    private val _myRequests = MutableStateFlow<List<MySongRequest>>(emptyList())
    val myRequests = _myRequests.asStateFlow()
    
    private val _isLoadingRequests = MutableStateFlow(false)
    val isLoadingRequests = _isLoadingRequests.asStateFlow()
    
    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                } else if (playbackState == Player.STATE_ENDED) {
                    _isPlaying.value = false
                    _currentPosition.value = 0L
                    stopProgressTracker()
                    _currentlyPlayingTrackId.value = null
                }
            }
        })
        
        // Fetch user requests on init
        fetchMyRequests()
    }
    
    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive && exoPlayer.isPlaying) {
                _currentPosition.value = exoPlayer.currentPosition
                delay(100)
            }
        }
    }
    
    private fun stopProgressTracker() {
        progressJob?.cancel()
    }
    
    fun searchSongs(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // Ensure limit is passed or just use the default
                val response = DeezerRetrofit.api.searchTracks(query)
                _searchResults.value = response.data
            } catch (e: Exception) {
                _errorMessage.value = "Gagal mencari lagu: ${e.message}"
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun playPreview(previewUrl: String, trackId: String) {
        if (previewUrl.isBlank()) {
            _errorMessage.value = "Preview tidak tersedia untuk lagu ini"
            return
        }

        if (_currentlyPlayingTrackId.value == trackId && _isPlaying.value) {
            exoPlayer.pause()
        } else {
            val mediaItem = MediaItem.fromUri(previewUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
            _currentlyPlayingTrackId.value = trackId
            _currentPosition.value = 0L
            _duration.value = 0L
        }
    }
    
    fun stopPreview() {
        if (_isPlaying.value) {
            exoPlayer.pause()
        }
        _currentlyPlayingTrackId.value = null
        stopProgressTracker()
        _currentPosition.value = 0L
        _duration.value = 0L
    }
    
    fun pausePreview() {
        exoPlayer.pause()
    }
    
    fun requestSong(track: DeezerTrack) {
        val user = UserManager.currentUser
        if (user == null) {
            _requestStatus.value = RequestStatus.Error("Kamu harus login untuk request lagu.")
            return
        }
        
        // Prevent duplicate request clicks if already tracked locally in this session
        if (_requestedTrackIds.value.contains(track.id)) {
            _requestStatus.value = RequestStatus.Error("Kamu sudah merest lagu ini.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _requestStatus.value = RequestStatus.Loading
            try {
                // 1. Check if the user has already requested this exact song
                val existingRequest = SupabaseManager.client
                    .from("song_requests")
                    .select {
                        filter {
                            eq("requester_id", user.uid)
                            eq("song_title", track.title)
                            eq("artist_name", track.artist.name)
                        }
                    }
                    .decodeList<SongRequestInsert>()

                if (existingRequest.isNotEmpty()) {
                    // Update local state so the button changes immediately, even if we just found out
                    _requestedTrackIds.update { it + track.id }
                    _requestStatus.value = RequestStatus.Error("Kamu sudah pernah me-request lagu ini sebelumnya.")
                    return@launch
                }

                // 2. If no existing request, insert new data
                val insertData = SongRequestInsert(
                    requesterId = user.uid,
                    songTitle = track.title,
                    artistName = track.artist.name,
                    referenceUrl = track.link,
                    previewUrl = track.preview,
                    coverUrl = track.album.coverMedium,
                    artistPhotoUrl = track.artist.pictureMedium
                )
                
                SupabaseManager.client.from("song_requests").insert(insertData)
                
                // 3. Mark as requested locally to update UI
                _requestedTrackIds.update { it + track.id }
                
                _requestStatus.value = RequestStatus.Success("Berhasil request lagu ${track.title}!")

                // 4. Notify Next.js Admin Backend (Webhook)
                launch(Dispatchers.IO) {
                    try {
                        val jsonBody = JSONObject().apply {
                            put("requester_name", user.displayName ?: "User Anonim")
                            put("song_title", track.title)
                            put("artist_name", track.artist.name)
                            put("cover_url", track.album.coverMedium)
                            put("preview_url", track.preview)
                        }
                        
                        httpClient.post("https://remusic-admin.vercel.app/api/telegram/request-song") {
                            contentType(ContentType.Application.Json)
                            setBody(jsonBody.toString())
                        }
                        android.util.Log.d("Webhook", "Berhasil kirim notifikasi request ke admin.")
                    } catch (e: Exception) {
                        android.util.Log.e("Webhook", "Gagal kirim notifikasi webhook", e)
                    }
                }
                
            } catch (e: Exception) {
                _requestStatus.value = RequestStatus.Error("Gagal request lagu: ${e.message}")
            }
        }
    }
    
    fun requestManualSong(title: String, artist: String, referenceUrl: String) {
        val user = UserManager.currentUser
        if (user == null) {
            _requestStatus.value = RequestStatus.Error("Kamu harus login untuk me-request lagu.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _requestStatus.value = RequestStatus.Loading
            try {
                // Check local state first for simple deduplication (by title+artist loosely, or just skip local check for manual)
                // We will check the database directly
                val existingRequest = SupabaseManager.client
                    .from("song_requests")
                    .select {
                        filter {
                            eq("requester_id", user.uid)
                            ilike("song_title", title)
                            ilike("artist_name", artist)
                        }
                    }
                    .decodeList<SongRequestInsert>()

                if (existingRequest.isNotEmpty()) {
                    _requestStatus.value = RequestStatus.Error("Kamu sudah pernah me-request lagu ini sebelumnya.")
                    return@launch
                }

                val insertData = SongRequestInsert(
                    requesterId = user.uid,
                    songTitle = title,
                    artistName = artist,
                    referenceUrl = referenceUrl.ifBlank { null },
                    previewUrl = null,
                    coverUrl = null,
                    artistPhotoUrl = null
                )
                
                SupabaseManager.client.from("song_requests").insert(insertData)
                
                _requestStatus.value = RequestStatus.Success("Berhasil request lagu $title secara manual!")

                // Notify Next.js Admin Backend (Webhook)
                launch(Dispatchers.IO) {
                    try {
                        val jsonBody = JSONObject().apply {
                            put("requester_name", user.displayName ?: "User Anonim")
                            put("song_title", title)
                            put("artist_name", artist)
                            // Manual request tidak ada cover/preview
                        }
                        
                        httpClient.post("https://remusic-admin.vercel.app/api/telegram/request-song") {
                            contentType(ContentType.Application.Json)
                            setBody(jsonBody.toString())
                        }
                        android.util.Log.d("Webhook", "Berhasil kirim notifikasi manual request ke admin.")
                    } catch (e: Exception) {
                        android.util.Log.e("Webhook", "Gagal kirim notifikasi webhook manual", e)
                    }
                }
                
                // Refresh list so it shows up in "Status Request" tab
                fetchMyRequests()
            } catch (e: Exception) {
                _requestStatus.value = RequestStatus.Error("Gagal request lagu manual: ${e.message}")
            }
        }
    }
    
    fun resetRequestStatus() {
        _requestStatus.value = RequestStatus.Idle
    }
    
    fun fetchMyRequests() {
        val user = UserManager.currentUser ?: return
        
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingRequests.value = true
            
            var attempt = 0
            val maxRetries = 4
            var success = false
            
            while (attempt < maxRetries && !success) {
                try {
                    val requests = SupabaseManager.client
                        .from("song_requests")
                        .select {
                            filter {
                                eq("requester_id", user.uid)
                            }
                            order("created_at", Order.DESCENDING)
                        }
                        .decodeList<MySongRequest>()
                    
                    _myRequests.value = requests
                    android.util.Log.d("RequestSong", "Fetched ${requests.size} requests")
                    success = true
                } catch (e: Exception) {
                    attempt++
                    android.util.Log.e("RequestSong", "Error fetching requests (Attempt $attempt/$maxRetries): ${e.message}", e)
                    if (attempt < maxRetries) {
                        delay(1000L * attempt) // Exponential backoff like delay 1s, 2s, 3s
                    } else {
                        // All retries failed
                    }
                }
            }
            
            _isLoadingRequests.value = false
        }
    }
    
    fun cancelRequest(requestId: String, context: android.content.Context) {
        val user = UserManager.currentUser
        if (user == null) {
            android.widget.Toast.makeText(context, "Harap login ulang", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        // Optimistic UI update
        val previousRequests = _myRequests.value
        _myRequests.update { list -> list.filter { it.id != requestId } }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Delete from DB strictly matching the requester id as well
                SupabaseManager.client.from("song_requests").delete {
                    filter {
                        eq("id", requestId)
                        eq("requester_id", user.uid)
                    }
                }
                
                android.util.Log.d("RequestSong", "Successfully deleted request $requestId")
                
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Request lagu dibatalkan", android.widget.Toast.LENGTH_SHORT).show()
                }
                
                // Fetch again to ensure sync
                fetchMyRequests()
                
            } catch (e: Exception) {
                // Rollback optimistic update
                _myRequests.value = previousRequests
                android.util.Log.e("RequestSong", "Failed to delete request $requestId: ${e.message}", e)
                
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Gagal membatalkan request: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        exoPlayer.release()
        httpClient.close()
        stopProgressTracker()
    }
}
