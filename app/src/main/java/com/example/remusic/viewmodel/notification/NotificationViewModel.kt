package com.example.remusic.viewmodel.notification

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.UserManager
import com.example.remusic.data.model.Notification
import com.example.remusic.data.preferences.UserPreferencesRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class NotificationUiState {
    object Loading : NotificationUiState()
    data class Success(val notifications: List<Notification>) : NotificationUiState()
    data class Error(val message: String) : NotificationUiState()
}

class NotificationViewModel(application: Application) : AndroidViewModel(application) {

    private val userPrefs = UserPreferencesRepository(application)
    
    private val _uiState = MutableStateFlow<NotificationUiState>(NotificationUiState.Loading)
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    init {
        // Obeserve global read IDs to update UI automatically when a global notif is read
        viewModelScope.launch {
            userPrefs.readGlobalNotificationsFlow.collect { readIds ->
                if (_uiState.value is NotificationUiState.Success) {
                    val currentList = (_uiState.value as NotificationUiState.Success).notifications
                    val updatedList = currentList.map { notif ->
                        if (notif.userId == null) {
                            notif.copy(isReadLocally = readIds.contains(notif.id))
                        } else {
                            notif
                        }
                    }
                    _uiState.value = NotificationUiState.Success(updatedList)
                    updateUnreadCount(updatedList)
                }
            }
        }
        
        // Auto-fetch on ViewModel creation if logged in
        UserManager.currentUser?.uid?.let { fetchNotifications(it) }
    }

    fun fetchNotifications(userId: String) {
        viewModelScope.launch {
            _uiState.value = NotificationUiState.Loading
            try {
                // Fetch where user_id is the current user OR user_id is null (global)
                val rawNotifications = SupabaseManager.client
                    .from("notifications")
                    .select(
                        columns = Columns.list(
                            "id", "user_id", "title", "message", "type", 
                            "reference_id", "image_url", "is_read", "created_at"
                        )
                    ) {
                        filter {
                            or {
                                eq("user_id", userId)
                                filter("user_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.IS, null as String?)
                            }
                        }
                        order("created_at", order = Order.DESCENDING)
                        limit(50) // Adjust if necessary
                    }
                    .decodeList<Notification>()

                // Populate local read states for global notifications
                val localReadIds = userPrefs.readGlobalNotificationsFlow.first()
                val processedNotifications = rawNotifications.map { notif ->
                    if (notif.userId == null) {
                        notif.copy(isReadLocally = localReadIds.contains(notif.id))
                    } else {
                        notif
                    }
                }

                _uiState.value = NotificationUiState.Success(processedNotifications)
                updateUnreadCount(processedNotifications)
            } catch (e: Exception) {
                _uiState.value = NotificationUiState.Error(e.message ?: "Failed to load notifications")
            }
        }
    }

    fun markAsRead(notification: Notification) {
        viewModelScope.launch {
            if (notification.userId == null) {
                // Global mapping: save ID in DataStore
                userPrefs.addReadGlobalNotification(notification.id)
            } else {
                // Personal mapping: update Supabase
                try {
                    SupabaseManager.client
                        .from("notifications")
                        .update(
                            {
                                set("is_read", true)
                            }
                        ) {
                            filter {
                                eq("id", notification.id)
                            }
                        }
                    // Locally update State to reflect instantly instead of re-fetching
                    if (_uiState.value is NotificationUiState.Success) {
                        val currentList = (_uiState.value as NotificationUiState.Success).notifications
                        val updatedList = currentList.map { 
                            if (it.id == notification.id) it.copy(isReadSupabase = true) else it 
                        }
                        _uiState.value = NotificationUiState.Success(updatedList)
                        updateUnreadCount(updatedList)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updateUnreadCount(notifications: List<Notification>) {
        _unreadCount.value = notifications.count { !it.isRead }
    }
}
