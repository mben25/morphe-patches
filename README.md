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
> **[v1.5.7](https://github.com/mben25/morphe-patches/releases/tag/v1.5.7)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;49 patches total
<details open>
<summary>📦 Masareef&nbsp;&nbsp;•&nbsp;&nbsp;14 patches</summary>
<br>

**🎯 Supported versions:**

| 2.6.4 |
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
<summary>📦 AliExpress&nbsp;&nbsp;•&nbsp;&nbsp;10 patches</summary>
<br>

**🎯 Supported versions:**

| 8.162.8 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [AMOLED dark mode](#amoled-dark-mode) | Unlocks AliExpress' built-in dark mode on every device (Redmi/Xiaomi/POCO are blacklisted by the app), turns it on by default so the app follows the system theme (it can still be switched off in Settings > Dark mode), and makes dark backgrounds pure black. |  |
| [Disable analytics](#disable-analytics) | Stops the Alibaba UT analytics SDK from attaching global properties and your account identity (nick, user id, open id) to tracked events. |  |
| [Disable forced updates](#disable-forced-updates) | Stops the automatic "new version available" and forced update dialogs. |  |
| [Disable promotions notifications](#disable-promotions-notifications) | Drops promotional push notifications (deals, campaigns, wishlist price drops, trends). Order status and message notifications still come through. |  |
| [Disable splash screen](#disable-splash-screen) | Skips the full-screen splash advertisement shown on app launch. |  |
| [Enable image saving](#enable-image-saving) | Always shows the Save button in the full-screen product and review image viewer. |  |
| [Remove ads](#remove-ads) | Blocks the marketing pop-up layers (campaign interstitials, coupon and gift overlays) shown over the home page, product pages and search. |  |
| [Remove affiliate tracking](#remove-affiliate-tracking) | Disables the Firebase Analytics events AliExpress logs for attribution and affiliate/marketing tracking. |  |
| [Remove coupons popup](#remove-coupons-popup) | Hides the coupon floor/popup shown over search results. |  |
| [Remove sponsored items from search](#remove-sponsored-items-from-search) | Removes sponsored (AD) products from search results. |  |

</details>

<details open>
<summary>📦 Salaat First&nbsp;&nbsp;•&nbsp;&nbsp;6 patches</summary>
<br>

**🎯 Supported versions:**

| 6.3.4 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Deactivate Firebase Telemetry](#deactivate-firebase-telemetry) | Disables Firebase/Google Analytics collection, Crashlytics crash reporting and session tracking via manifest flags, strips the App Measurement services and receiver, and removes the advertising-ID permission. App functionality (prayer times, push notifications) is unaffected. |  |
| [Disable All Telemetry](#disable-all-telemetry) | Comprehensive privacy patch: disables the OpenSignal and CellRebel data-collection SDKs and deactivates Firebase/Google Analytics, Crashlytics and session tracking. Does not affect app functionality. |  |
| [Disable App Rating Prompt](#disable-app-rating-prompt) | Removes the Play In-App Review ("rate this app") prompt launched from MainActivity.onCreate, so the review dialog is never requested. |  |
| [Disable Partner Data-Collection SDKs](#disable-partner-data-collection-sdks) | Disables the bundled OpenSignal and CellRebel network-measurement SDKs, which collect location, cell and network telemetry in the background. Forces each wrapper's eligibility check to return false so the SDKs never initialize. Also strips their background components from the manifest. |  |
| [Ignore Remote-Config Overrides](#ignore-remote-config-overrides) | Severs the Flagsmith remote-override channel by forcing the remote-config delegate to always resolve to the Firebase provider (which uses the app's bundled defaults). Server-side flags can no longer be flipped on against a patched build. Config reads keep working normally. |  |
| [Neutralize Signature Check](#neutralize-signature-check) | Stubs the hidden anti-tamper check in LanguageCheckerKt that MD5-fingerprints the signing certificate and calls System.exit(0) when it does not match the bundled signature. A re-signed (patched) APK always mismatches, so without this the app kills itself on launch and no other patch can run. Required dependency of every other Salaat patch. |  |

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
<summary>📦 StayFree&nbsp;&nbsp;•&nbsp;&nbsp;7 patches</summary>
<br>

**🎯 Supported versions:**

| 20.16.1 | 20.14.1 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable All Telemetry](#disable-all-telemetry) | Removes and blocks all telemetry and analytics: SensorTower usage-data upload (app/web usage, browsing, shopping, AI prompts), Amplitude, Singular, Bugsnag, Firebase Analytics/Crashlytics/Performance/Sessions, Google App Measurement, Facebook app events and the advertising ID. Telemetry endpoints are also rewritten to localhost. |  |
| [Hide Google sign-in in Paired Devices](#hide-google-sign-in-in-paired-devices) | Hides the "Sign in with Google" button from the Paired Devices section in settings. Pairing with a code still works. |  |
| [In-app QR scanner](#in-app-qr-scanner) | Scans the browser extension's pairing QR code inside StayFree, like Brave's built-in scanner, instead of asking you to open the camera app (whose link can't open a patched app). Opens on its own when you choose to pair with a browser extension or the desktop app, and from a "Scan QR code" shortcut on the app icon. Uses ZXing, so it works without Google Play Services. |  |
| [Local device sync](#local-device-sync) | Pairs the phone with the StayFree browser extension over your local network without data collection. The app runs a small sync server on port 8787 and serves its usage straight from Android, so nothing goes to StayFree/SensorTower. Removes the "Anonymous data collection must be enabled to pair devices" prompt. Needs the patched browser extension. |  |
| [Remove Google login request](#remove-google-login-request) | Removes the "Sign in with Google" page from onboarding and the "Create StayFree profile" step from the home setup checklist. Signing in manually from the drawer/pairing screens still works. |  |
| [Remove Inbox Control](#remove-inbox-control) | Removes the Inbox Control (Gmail cleaner) feature: its drawer entry, its permissions section and every screen that opens it. The Neon theme and custom app icons that were locked behind signing up for it are unlocked, and the Gmail sign-in no longer keeps the permission setup reminder on the home screen. |  |
| [Unlock all premium features](#unlock-all-premium-features) | Unlocks every theme (gamification-level, Black and Neon themes) and all custom app icons without earning levels, pairing a device or signing up for Inbox Control. |  |

</details>

<details open>
<summary>📦 Windy&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 51.2.1 | 51.0.1 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Premium](#unlock-premium) | Unlocks Windy Premium UI and features client-side by patching the JS bundle (store default 'premium', hasAny()=true, clearTier() neutralised, subs-premium body class) and forces the widgets' native premium check to true. Server-side premium data still requires a real subscription. Note: the map needs WebGL — it stays gray on WebViews that block WebGL (e.g. Cromite SystemWebView). |  |

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
