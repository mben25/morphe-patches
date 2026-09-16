package app.brave.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException

/**
 * Brave ships as an app bundle with isolated splits. The base split carries only ~4.6k
 * classes (stubs plus `org.chromium.base`) while every class the Brave patches fingerprint
 * lives in the chrome split — but it still carries `libchrome.so` and the full resources,
 * so a base split looks like a complete APK right up until the first fingerprint fails
 * with nothing but a Fingerprint hash in the message.
 *
 * Every Brave bytecode patch calls this first so that case is reported for what it is.
 */
context(context: BytecodePatchContext)
fun requireChromeDex() {
    val prefServiceIsPresent = try {
        Fingerprint(
            definingClass = "Lorg/chromium/components/prefs/PrefService;",
            name = "create",
            returnType = "Lorg/chromium/components/prefs/PrefService;",
            parameters = listOf("J"),
        ).originalMethod != null
    } catch (_: PatchException) {
        false
    }

    if (!prefServiceIsPresent) {
        throw PatchException(
            "This APK does not contain Brave's browser code — org.chromium.components.prefs.PrefService " +
                "is missing, so it is the base split of the app bundle, not a complete APK. " +
                "Patch the standalone BraveMonoarm64.apk for ${Constants.BRAVE_TARGET_VERSION} from " +
                "github.com/brave/brave-browser/releases/tag/v${Constants.BRAVE_TARGET_VERSION} instead.",
        )
    }
}
