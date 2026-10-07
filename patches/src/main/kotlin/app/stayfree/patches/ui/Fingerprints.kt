package app.stayfree.patches.ui

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import com.android.tools.smali.dexlib2.AccessFlags

private const val R_STRING = "Lcom/burockgames/R\$string;"

/**
 * "Pair with code" screen paragraph composable (`Lbyd;->b(Le02;I)V` in 20.16.1). It builds the
 * annotated "Visit https://stayfreeapps.com/?download to explore all StayFree apps." text and
 * draws it with the spacer below it; the "…end_short" string is used nowhere else.
 */
object PairWithCodeVisitTextFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "I"),
    filters = listOf(fieldAccess(definingClass = R_STRING, name = "promote_connect_devices_annotated_string_end_short")),
)

/**
 * Lambda (`Lds1;->g` in 20.16.1) that draws the "Select the app you want to pair your Android app
 * with:" Text, then a Spacer below it.
 */
object PairWithCodeSelectTextFingerprint : Fingerprint(
    filters = listOf(fieldAccess(definingClass = R_STRING, name = "select_platform_to_pair_device")),
)
