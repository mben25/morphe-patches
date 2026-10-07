package app.stayfree.patches.sync

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess

private const val R_STRING = "Lcom/burockgames/R\$string;"

/**
 * Retrofit `Builder.baseUrl(String)` (`Lvo9;->c(Ljava/lang/String;)V` in 20.14.1, merged by R8
 * into an unrelated class). Every SensorTower/StayFree Retrofit service is built through it, so
 * hooking it redirects all `https://api.stayfreeapps.com/` services in one place.
 */
object RetrofitBaseUrlFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    strings = listOf("baseUrl == null", "baseUrl must end in /: "),
)

/**
 * Every place that shows "Anonymous data collection must be enabled to pair devices…"
 * (`pair_device_data_collection_is_disabled`). In 20.14.1 there are three:
 * - `Lhr0;->invoke(…)`: the navigator lambda; when SensorTower is opted out, navigating to the
 *   pair-with-code destination (`Lpa9;->g`) shows the opt-in dialog instead,
 * - `Lwy4;->invoke()`: the QR-code / `android-connect-device?pairingCode=` deep-link path, same gate,
 * - `Lqs9;->a(Lpz1;I)V`: the warning text composable in Settings > Paired Devices.
 */
object PairDeviceDataCollectionGateFingerprint : Fingerprint(
    filters = listOf(
        fieldAccess(definingClass = R_STRING, name = "pair_device_data_collection_is_disabled"),
    ),
)