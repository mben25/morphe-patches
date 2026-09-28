# 👋🧩 Mben Morphe Patches

Personal Morphe patch bundle.

## ❓ About

Patches for apps I like. Right now the bundle ships patches for a single app:
**MT Capsule**.

## 📱 Supported apps

| App | Package | Verified version | Min SDK | File type |
| --- | --- | --- | --- | --- |
| MT Capsule | `com.pryshedko.mtisland` | 15.7 | 32 (Android 12L) | APK |

Newer MT Capsule versions than 15.7 are also accepted, but only as
**experimental** targets — the patches are fingerprint based, so they may or may
not resolve against a build they were not verified on. If a patch fails to apply,
downgrade to 15.7.

Any app not listed above is **not** supported by this bundle.

### Patches included

| Patch | App | Default | What it does |
| --- | --- | --- | --- |
| Unlock Pro | MT Capsule | ✅ enabled | Unlocks all pro features without a purchase. |

### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=mben25/morphe-patches

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.2.11](https://github.com/mben25/morphe-patches/releases/tag/v1.2.11)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;25 patches total
<details open>
<summary>📦 Masareef&nbsp;&nbsp;•&nbsp;&nbsp;14 patches</summary>
<br>

**🎯 Supported versions:**

| 2.6.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [AMOLED Dark Theme](#amoled-dark-theme) | Flattens dark theme backgrounds (window, cards, dialogs, toolbar, navigation bar, search, calendar) to pure black instead of the stock dark-gray shades. |  |
| [Bypass License Check](#bypass-license-check) | Stubs the Google Play Automatic Integrity Protection (pairip) signature and license checks, which otherwise detect the re-signed APK on launch and send the user to the app's Play Store page instead of opening the app. |  |
| [Deactivate Firebase Analytics](#deactivate-firebase-analytics) | Deactivates Firebase Analytics and removes its associated broadcast receivers and services. |  |
| [Deactivate Firebase Crashlytics](#deactivate-firebase-crashlytics) | Deactivates Firebase Crashlytics crash reporting and removes associated services. |  |
| [Deactivate Firebase Performance Monitoring](#deactivate-firebase-performance-monitoring) | Deactivates the collection of performance data on app startup time, network requests, and other related metrics. |  |
| [Disable All Telemetry](#disable-all-telemetry) | Disables all analytics and telemetry including Firebase, Google Analytics, Facebook, and Google Ads tracking. This is a comprehensive privacy patch that removes all data collection. |  |
| [Disable Facebook Ads Tracking](#disable-facebook-ads-tracking) | Disables Facebook Audience Network ad tracking and telemetry. |  |
| [Disable Facebook Analytics](#disable-facebook-analytics) | Disables Facebook App Events tracking and analytics. |  |
| [Disable Firebase Messaging Analytics](#disable-firebase-messaging-analytics) | Disables Firebase Cloud Messaging analytics and notification tracking. |  |
| [Disable Firebase Sessions](#disable-firebase-sessions) | Disables Firebase Sessions tracking and telemetry. |  |
| [Disable Google Ads Tracking](#disable-google-ads-tracking) | Disables Google AdMob tracking and impression reporting. |  |
| [Fill Adaptive Icon](#fill-adaptive-icon) | Scales the launcher adaptive icon foreground to fill the whole icon shape, removing the empty padding around it. |  |
| [Remove Ads](#remove-ads) | Stubs out AdsManager so no banner/native ads or consent dialogs are ever loaded, requested, or shown, and the Mobile Ads SDK is never initialized. |  |
| [Unlock Pro (Masareef)](#unlock-pro-masareef) | Makes UserDataManager.isSubscribed() always return true, unlocking all Pro features. |  |

</details>

<details open>
<summary>📦 DeviceInfo&nbsp;&nbsp;•&nbsp;&nbsp;7 patches</summary>
<br>

**🎯 Supported versions:**

| 3.4.3.4 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable All Telemetry](#disable-all-telemetry) | Disables all analytics and telemetry: Firebase Analytics, Crashlytics, Sessions, Installations and Transport auto-registration, and the AdServices / Advertising ID attribution surface. |  |
| [Hide Support Us Section](#hide-support-us-section) | Removes the "Support Us" preference category (Rate Us, Donate / Remove Ads) from the Settings screen. |  |
| [Remove AdServices Attribution](#remove-adservices-attribution) | Removes the ACCESS_ADSERVICES_ATTRIBUTION / ACCESS_ADSERVICES_AD_ID permissions and the android.ext.adservices uses-library entry, so the Privacy Sandbox AdServices APIs are never touched. |  |
| [Remove Advertising ID](#remove-advertising-id) | Removes the Google Play Services Advertising ID permission. |  |
| [Remove All Ads](#remove-all-ads) | Stubs the app's single native-ad load trigger so no banner, native, or interstitial ad is ever requested or shown on any screen (dashboard, Wi-Fi/app analyzer, sensors, battery, memory, tools, or automatic tests), and removes the Facebook Audience Network mediation SDK's auto-initializing ContentProvider so it never starts. |  |
| [Remove Facebook Audience Network Initialization](#remove-facebook-audience-network-initialization) | Removes the manifest-declared ContentProvider that auto-initializes the Facebook Audience Network mediation SDK on every app start. |  |
| [Remove Firebase Component Discovery](#remove-firebase-component-discovery) | Removes the Firebase ComponentDiscoveryService, which is how Analytics, Crashlytics, Sessions, Installations, and Transport auto-register themselves on startup. |  |

</details>

<details open>
<summary>📦 MT Capsule&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 15.9 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Unlocks all pro features without a purchase. |  |

</details>

<details open>
<summary>🌐 Universal&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Force hasSystemFeature true (narrow)](#force-hassystemfeature-true-narrow) | Return true from public static boolean methods calling PackageManager.hasSystemFeature with a String arg. Narrow-scoped to avoid lifecycle callbacks. |  |
| [Spoof Pixel model check (narrow)](#spoof-pixel-model-check-narrow) | In-APK utility methods checking Pixel model return true. Narrow-scoped to avoid lifecycle callbacks. |  |
| [Strip root detection (narrow)](#strip-root-detection-narrow) | Force public static boolean methods referencing "Magisk" to return false. Narrow-scoped. |  |

</details>

<!-- PATCHES_END -->

### 🛠️ Building locally

- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Patch the mpp file using [Morphe-Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information.

## 📜 License

Mben Morphe Patches are licensed under the [GNU General Public License v3.0](LICENSE)
