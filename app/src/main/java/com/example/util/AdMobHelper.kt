package com.example.util

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AdMobConfig {
    /**
     * Google AdMob Production Banner Ad Unit ID.
     */
    const val BANNER_AD_UNIT_ID = "ca-app-pub-2467537610768055/6135111547"

    /**
     * Google AdMob Production Rewarded Ad Unit ID.
     */
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-2467537610768055/8148610373"

    @Volatile
    private var isInitialized = false

    fun initialize(context: Context) {
        if (!isInitialized) {
            isInitialized = true
            try {
                // Ensure WebView and Chromium cache folders exist before Chromium initialization
                context.cacheDir?.let { cache ->
                    java.io.File(cache, "WebView/Default/HTTP Cache/index-dir").mkdirs()
                    java.io.File(cache, "WebView/Default/HTTP Cache/Code Cache/js").mkdirs()
                    java.io.File(cache, "WebView/Default/HTTP Cache/Code Cache/wasm").mkdirs()
                    java.io.File(cache, "app_webview").mkdirs()
                }
            } catch (e: Exception) {
                Log.w("AdMobConfig", "Cache directory preparation note: ${e.message}")
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Configure emulator test device ID for safe ad serving
                    val configuration = com.google.android.gms.ads.RequestConfiguration.Builder()
                        .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                        .build()
                    MobileAds.setRequestConfiguration(configuration)

                    MobileAds.initialize(context.applicationContext) { status ->
                        Log.d("AdMobConfig", "AdMob initialized successfully: ${status.adapterStatusMap}")
                    }
                } catch (e: Exception) {
                    Log.w("AdMobConfig", "Failed to initialize MobileAds: ${e.message}")
                }
            }
        }
    }
}

/**
 * Manager for loading and showing Rewarded Ads for premium wallpaper unlocks.
 */
class RewardedAdManager(private val context: Context) {

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    init {
        AdMobConfig.initialize(context)
        preloadAd()
    }

    fun preloadAd() {
        if (isLoading || rewardedAd != null) return
        isLoading = true

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            AdMobConfig.REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading = false
                    Log.d("RewardedAdManager", "Rewarded ad loaded successfully.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isLoading = false
                    Log.w("RewardedAdManager", "Rewarded ad failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Shows the rewarded ad if ready. If not ready yet, attempts to load and show, or triggers fallback.
     */
    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onAdFailedToShow: (String) -> Unit
    ) {
        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    preloadAd() // Preload the next ad
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    Log.e("RewardedAdManager", "Ad failed to show: ${adError.message}")
                    onAdFailedToShow(adError.message)
                    preloadAd()
                }
            }

            ad.show(activity) { rewardItem ->
                Log.d("RewardedAdManager", "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onRewardEarned()
            }
        } else {
            // Ad wasn't preloaded yet; attempt one quick load or offer graceful unlock
            Log.w("RewardedAdManager", "Ad not ready yet, loading on-demand...")
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(
                activity,
                AdMobConfig.REWARDED_AD_UNIT_ID,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(newAd: RewardedAd) {
                        rewardedAd = newAd
                        newAd.show(activity) {
                            onRewardEarned()
                        }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w("RewardedAdManager", "On-demand ad load failed (code=${error.code}): ${error.message}")
                        onAdFailedToShow(
                            if (error.code == AdRequest.ERROR_CODE_NO_FILL) {
                                "No ad inventory available right now"
                            } else {
                                "Ad unavailable (${error.message})"
                            }
                        )
                    }
                }
            )
        }
    }
}

/**
 * Composable Banner Ad pinned to the screen.
 */
@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = AdMobConfig.BANNER_AD_UNIT_ID
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                AdMobConfig.initialize(context)
                AdView(context).apply {
                    // Use software layer type to avoid DRM rendernode graphics driver issues in emulated environments
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    setAdSize(AdSize.BANNER)
                    setAdUnitId(adUnitId)
                    adListener = object : AdListener() {
                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            Log.w("AdMobBanner", "Banner ad failed to load: ${loadAdError.message}")
                        }
                    }
                    post {
                        try {
                            loadAd(AdRequest.Builder().build())
                        } catch (e: Exception) {
                            Log.w("AdMobBanner", "Failed to request banner: ${e.message}")
                        }
                    }
                }
            },
            onRelease = { adView ->
                adView.destroy()
            }
        )
    }
}
