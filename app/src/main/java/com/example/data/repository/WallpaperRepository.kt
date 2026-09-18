package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.Wallpaper
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Repository handling wallpaper queries against the Firestore "wallpapers" collection.
 *
 * NOTE ON FIREBASE SETUP:
 * Ensure 'google-services.json' is placed at: app/google-services.json
 * And that Google services plugin is applied in app/build.gradle.kts.
 */
class WallpaperRepository(private val context: Context) {

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseFirestore.getInstance()
            } else {
                Log.w("WallpaperRepository", "Firebase not initialized yet (google-services.json pending).")
                null
            }
        } catch (e: Exception) {
            Log.w("WallpaperRepository", "Failed to get Firestore instance: ${e.message}")
            null
        }
    }

    private val collectionName = "wallpapers"

    /**
     * Fetches a paginated list of wallpapers filtered by category.
     */
    suspend fun getWallpapers(
        category: String?,
        lastSnapshot: DocumentSnapshot?,
        pageSize: Long = 20
    ): Result<Pair<List<Wallpaper>, DocumentSnapshot?>> = runCatching {
        val db = firestore
        if (db == null) {
            // Provide curated sample wallpapers when Firebase google-services.json is pending
            return@runCatching getSampleWallpapers(category, lastSnapshot, pageSize)
        }

        try {
            var query: Query = db.collection(collectionName)

            if (!category.isNullOrBlank() && !category.equals("All", ignoreCase = true)) {
                query = query.whereEqualTo("category", category)
            }

            // Order by uploadDate descending if possible, or fallback to default
            query = query.orderBy("uploadDate", Query.Direction.DESCENDING)

            if (lastSnapshot != null) {
                query = query.startAfter(lastSnapshot)
            }

            query = query.limit(pageSize)

            val snapshot = query.get().await()
            val wallpapers = snapshot.documents.map { Wallpaper.fromDocument(it) }
            val nextLastSnapshot = snapshot.documents.lastOrNull()

            Pair(wallpapers, nextLastSnapshot)
        } catch (e: Exception) {
            Log.e("WallpaperRepository", "Firestore query failed, using fallback data: ${e.message}")
            getSampleWallpapers(category, lastSnapshot, pageSize)
        }
    }

    /**
     * Searches wallpapers matching against tags and category.
     * Uses lowercase tags array matching or client-side filtering over fetched documents.
     */
    suspend fun searchWallpapers(queryText: String): Result<List<Wallpaper>> = runCatching {
        val cleanQuery = queryText.trim().lowercase()
        if (cleanQuery.isEmpty()) return@runCatching emptyList()

        val db = firestore
        if (db == null) {
            return@runCatching sampleWallpapersList.filter { wp ->
                wp.category.lowercase().contains(cleanQuery) ||
                wp.tags.any { it.lowercase().contains(cleanQuery) }
            }
        }

        try {
            // Try array-contains query for tag match first
            val tagMatchQuery = db.collection(collectionName)
                .whereArrayContains("tags", cleanQuery)
                .limit(40)
                .get()
                .await()

            val tagResults = tagMatchQuery.documents.map { Wallpaper.fromDocument(it) }

            // If no tag match or short list, also check category match
            val categoryQuery = db.collection(collectionName)
                .whereEqualTo("category", queryText.trim())
                .limit(40)
                .get()
                .await()

            val categoryResults = categoryQuery.documents.map { Wallpaper.fromDocument(it) }

            // Combine and distinct by ID
            val combined = (tagResults + categoryResults).distinctBy { it.id }
            if (combined.isNotEmpty()) {
                return@runCatching combined
            }

            // Client-side fallback search across top recent wallpapers
            val recentQuery = db.collection(collectionName)
                .orderBy("uploadDate", Query.Direction.DESCENDING)
                .limit(60)
                .get()
                .await()

            recentQuery.documents
                .map { Wallpaper.fromDocument(it) }
                .filter { wp ->
                    wp.category.lowercase().contains(cleanQuery) ||
                    wp.tags.any { it.lowercase().contains(cleanQuery) }
                }
        } catch (e: Exception) {
            Log.w("WallpaperRepository", "Search query error, using sample search: ${e.message}")
            sampleWallpapersList.filter { wp ->
                wp.category.lowercase().contains(cleanQuery) ||
                wp.tags.any { it.lowercase().contains(cleanQuery) }
            }
        }
    }

    /**
     * Increments the wallpaper's "downloads" counter by 1.
     * Read-only access rule: this is the only write operation permitted in Firestore.
     */
    suspend fun incrementDownloads(wallpaperId: String) {
        if (wallpaperId.isBlank()) return
        val db = firestore ?: return
        try {
            db.collection(collectionName).document(wallpaperId)
                .update("downloads", FieldValue.increment(1))
                .await()
            Log.d("WallpaperRepository", "Successfully incremented downloads for $wallpaperId")
        } catch (e: Exception) {
            Log.w("WallpaperRepository", "Failed to increment downloads for $wallpaperId: ${e.message}")
        }
    }

    private fun getSampleWallpapers(
        category: String?,
        lastSnapshot: DocumentSnapshot?,
        pageSize: Long
    ): Pair<List<Wallpaper>, DocumentSnapshot?> {
        val filtered = if (category.isNullOrBlank() || category.equals("All", ignoreCase = true)) {
            sampleWallpapersList
        } else {
            sampleWallpapersList.filter { it.category.equals(category, ignoreCase = true) }
        }
        return Pair(filtered.take(pageSize.toInt()), null)
    }

    companion object {
        // High-quality sample wallpapers covering all required categories:
        // Gaming, GTA 6, Anime, Nature, Abstract, Cars, Sports, Space, Dark, Minimal, 4K, Other
        val sampleWallpapersList = listOf(
            Wallpaper(
                id = "wp_gta6_01",
                imageUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?q=80&w=1280&auto=format&fit=crop",
                thumbUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?q=80&w=400&auto=format&fit=crop",
                category = "GTA 6",
                tags = listOf("gta", "gta 6", "vice city", "neon", "sunset", "palm", "gaming"),
                isPremium = true,
                downloads = 1420L
            ),
            Wallpaper(
                id = "wp_gaming_01",
                imageUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?q=80&w=1280&auto=format&fit=crop",
                thumbUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?q=80&w=400&auto=format&fit=crop",
                category = "Gaming",
                tags = listOf("gaming", "esports", "cyberpunk", "setup", "rgb", "neon"),
                isPremium = false,
                downloads = 980L
            ),
            Wallpaper(
                id = "wp_anime_01",
                imageUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?q=80&w=1280&auto=format&fit=crop",
                thumbUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?q=80&w=400&auto=format&fit=crop",
                category = "Anime",
                tags = listOf("anime", "illustration", "fantasy", "scenery", "clouds"),
                isPremium = true,
                downloads = 2105L
            ),
            Wallpaper(
                id = "wp_nature_01",
                imageUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?q=80&w=1280&auto=format&fit=crop",
                thumbUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?q=80&w=400&auto=format&fit=crop",
                category = "Nature",
                tags = listOf("nature", "mountains", "lake", "reflection", "scenic", "landscape"),
                isPremium = false,
                downloads = 3450L
            ),
            Wallpaper(
                id = "wp_abstract_01",
                imageUrl = "https://images.unsplash.com/photo-1541701494587-cb58502866ab?q=80&w=1280&auto=format&fit=crop",
                thumbUrl = "https://images.unsplash.com/photo-1541701494587-cb58502866ab?q=80&w=400&auto=format&fit=crop",
                category = "Abstract",
                tags = listOf("abstract", "liquid", "colors", "fluid", "art", "modern"),
                isPremium = false,
                downloads = 875L
            ),
            Wallpaper(
                id = "wp_cars_01",
                imageUrl = "https://images.unsplash.com/photo-1617814076367-b759c7d7e738?q=80&w=1280&auto=format&fit=crop",
                thumbUrl = "https://images.unsplash.com/photo-1617814076367-b759c7d7e738?q=80&w=400&auto=format&fit=crop",
                category = "Cars",
                tags = listOf("cars", "supercar", "speed", "dark", "automotive"),
                isPremium = true,
                downloads = 1890L
            ),
            Wallpaper(
                id = "wp_space_01",
                imageUrl = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?q=80&w=1280&auto=format&fit=crop",
                thumbUrl = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?q=80&w=400&auto=format&fit=crop",
                category = "Space",
                tags = listOf("space", "galaxy", "stars", "nebula", "cosmos", "astronomy"),
                isPremium = false,
                downloads = 4120L
            ),
            Wallpaper(
                id = "wp_dark_01",
                imageUrl = "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?q=80&w=1280&auto=format&fit=crop",
                thumbUrl = "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?q=80&w=400&auto=format&fit=crop",
                category = "Dark",
                tags = listOf("dark", "amoled", "black", "neon", "minimalist"),
                isPremium = false,
                downloads = 5300L
            ),
            Wallpaper(
                id = "wp_minimal_01",
                imageUrl = "https://images.unsplash.com/photo-1494526585095-c41746248156?q=80&w=1280&auto=format&fit=crop",
                thumbUrl = "https://images.unsplash.com/photo-1494526585095-c41746248156?q=80&w=400&auto=format&fit=crop",
                category = "Minimal",
                tags = listOf("minimal", "clean", "architecture", "geometry", "lines"),
                isPremium = false,
                downloads = 1240L
            ),
            Wallpaper(
                id = "wp_4k_01",
                imageUrl = "https://images.unsplash.com/photo-1518495973542-4542c06a5843?q=80&w=1280&auto=format&fit=crop",
                thumbUrl = "https://images.unsplash.com/photo-1518495973542-4542c06a5843?q=80&w=400&auto=format&fit=crop",
                category = "4K",
                tags = listOf("4k", "ultra hd", "texture", "nature", "forest", "detail"),
                isPremium = true,
                downloads = 2890L
            ),
            Wallpaper(
                id = "wp_sports_01",
                imageUrl = "https://images.unsplash.com/photo-1517649763962-0c623266ddc0?q=80&w=1280&auto=format&fit=crop",
                thumbUrl = "https://images.unsplash.com/photo-1517649763962-0c623266ddc0?q=80&w=400&auto=format&fit=crop",
                category = "Sports",
                tags = listOf("sports", "stadium", "lights", "action", "arena"),
                isPremium = false,
                downloads = 930L
            ),
            Wallpaper(
                id = "wp_other_01",
                imageUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?q=80&w=1280&auto=format&fit=crop",
                thumbUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?q=80&w=400&auto=format&fit=crop",
                category = "Other",
                tags = listOf("aurora", "northern lights", "night", "sky"),
                isPremium = true,
                downloads = 1760L
            ),
            // Live Video Wallpapers
            Wallpaper(
                id = "wp_live_gaming_01",
                mediaType = Wallpaper.MEDIA_TYPE_VIDEO,
                thumbUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?q=80&w=400&auto=format&fit=crop",
                videoUrl = "https://test-videos.co.uk/vids/jellyfish/mp4/h264/720/Jellyfish_720_10s_1MB.mp4",
                category = "Gaming",
                tags = listOf("live", "city", "neon", "cyberpunk", "gaming", "night"),
                isPremium = false,
                downloads = 3210L
            ),
            Wallpaper(
                id = "wp_live_nature_02",
                mediaType = Wallpaper.MEDIA_TYPE_VIDEO,
                thumbUrl = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?q=80&w=400&auto=format&fit=crop",
                videoUrl = "https://test-videos.co.uk/vids/sintel/mp4/h264/720/Sintel_720_10s_1MB.mp4",
                category = "Nature",
                tags = listOf("live", "nature", "mountains", "clouds", "landscape"),
                isPremium = true,
                downloads = 4890L
            ),
            Wallpaper(
                id = "wp_live_cars_03",
                mediaType = Wallpaper.MEDIA_TYPE_VIDEO,
                thumbUrl = "https://images.unsplash.com/photo-1503376780353-7e6692767b70?q=80&w=400&auto=format&fit=crop",
                videoUrl = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4",
                category = "Cars",
                tags = listOf("live", "cars", "speed", "drift", "supercar"),
                isPremium = false,
                downloads = 2650L
            )
        )
    }
}
