/*
 * Facebook 573.0.0.37.74 / 473623755
 *
 * Settings rows such as Tab bar, Language and region, Media, Time management,
 * Browser and Dark mode do nothing when tapped on a patched build.
 *
 * Their Bloks click handler (X.GuY.A00) resolves the fb:// link to an
 * ImmersiveActivity intent, then launches it through Facebook's secure intent
 * launcher (X.0pE.A08). That launcher runs every resolved component through
 * X.0Zn ("FamilyIntentScope"), whose A01(Context, ApplicationInfo) asks
 * X.04C.isAppIdentityTrusted whether the target app is signed with a Meta
 * release key. Morphe re-signs the APK, so Facebook's own package fails the
 * check, the launcher reports "Components matching the intent were found but
 * none match the given scope. [FamilyIntentScope]" and drops the intent.
 * Nothing is started, so the tap appears dead. (Confirmed with an ART method
 * trace and a heap dump of a real tap on the Media row.)
 *
 * Trust the app's own package in that check. Every other package still goes
 * through the stock signature allowlist, so intents to other apps keep their
 * original scope rules.
 */
package app.facebook.patches.intents

import app.facebook.patches.shared.Constants.COMPATIBILITY_FACEBOOK_573
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

private val familyIntentScopeTrustedApp = Fingerprint(
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;", "Landroid/content/pm/ApplicationInfo;"),
    strings = listOf(" is not an app matching the targeted app filter, but fail-open."),
)

@Suppress("unused")
val fixFacebookOwnAppIntentScope573Patch = bytecodePatch(
    name = "Fix Facebook settings links (573)",
    description = "Lets re-signed Facebook open its own screens, such as the Media, Dark mode and Tab bar settings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK_573)

    execute {
        val method = familyIntentScopeTrustedApp.method
        // p1 = Context, p2 = ApplicationInfo; .locals 6, so p1/p2 are v7/v8.
        // v0 and v1 are overwritten by the stock body before any read.
        method.addInstructionsWithLabels(
            0,
            """
                invoke-virtual {p1}, Landroid/content/Context;->getPackageName()Ljava/lang/String;
                move-result-object v0
                iget-object v1, p2, Landroid/content/pm/PackageItemInfo;->packageName:Ljava/lang/String;
                invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :mben_fb573_scope_stock
                const/4 v0, 0x1
                return v0
            """.trimIndent(),
            ExternalLabel("mben_fb573_scope_stock", method.getInstruction(0)),
        )
    }
}
