package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Data model for a wallpaper document from Firestore collection "wallpapers".
 * Supports both static "image" and looping "video" live wallpapers.
 */
data class Wallpaper(
    val id: String = "",
    val mediaType: String = MEDIA_TYPE_IMAGE, // "image" or "video"
    val thumbUrl: String = "",
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val category: String = "",
    val tags: List<String> = emptyList(),
    val isPremium: Boolean = false,
    val downloads: Long = 0L,
    val uploadDate: Timestamp? = null
) {
    val isVideo: Boolean get() = mediaType.equals(MEDIA_TYPE_VIDEO, ignoreCase = true)
    val isLiveWallpaper: Boolean get() = isVideo

    companion object {
        const val MEDIA_TYPE_IMAGE = "image"
        const val MEDIA_TYPE_VIDEO = "video"

        fun fromDocument(doc: DocumentSnapshot): Wallpaper {
            @Suppress("UNCHECKED_CAST")
            val tagsList = (doc.get("tags") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            val rawMediaType = doc.getString("mediaType")?.lowercase() ?: MEDIA_TYPE_IMAGE
            val mediaType = if (rawMediaType == MEDIA_TYPE_VIDEO) MEDIA_TYPE_VIDEO else MEDIA_TYPE_IMAGE
            val thumb = doc.getString("thumbUrl") ?: doc.getString("imageUrl") ?: ""

            return Wallpaper(
                id = doc.id,
                mediaType = mediaType,
                thumbUrl = thumb,
                imageUrl = if (mediaType == MEDIA_TYPE_IMAGE) (doc.getString("imageUrl") ?: thumb) else null,
                videoUrl = if (mediaType == MEDIA_TYPE_VIDEO) doc.getString("videoUrl") else null,
                category = doc.getString("category") ?: "Other",
                tags = tagsList,
                isPremium = doc.getBoolean("isPremium") ?: false,
                downloads = doc.getLong("downloads") ?: 0L,
                uploadDate = doc.getTimestamp("uploadDate")
            )
        }
    }
}
