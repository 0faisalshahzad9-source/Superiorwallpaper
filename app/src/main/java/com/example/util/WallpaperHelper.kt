package com.example.util

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

enum class WallpaperTarget {
    HOME_SCREEN,
    LOCK_SCREEN,
    BOTH
}

object WallpaperHelper {

    /**
     * Downloads an image bitmap using Coil ImageLoader in background thread.
     */
    suspend fun downloadBitmap(context: Context, imageUrl: String): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false) // Hardware bitmaps cannot be drawn directly or saved
                .build()

            val result = (loader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (result as? BitmapDrawable)?.bitmap
                ?: throw IllegalStateException("Could not decode image bitmap from $imageUrl")
            bitmap
        }
    }

    /**
     * Applies bitmap as wallpaper using WallpaperManager.
     * Supports Home Screen, Lock Screen, or Both.
     */
    suspend fun setWallpaper(
        context: Context,
        bitmap: Bitmap,
        target: WallpaperTarget
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val flag = when (target) {
                WallpaperTarget.HOME_SCREEN -> WallpaperManager.FLAG_SYSTEM
                WallpaperTarget.LOCK_SCREEN -> WallpaperManager.FLAG_LOCK
                WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(bitmap, null, true, flag)
            } else {
                wallpaperManager.setBitmap(bitmap)
            }
            Unit
        }
    }

    /**
     * Saves image to device Pictures/SuperiorWallpapers folder using MediaStore API.
     * Compliant with Scoped Storage for Android 10+ (API 29+).
     * Returns the destination path or URI display message.
     */
    suspend fun saveWallpaperToGallery(
        context: Context,
        bitmap: Bitmap,
        wallpaperId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val filename = "SuperiorWallpaper_${wallpaperId}_${System.currentTimeMillis()}.jpg"
            val relativeDir = "Pictures/SuperiorWallpapers"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("Failed to create MediaStore entry")

                resolver.openOutputStream(uri)?.use { stream: OutputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                } ?: throw IllegalStateException("Failed to open output stream for image")

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                "$relativeDir/$filename"
            } else {
                // Legacy storage for Android 9 and lower
                @Suppress("DEPRECATION")
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(picturesDir, "SuperiorWallpapers").apply {
                    if (!exists()) mkdirs()
                }
                val destFile = File(targetDir, filename)
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }

                // Notify media scanner so the photo appears in system gallery
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )

                destFile.absolutePath
            }
        }
    }

    /**
     * Downloads an MP4 video file and saves to Pictures/SuperiorWallpapers using MediaStore.Video.Media.
     * Compliant with Scoped Storage for Android 10+ (API 29+) and legacy fallback.
     */
    suspend fun downloadAndSaveVideo(
        context: Context,
        videoUrl: String,
        wallpaperId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val filename = "SuperiorWallpaper_${wallpaperId}_${System.currentTimeMillis()}.mp4"
            val relativeDir = "Pictures/SuperiorWallpapers"

            val connection = java.net.URL(videoUrl).openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "SuperiorWallpapers/1.0 (Android; Mobile)")
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Server returned HTTP ${connection.responseCode}")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("Failed to create MediaStore video entry")

                resolver.openOutputStream(uri)?.use { outputStream ->
                    connection.inputStream.use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: throw IllegalStateException("Failed to open output stream for video")

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                "$relativeDir/$filename"
            } else {
                @Suppress("DEPRECATION")
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(picturesDir, "SuperiorWallpapers").apply {
                    if (!exists()) mkdirs()
                }
                val destFile = File(targetDir, filename)
                FileOutputStream(destFile).use { outputStream ->
                    connection.inputStream.use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )

                destFile.absolutePath
            }
        }
    }
}
