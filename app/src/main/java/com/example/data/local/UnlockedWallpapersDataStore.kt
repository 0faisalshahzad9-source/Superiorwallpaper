package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "unlocked_wallpapers_prefs")

/**
 * Manages locally unlocked wallpaper IDs via Jetpack DataStore (Preferences).
 * Persists unlocked wallpaper IDs across app restarts without requiring authentication.
 */
class UnlockedWallpapersDataStore(private val context: Context) {

    companion object {
        val UNLOCKED_IDS_KEY = stringSetPreferencesKey("unlocked_wallpaper_ids")
    }

    val unlockedWallpaperIds: Flow<Set<String>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[UNLOCKED_IDS_KEY] ?: emptySet()
        }

    suspend fun unlockWallpaper(wallpaperId: String) {
        if (wallpaperId.isBlank()) return
        context.dataStore.edit { preferences ->
            val current = preferences[UNLOCKED_IDS_KEY] ?: emptySet()
            preferences[UNLOCKED_IDS_KEY] = current + wallpaperId
        }
    }

    suspend fun isWallpaperUnlocked(wallpaperId: String, currentUnlockedIds: Set<String>): Boolean {
        return currentUnlockedIds.contains(wallpaperId)
    }
}
