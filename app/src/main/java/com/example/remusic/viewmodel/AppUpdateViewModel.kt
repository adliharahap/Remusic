package com.example.remusic.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.remusic.BuildConfig
import com.example.remusic.data.SupabaseManager
import com.example.remusic.data.model.AppUpdate
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppUpdateViewModel : ViewModel() {
    private val TAG = "AppUpdateViewModel"

    private val _updateAvailable = MutableStateFlow<AppUpdate?>(null)
    val updateAvailable: StateFlow<AppUpdate?> = _updateAvailable.asStateFlow()

    companion object {
        var hasDismissedThisSession = false
    }

    fun checkForUpdates() {
        if (hasDismissedThisSession) return
        
        viewModelScope.launch {
            try {
                // Get the current version code of the app
                val currentVersionCode = BuildConfig.VERSION_CODE
                Log.d(TAG, "🔍 Checking for updates. Local Version Code: $currentVersionCode")

                // Fetch the latest entry from the 'app_updates' table
                val latestUpdate = SupabaseManager.client
                    .from("app_updates")
                    .select {
                        order(column = "version_code", order = Order.DESCENDING)
                        limit(1)
                    }
                    .decodeSingleOrNull<AppUpdate>()

                if (latestUpdate != null) {
                    Log.d(TAG, "📡 Latest Version Code from DB: ${latestUpdate.versionCode}")
                    
                    if (latestUpdate.versionCode > currentVersionCode) {
                        Log.d(TAG, "🚀 New update found! Triggering alert.")
                        _updateAvailable.value = latestUpdate
                    } else {
                        Log.d(TAG, "✅ App is up to date.")
                    }
                } else {
                    Log.d(TAG, "❌ No update records found in 'app_updates' table.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ Failed to check for app update: ${e.message}")
            }
        }
    }

    // Call this to dismiss the dialog temporarily
    fun dismissUpdate() {
        hasDismissedThisSession = true
        _updateAvailable.value = null
    }
}
