# Superior Wallpapers

A wallpaper gallery Android application built with **Kotlin** and **Jetpack Compose** (Material 3).

---

## Features

- **Firestore Gallery**: Real-time wallpapers collection from Firebase Firestore with category filtering (`All`, `Gaming`, `GTA 6`, `Anime`, `Nature`, `Abstract`, `Cars`, `Sports`, `Space`, `Dark`, `Minimal`, `4K`, `Other`).
- **Paging & Performance**: 2-column grid layout using `LazyVerticalGrid` with pagination cursors (`limit(20)` and `startAfter()`). Low-bandwidth thumbnails (`thumbUrl`) are loaded in grid views and full-resolution images (`imageUrl`) in preview/download.
- **Search Screen**: Case-insensitive search matching against `tags` array and `category` field with quick suggestion pills and clean empty/error states.
- **Full-Screen Preview**: High-resolution wallpaper preview with interactive pinch-to-zoom and pan gestures.
- **Set Wallpaper**: Bottom sheet dialog with 3 options:
  1. Home Screen (`FLAG_SYSTEM`)
  2. Lock Screen (`FLAG_LOCK`)
  3. Both (`FLAG_SYSTEM or FLAG_LOCK`)
- **Download to Gallery**: Scoped-storage compliant image downloader that saves wallpapers to `Pictures/SuperiorWallpapers` using Android MediaStore API (Android 10+) and runtime permission handling (Android 9 and below).
- **Download Counting**: Increments wallpaper `downloads` count in Firestore using `FieldValue.increment(1)` on every set or download event.
- **Premium Unlock & AdMob Rewarded Ads**:
  - Premium wallpapers display a `PREMIUM` badge with lock overlay.
  - Locked wallpapers feature a **"Watch Ad to Unlock"** button that plays a Google AdMob Rewarded Ad.
  - On reward earned, the wallpaper document ID is stored locally in Jetpack DataStore (Preferences) and permanently unlocked on that device.
- **Banner Ads**: Pinned AdMob Banner ad at the bottom of the Home screen.
- **Dark Theme**: Minimal, dark-first UI aesthetic with purple (`#6D5BFF`) to pink (`#FF4D8D`) gradients.

---

## Configuration & Setup

### 1. Firebase Configuration (`google-services.json`)
Place your Firebase project's `google-services.json` file into the `app/` directory:
```
app/google-services.json
```
The Google Services Gradle plugin is already applied in `app/build.gradle.kts`:
```kotlin
alias(libs.plugins.google.services)
```
*Note: The app contains graceful offline fallback sample data so it can be previewed or tested even before `google-services.json` is supplied.*

### 2. Google AdMob IDs (Production Ready)

#### A. AdMob App ID
The production AdMob App ID is already configured in `app/src/main/AndroidManifest.xml`:
```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-2467537610768055~5956727157" />
```

#### B. Production Ad Unit IDs
`app/src/main/java/com/example/util/AdMobHelper.kt` contains the production Ad Unit IDs configured directly:

```kotlin
object AdMobConfig {
    // Production Banner Ad Unit ID (Home screen bottom):
    const val BANNER_AD_UNIT_ID = "ca-app-pub-2467537610768055/6135111547"

    // Production Rewarded Ad Unit ID (Watch Ad to Unlock):
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-2467537610768055/8148610373"
}
```

---

## Building via GitHub Actions or Local CLI

This project uses the standard Android Gradle setup.

### Debug Build
```bash
./gradlew assembleDebug
```

### Release APK / Bundle Build
```bash
./gradlew assembleRelease
# Or for Play Store App Bundle:
./gradlew bundleRelease
```

### CI / GitHub Actions Notes
- Set `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` secrets in your repository settings if signing release artifacts.
- Place `google-services.json` using GitHub Secrets (e.g., base64 decode step) before running the build step.
