package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.local.LiveWallpaperDataStore
import com.example.data.model.Wallpaper
import com.example.service.VideoLiveWallpaperService
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PremiumGold
import com.example.ui.theme.SuperiorPink
import com.example.ui.theme.SuperiorPurple
import com.example.ui.viewmodel.WallpapersViewModel
import com.example.util.RewardedAdManager
import com.example.util.WallpaperHelper
import com.example.util.WallpaperTarget
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun WallpaperDetailScreen(
    wallpaper: Wallpaper,
    viewModel: WallpapersViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val unlockedIds by viewModel.unlockedWallpaperIds.collectAsState()
    val isUnlocked = !wallpaper.isPremium || unlockedIds.contains(wallpaper.id)

    // Rewarded ad manager
    val rewardedAdManager = remember { RewardedAdManager(context) }

    // Bottom sheet state for "Set Wallpaper"
    var showSetWallpaperSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Operation progress states
    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }

    // Pinch-to-zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Video Live Wallpaper playback state
    var isMuted by remember { mutableStateOf(true) }
    var isVideoReady by remember { mutableStateOf(false) }
    var isVideoError by remember { mutableStateOf(false) }

    val exoPlayer = remember(context, wallpaper.videoUrl) {
        val url = wallpaper.videoUrl
        if (wallpaper.isVideo && !url.isNullOrBlank()) {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("SuperiorWallpapers/1.0 (Android; Mobile)")
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setAllowCrossProtocolRedirects(true)
            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                    repeatMode = Player.REPEAT_MODE_ONE
                    volume = 0f
                    prepare()
                    playWhenReady = true
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                isVideoReady = true
                                isVideoError = false
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            android.util.Log.w("WallpaperDetailScreen", "Live wallpaper preview error: ${error.message}", error)
                            isVideoError = true
                        }
                    })
                }
        } else null
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.stop()
            exoPlayer?.release()
        }
    }

    // Permission launcher for Android 9/10 WRITE_EXTERNAL_STORAGE
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch {
                if (wallpaper.isVideo) {
                    executeDownloadVideo(
                        wallpaper = wallpaper,
                        context = context,
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        setProcessing = { isProcessing = it },
                        setMessage = { processingMessage = it }
                    )
                } else {
                    executeDownload(
                        wallpaper = wallpaper,
                        context = context,
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        setProcessing = { isProcessing = it },
                        setMessage = { processingMessage = it }
                    )
                }
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Storage permission is needed on your Android version to save to Gallery."
                )
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (wallpaper.isVideo && exoPlayer != null) {
                // Live Video Preview with ExoPlayer
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Show thumbnail while video is loading or if video playback encounters an issue
                    if (!isVideoReady || isVideoError) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(wallpaper.thumbUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Loading live wallpaper preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = SuperiorPurple,
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                        )
                    }
                }
            } else {
                // Full-screen Image Preview with pinch-to-zoom support
                val imageUrl = wallpaper.imageUrl?.ifBlank { wallpaper.thumbUrl } ?: wallpaper.thumbUrl
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Full-screen wallpaper preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            ),
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = SuperiorPurple,
                                    strokeWidth = 3.dp
                                )
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.BrokenImage,
                                        contentDescription = "Failed to load high-resolution image",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(54.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Failed to load full image",
                                        color = Color.LightGray,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    )
                }
            }

            // Top gradient scrim with Back button and category info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    // Category Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SuperiorPurple.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = wallpaper.category,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    // Mute toggle for video wallpaper or Reset zoom button for image
                    if (wallpaper.isVideo && exoPlayer != null) {
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else 1f
                            },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("mute_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (isMuted) "Unmute Video" else "Mute Video",
                                tint = Color.White
                            )
                        }
                    } else if (scale > 1.05f) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            color = Color.Transparent
                        ) {
                            Text(
                                text = "1x Reset",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                }
            }

            // Bottom gradient scrim with actions and wallpaper details
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Wallpaper info row: Tags and downloads counter
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Tags scrollable row
                        if (wallpaper.tags.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                wallpaper.tags.take(4).forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.85f)
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Downloads Counter Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "Downloads",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${wallpaper.downloads}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Action Buttons (Unlock logic)
                    if (!isUnlocked) {
                        // Premium Locked State: Show "Watch Ad to Unlock" button
                        Button(
                            onClick = {
                                val activity = context as? Activity
                                if (activity != null) {
                                    isProcessing = true
                                    processingMessage = "Loading rewarded ad..."
                                    rewardedAdManager.showRewardedAd(
                                        activity = activity,
                                        onRewardEarned = {
                                            isProcessing = false
                                            viewModel.unlockWallpaper(wallpaper.id)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Wallpaper unlocked! Enjoy your high-res wallpaper.")
                                            }
                                        },
                                        onAdFailedToShow = { error ->
                                            isProcessing = false
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Ad failed: $error. Unlocking for free!")
                                                viewModel.unlockWallpaper(wallpaper.id)
                                            }
                                        }
                                    )
                                } else {
                                    viewModel.unlockWallpaper(wallpaper.id)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .testTag("watch_ad_unlock_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PremiumGold,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Watch Ad to Unlock Premium",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp
                                )
                            )
                        }
                    } else {
                        // Unlocked or Free State: Show "Set Live Wallpaper" (if video) or "Set Wallpaper" (if image), and "Download" buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (wallpaper.isVideo) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val videoUrl = wallpaper.videoUrl
                                            if (!videoUrl.isNullOrBlank()) {
                                                val dataStore = LiveWallpaperDataStore(context)
                                                dataStore.setCurrentLiveWallpaperUrl(videoUrl)
                                                viewModel.recordDownloadOrSet(wallpaper.id)

                                                try {
                                                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                                        putExtra(
                                                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                                            ComponentName(context, VideoLiveWallpaperService::class.java)
                                                        )
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    try {
                                                        val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(fallbackIntent)
                                                    } catch (fallbackErr: Exception) {
                                                        snackbarHostState.showSnackbar("Could not launch live wallpaper chooser: ${e.message}")
                                                    }
                                                }
                                            } else {
                                                snackbarHostState.showSnackbar("No video URL found for this live wallpaper")
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("set_live_wallpaper_button"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SuperiorPurple,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Wallpaper,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Set Live Wallpaper",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { showSetWallpaperSheet = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("set_wallpaper_button"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SuperiorPurple,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Wallpaper,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Set Wallpaper",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    // Check permission for Android 9/10 or save via MediaStore for Android 11+
                                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    } else {
                                        scope.launch {
                                            if (wallpaper.isVideo) {
                                                executeDownloadVideo(
                                                    wallpaper = wallpaper,
                                                    context = context,
                                                    viewModel = viewModel,
                                                    snackbarHostState = snackbarHostState,
                                                    setProcessing = { isProcessing = it },
                                                    setMessage = { processingMessage = it }
                                                )
                                            } else {
                                                executeDownload(
                                                    wallpaper = wallpaper,
                                                    context = context,
                                                    viewModel = viewModel,
                                                    snackbarHostState = snackbarHostState,
                                                    setProcessing = { isProcessing = it },
                                                    setMessage = { processingMessage = it }
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("download_wallpaper_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, SuperiorPink)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = SuperiorPink,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Download",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = SuperiorPink
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Loading overlay indicator
            AnimatedVisibility(
                visible = isProcessing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = SuperiorPurple,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = processingMessage.ifBlank { "Processing..." },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Set Wallpaper Bottom Sheet with 3 Options
        if (showSetWallpaperSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSetWallpaperSheet = false },
                sheetState = sheetState,
                containerColor = DarkSurface,
                scrimColor = Color.Black.copy(alpha = 0.6f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "Set as Wallpaper",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = "Choose where to apply this wallpaper:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    SetOptionItem(
                        icon = Icons.Default.Smartphone,
                        title = "Home Screen",
                        subtitle = "Apply wallpaper to your main screen",
                        onClick = {
                            showSetWallpaperSheet = false
                            scope.launch {
                                executeSetWallpaper(
                                    wallpaper = wallpaper,
                                    target = WallpaperTarget.HOME_SCREEN,
                                    context = context,
                                    viewModel = viewModel,
                                    snackbarHostState = snackbarHostState,
                                    setProcessing = { isProcessing = it },
                                    setMessage = { processingMessage = it }
                                )
                            }
                        }
                    )

                    HorizontalDivider(color = DarkCardBorder, modifier = Modifier.padding(vertical = 4.dp))

                    SetOptionItem(
                        icon = Icons.Default.ScreenLockPortrait,
                        title = "Lock Screen",
                        subtitle = "Apply wallpaper to your lock screen",
                        onClick = {
                            showSetWallpaperSheet = false
                            scope.launch {
                                executeSetWallpaper(
                                    wallpaper = wallpaper,
                                    target = WallpaperTarget.LOCK_SCREEN,
                                    context = context,
                                    viewModel = viewModel,
                                    snackbarHostState = snackbarHostState,
                                    setProcessing = { isProcessing = it },
                                    setMessage = { processingMessage = it }
                                )
                            }
                        }
                    )

                    HorizontalDivider(color = DarkCardBorder, modifier = Modifier.padding(vertical = 4.dp))

                    SetOptionItem(
                        icon = Icons.Default.PhoneAndroid,
                        title = "Both (Home & Lock Screen)",
                        subtitle = "Apply wallpaper across your entire device",
                        onClick = {
                            showSetWallpaperSheet = false
                            scope.launch {
                                executeSetWallpaper(
                                    wallpaper = wallpaper,
                                    target = WallpaperTarget.BOTH,
                                    context = context,
                                    viewModel = viewModel,
                                    snackbarHostState = snackbarHostState,
                                    setProcessing = { isProcessing = it },
                                    setMessage = { processingMessage = it }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SetOptionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(DarkSurfaceVariant.copy(alpha = 0.5f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = SuperiorPurple.copy(alpha = 0.2f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SuperiorPurple,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )
        }
    }
}

private suspend fun executeSetWallpaper(
    wallpaper: Wallpaper,
    target: WallpaperTarget,
    context: android.content.Context,
    viewModel: WallpapersViewModel,
    snackbarHostState: SnackbarHostState,
    setProcessing: (Boolean) -> Unit,
    setMessage: (String) -> Unit
) {
    setProcessing(true)
    setMessage("Downloading high-resolution image...")

    val bitmapResult = WallpaperHelper.downloadBitmap(
        context = context,
        imageUrl = wallpaper.imageUrl?.ifBlank { wallpaper.thumbUrl } ?: wallpaper.thumbUrl
    )

    bitmapResult.fold(
        onSuccess = { bitmap ->
            setMessage("Applying wallpaper to device...")
            val setResult = WallpaperHelper.setWallpaper(context, bitmap, target)
            setProcessing(false)
            setResult.fold(
                onSuccess = {
                    viewModel.recordDownloadOrSet(wallpaper.id)
                    val targetName = when (target) {
                        WallpaperTarget.HOME_SCREEN -> "Home Screen"
                        WallpaperTarget.LOCK_SCREEN -> "Lock Screen"
                        WallpaperTarget.BOTH -> "Home & Lock Screen"
                    }
                    snackbarHostState.showSnackbar("Successfully set as $targetName wallpaper!")
                },
                onFailure = { err ->
                    snackbarHostState.showSnackbar("Failed to set wallpaper: ${err.message}")
                }
            )
        },
        onFailure = { err ->
            setProcessing(false)
            snackbarHostState.showSnackbar("Failed to download image: ${err.message}")
        }
    )
}

private suspend fun executeDownload(
    wallpaper: Wallpaper,
    context: android.content.Context,
    viewModel: WallpapersViewModel,
    snackbarHostState: SnackbarHostState,
    setProcessing: (Boolean) -> Unit,
    setMessage: (String) -> Unit
) {
    setProcessing(true)
    setMessage("Downloading full-resolution image...")

    val bitmapResult = WallpaperHelper.downloadBitmap(
        context = context,
        imageUrl = wallpaper.imageUrl?.ifBlank { wallpaper.thumbUrl } ?: wallpaper.thumbUrl
    )

    bitmapResult.fold(
        onSuccess = { bitmap ->
            setMessage("Saving to Pictures/SuperiorWallpapers...")
            val saveResult = WallpaperHelper.saveWallpaperToGallery(context, bitmap, wallpaper.id)
            setProcessing(false)
            saveResult.fold(
                onSuccess = { path ->
                    viewModel.recordDownloadOrSet(wallpaper.id)
                    snackbarHostState.showSnackbar("Wallpaper saved successfully to $path")
                },
                onFailure = { err ->
                    snackbarHostState.showSnackbar("Failed to save wallpaper: ${err.message}")
                }
            )
        },
        onFailure = { err ->
            setProcessing(false)
            snackbarHostState.showSnackbar("Failed to download image: ${err.message}")
        }
    )
}

private suspend fun executeDownloadVideo(
    wallpaper: Wallpaper,
    context: android.content.Context,
    viewModel: WallpapersViewModel,
    snackbarHostState: SnackbarHostState,
    setProcessing: (Boolean) -> Unit,
    setMessage: (String) -> Unit
) {
    val videoUrl = wallpaper.videoUrl
    if (videoUrl.isNullOrBlank()) {
        snackbarHostState.showSnackbar("No video URL available for this live wallpaper")
        return
    }

    setProcessing(true)
    setMessage("Downloading live wallpaper video...")

    val downloadResult = WallpaperHelper.downloadAndSaveVideo(
        context = context,
        videoUrl = videoUrl,
        wallpaperId = wallpaper.id
    )

    setProcessing(false)
    downloadResult.fold(
        onSuccess = { path ->
            viewModel.recordDownloadOrSet(wallpaper.id)
            snackbarHostState.showSnackbar("Live wallpaper saved successfully to $path")
        },
        onFailure = { err ->
            snackbarHostState.showSnackbar("Failed to save video: ${err.message}")
        }
    )
}
