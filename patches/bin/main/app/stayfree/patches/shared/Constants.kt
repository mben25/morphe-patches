package app.stayfree.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    /**
     * Verified against the decompiled `apps/stayfree/stayfree.20.14.1.apk`
     * (apktool.yml: versionCode 201401492, versionName 20.14.1, minSdk 32).
     */
    val COMPATIBILITY_STAYFREE = Compatibility(
        name = "StayFree",
        packageName = "com.burockgames.timeclocker",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x4E7CFF,
        targets = listOf(
            AppTarget(version = "20.14.1"),
        ),
    )
}
