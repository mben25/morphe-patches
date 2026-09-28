package app.facebook.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    const val PACKAGE_NAME = "com.facebook.katana"

    /**
     * Verified against the decoded manifest of `facebook_580.0.0.51.74.apkm`
     * (arm64-v8a, 480dpi, Android 9.0+, versionCode 475019269).
     */
    val COMPATIBILITY_FACEBOOK = Compatibility(
        name = "Facebook",
        packageName = PACKAGE_NAME,
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x0866FF,
        targets = listOf(
            AppTarget(version = "580.0.0.51.74"),
        ),
    )
}
