package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.model.Wallpaper
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.WallpaperDetailScreen
import com.example.ui.theme.SuperiorWallpapersTheme
import com.example.ui.viewmodel.WallpapersViewModel
import com.example.util.AdMobConfig

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data class Detail(val wallpaper: Wallpaper) : Screen
}

class MainActivity : ComponentActivity() {

    private val viewModel: WallpapersViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Google Mobile Ads (AdMob)
        AdMobConfig.initialize(this)

        setContent {
            SuperiorWallpapersTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SuperiorWallpapersApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun SuperiorWallpapersApp(viewModel: WallpapersViewModel) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    BackHandler(enabled = currentScreen !is Screen.Home) {
        currentScreen = Screen.Home
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ScreenTransition"
    ) { screen ->
        when (screen) {
            is Screen.Home -> {
                HomeScreen(
                    viewModel = viewModel,
                    onWallpaperClick = { wallpaper ->
                        currentScreen = Screen.Detail(wallpaper)
                    },
                    onNavigateToSearch = {
                        currentScreen = Screen.Search
                    }
                )
            }

            is Screen.Search -> {
                SearchScreen(
                    viewModel = viewModel,
                    onWallpaperClick = { wallpaper ->
                        currentScreen = Screen.Detail(wallpaper)
                    },
                    onBack = {
                        currentScreen = Screen.Home
                    }
                )
            }

            is Screen.Detail -> {
                WallpaperDetailScreen(
                    wallpaper = screen.wallpaper,
                    viewModel = viewModel,
                    onBack = {
                        currentScreen = Screen.Home
                    }
                )
            }
        }
    }
}
