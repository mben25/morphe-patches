package app.stayfree.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    /**
     * Verified against `apps/stayfree/stayfree.20.16.1.apk` (versionCode 201601498, the base
     * APK of the Play split bundle).
     */
    val COMPATIBILITY_STAYFREE = Compatibility(
        name = "StayFree",
        packageName = "com.burockgames.timeclocker",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x4E7CFF,
        targets = listOf(
            AppTarget(version = "20.16.1"),
        ),
    )
}
