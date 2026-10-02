/*
 * Local verification helper. Not part of the published patch bundle behaviour:
 * it only applies the patches in this project to an APK on disk and prints the result,
 * so fingerprint regressions are caught without going through Manager or the CLI.
 *
 * Usage: ./gradlew :patches:applyToApk --args "<apk> [outputDexDir] [--patches=mtcapsule|masareef|deviceinfo|stayfree|salaat|windy]"
 */

package util

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.deviceinfo.patches.ads.removeAllAdsPatch
import app.deviceinfo.patches.ads.removeFacebookAudienceNetworkInitPatch
import app.deviceinfo.patches.settings.hideSupportUsSectionPatch
import app.deviceinfo.patches.telemetry.disableAllTelemetryPatch as deviceinfoDisableAllTelemetryPatch
import app.deviceinfo.patches.telemetry.removeAdServicesAttributionPatch
import app.deviceinfo.patches.telemetry.removeAdvertisingIdPatch as deviceinfoRemoveAdvertisingIdPatch
import app.deviceinfo.patches.telemetry.removeFirebaseComponentDiscoveryPatch
import app.masareef.patches.ads.removeAdsPatch
import app.masareef.patches.branding.amoledDarkThemePatch
import app.masareef.patches.branding.fillAdaptiveIconPatch
import app.masareef.patches.integrity.bypassLicenseCheckPatch
import app.masareef.patches.subscription.unlockProPatch as masareefUnlockProPatch
import app.masareef.patches.telemetry.deactivateFirebaseAnalyticsPatch
import app.masareef.patches.telemetry.deactivateFirebaseCrashlyticsPatch
import app.masareef.patches.telemetry.deactivateFirebasePerfPatch
import app.masareef.patches.telemetry.disableAllTelemetryPatch
import app.masareef.patches.telemetry.disableFacebookAdsPatch
import app.masareef.patches.telemetry.disableFacebookAnalyticsPatch
import app.masareef.patches.telemetry.disableFirebaseMessagingAnalyticsPatch
import app.masareef.patches.telemetry.disableFirebaseSessionsPatch
import app.masareef.patches.telemetry.disableGoogleAdsTrackingPatch
import app.masareef.patches.telemetry.removeAdsServicesPatch
import app.masareef.patches.telemetry.removeAdvertisingIdPatch
import app.masareef.patches.telemetry.removeAppMeasurementPatch
import app.masareef.patches.telemetry.removeCrashlyticsServicesPatch
import app.masareef.patches.telemetry.removeFacebookServicesPatch
import app.masareef.patches.telemetry.removeGoogleAnalyticsPatch
import app.mtcapsule.patches.mtisland.unlockProPatch
import app.salaat.patches.integrity.neutralizeSignatureCheckPatch
import app.salaat.patches.rating.disableAppRatingPatch
import app.salaat.patches.remoteconfig.ignoreRemoteConfigOverridesPatch
import app.salaat.patches.telemetry.disableAllTelemetryPatch as salaatDisableAllTelemetryPatch
import app.salaat.patches.telemetry.deactivateFirebaseTelemetryPatch
import app.salaat.patches.telemetry.disablePartnerSdksPatch
import app.stayfree.patches.inbox.removeInboxControlPatch
import app.stayfree.patches.login.removeGoogleLoginRequestPatch
import app.stayfree.patches.premium.unlockAllFeaturesPatch
import app.stayfree.patches.telemetry.disableAllTelemetryPatch as stayfreeDisableAllTelemetryPatch
import app.windy.patches.premium.unlockPremiumPatch as windyUnlockPremiumPatch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val positional = args.filterNot { it.startsWith("--") }
    val patchSet = args.firstOrNull { it.startsWith("--patches=") }?.substringAfter('=') ?: "mtcapsule"

    val apk = File(positional.firstOrNull() ?: "../mtcapsule15.7.apk")
    require(apk.exists()) { "APK not found: ${apk.absolutePath}" }

    val temporaryFiles = File(positional.getOrNull(1) ?: "build/apply-to-apk")
    temporaryFiles.deleteRecursively()

    var failed = false

    Patcher(PatcherConfig(apkFile = apk, temporaryFilesPath = temporaryFiles)).use { patcher ->
        patcher += when (patchSet) {
            "masareef" -> setOf(
                bypassLicenseCheckPatch,
                removeAdsPatch,
                amoledDarkThemePatch,
                fillAdaptiveIconPatch,
                masareefUnlockProPatch,
                disableAllTelemetryPatch,
                removeAdvertisingIdPatch,
                removeAppMeasurementPatch,
                removeGoogleAnalyticsPatch,
                removeCrashlyticsServicesPatch,
                removeAdsServicesPatch,
            )

            "deviceinfo" -> setOf(
                removeAllAdsPatch,
                removeFacebookAudienceNetworkInitPatch,
                hideSupportUsSectionPatch,
                deviceinfoDisableAllTelemetryPatch,
                deviceinfoRemoveAdvertisingIdPatch,
                removeAdServicesAttributionPatch,
                removeFirebaseComponentDiscoveryPatch,
            )

            "stayfree" -> setOf(
                stayfreeDisableAllTelemetryPatch,
                removeGoogleLoginRequestPatch,
                removeInboxControlPatch,
                unlockAllFeaturesPatch,
            )

            "salaat" -> setOf(
                neutralizeSignatureCheckPatch,
                salaatDisableAllTelemetryPatch,
                disablePartnerSdksPatch,
                deactivateFirebaseTelemetryPatch,
                disableAppRatingPatch,
                ignoreRemoteConfigOverridesPatch,
            )

            "windy" -> setOf(windyUnlockPremiumPatch)

            else -> setOf(unlockProPatch)
        }

        runBlocking {
            patcher().collect { result ->
                val exception = result.exception
                if (exception == null) {
                    println("OK   ${result.patch}")
                } else {
                    failed = true
                    println("FAIL ${result.patch}")
                    exception.printStackTrace()
                }
            }
        }

        // The dex is written out even when a patch failed, so a run where one fingerprint no
        // longer resolves can still be inspected for what the patches that did apply produced.
        // Disassembling that dex is the only way to catch a bad injection that the patcher
        // itself accepts, e.g. stale branch offsets that only surface as a VerifyError on device.
        val dexOutput = File(temporaryFiles, "patched-dex").apply { mkdirs() }
        patcher.get().dexFiles.forEach { dex ->
            File(dexOutput, dex.name).outputStream().use { dex.stream.copyTo(it) }
        }
        println("Patched dex files written to $dexOutput")
    }

    if (failed) exitProcess(1)
}
