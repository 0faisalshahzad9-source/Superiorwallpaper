package com.example.service

import android.net.Uri
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.data.local.LiveWallpaperDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android WallpaperService implementation that renders looping video live wallpapers.
 *
 * DATASTORE HAND-OFF:
 * Android's WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER does not pass custom arguments or
 * intent extras to this service. To communicate which video wallpaper the user selected,
 * WallpaperDetailScreen persists the chosen video URL in LiveWallpaperDataStore before launching
 * the system preview intent.
 *
 * This service's Engine observes the DataStore flow; whenever the Engine starts or a new video
 * URL is chosen by the user, ExoPlayer loads and loops that video onto the wallpaper's Surface.
 */
class VideoLiveWallpaperService : WallpaperService() {

    companion object {
        private const val TAG = "VideoLiveWallpaper"

        // Default fallback video URL if no live wallpaper has been selected yet
        const val DEFAULT_LIVE_WALLPAPER_URL =
            "https://test-videos.co.uk/vids/jellyfish/mp4/h264/720/Jellyfish_720_10s_1MB.mp4"
    }

    override fun onCreateEngine(): Engine {
        return VideoEngine()
    }

    inner class VideoEngine : WallpaperService.Engine() {

        private var player: ExoPlayer? = null
        private var engineScope: CoroutineScope? = null
        private var currentVideoUrl: String? = null
        private var isVisibleState = false
        private val dataStore = LiveWallpaperDataStore(applicationContext)

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            initPlayer(surfaceHolder)
            observeWallpaperUrl()
        }

        private fun initPlayer(surfaceHolder: SurfaceHolder) {
            try {
                if (player == null) {
                    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                        .setUserAgent("SuperiorWallpapers/1.0 (Android; Mobile)")
                        .setConnectTimeoutMs(15000)
                        .setReadTimeoutMs(20000)
                        .setAllowCrossProtocolRedirects(true)
                    val dataSourceFactory = DefaultDataSource.Factory(applicationContext, httpDataSourceFactory)
                    val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

                    player = ExoPlayer.Builder(applicationContext)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .build()
                        .apply {
                            setVideoSurfaceHolder(surfaceHolder)
                            repeatMode = Player.REPEAT_MODE_ONE
                            volume = 0f // Live wallpapers must always be completely silent
                            addListener(object : Player.Listener {
                                override fun onPlayerError(error: PlaybackException) {
                                    Log.w(TAG, "Live wallpaper playback error: ${error.message}", error)
                                }
                            })
                        }
                } else {
                    player?.setVideoSurfaceHolder(surfaceHolder)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize ExoPlayer for live wallpaper: ${e.message}", e)
            }
        }

        private fun observeWallpaperUrl() {
            engineScope?.launch {
                dataStore.currentLiveWallpaperUrlFlow.collectLatest { storedUrl ->
                    val targetUrl = storedUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_LIVE_WALLPAPER_URL
                    if (targetUrl != currentVideoUrl) {
                        currentVideoUrl = targetUrl
                        loadAndPlayVideo(targetUrl)
                    }
                }
            }
        }

        private fun loadAndPlayVideo(url: String) {
            val exo = player ?: return
            try {
                Log.d(TAG, "Loading live wallpaper video from: $url")
                val mediaItem = MediaItem.fromUri(Uri.parse(url))
                exo.setMediaItem(mediaItem)
                exo.prepare()
                if (isVisibleState) {
                    exo.play()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading live wallpaper video: ${e.message}", e)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            Log.d(TAG, "onSurfaceCreated: binding SurfaceHolder to player")
            player?.setVideoSurfaceHolder(holder)
            if (isVisibleState) {
                player?.play()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            player?.setVideoSurfaceHolder(holder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            Log.d(TAG, "onSurfaceDestroyed: clearing surface from player")
            player?.clearVideoSurfaceHolder(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisibleState = visible
            val exo = player ?: return
            if (visible) {
                // Resume playback when wallpaper is visible to user
                exo.play()
            } else {
                // Pause playback when obscured to save device battery and CPU
                exo.pause()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            Log.d(TAG, "VideoEngine onDestroy: releasing player and coroutine scope")
            try {
                engineScope?.cancel()
                engineScope = null
                player?.stop()
                player?.release()
                player = null
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing ExoPlayer in onDestroy: ${e.message}")
            }
        }
    }
}
