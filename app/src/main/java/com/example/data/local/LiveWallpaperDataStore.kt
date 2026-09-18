package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.liveWallpaperDataStore: DataStore<Preferences> by preferencesDataStore(name = "live_wallpaper_prefs")

/**
 * Manages the currently selected live wallpaper video URL via Jetpack DataStore Preferences.
 *
 * DATASTORE HAND-OFF EXPLANATION:
 * When a user taps "Set Live Wallpaper" in WallpaperDetailScreen, Android's system intent
 * (WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER) opens the system wallpaper preview activity for
 * VideoLiveWallpaperService.
 *
 * However, the Android framework does not forward custom Intent extras through this system chooser
 * to the WallpaperService. To bridge this hand-off, WallpaperDetailScreen writes the selected
 * video URL into this local DataStore store before launching the intent. When
 * VideoLiveWallpaperService (and its WallpaperService.Engine) creates or recreates its surface,
 * it synchronously reads the active video URL from this persistent store to load and loop the
 * correct video.
 */
class LiveWallpaperDataStore(private val context: Context) {

    companion object {
        val CURRENT_LIVE_WALLPAPER_URL_KEY = stringPreferencesKey("current_live_wallpaper_url")
    }

    val currentLiveWallpaperUrlFlow: Flow<String?> = context.liveWallpaperDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[CURRENT_LIVE_WALLPAPER_URL_KEY]
        }

    suspend fun getCurrentLiveWallpaperUrl(): String? {
        return try {
            currentLiveWallpaperUrlFlow.first()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun setCurrentLiveWallpaperUrl(url: String) {
        if (url.isBlank()) return
        context.liveWallpaperDataStore.edit { preferences ->
            preferences[CURRENT_LIVE_WALLPAPER_URL_KEY] = url
        }
    }
}
