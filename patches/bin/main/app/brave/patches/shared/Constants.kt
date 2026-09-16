package app.brave.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    const val BRAVE_TARGET_VERSION = "1.94.117"
    const val BRAVE_PACKAGE_NAME = "com.brave.browser"

    // Must be the self-contained mono APK, not a Play/APKM base split. The base split of
    // the app bundle carries only ~4.6k classes (stubs, org.chromium.base); everything the
    // bytecode patches fingerprint (PrefService, MinidumpUploadServiceImpl,
    // BraveOriginPreferences, …) lives in the chrome split. Patching the base split fails
    // on the first fingerprint.
    val COMPATIBILITY_BRAVE = Compatibility(
        name = "Brave Private Web Browser, VPN",
        packageName = BRAVE_PACKAGE_NAME,
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFF4500,
        targets = listOf(
            AppTarget(
                version = BRAVE_TARGET_VERSION,
                description = "Download BraveMonoarm64.apk (v1.94.117) from " +
                    "github.com/brave/brave-browser/releases/tag/v1.94.117 — " +
                    "the standalone mono APK, not a Play Store / APKM split bundle",
            ),
        ),
    )
}
