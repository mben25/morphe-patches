package app.mtcapsule.patches.mtisland

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.mtcapsule.patches.shared.Constants.COMPATIBILITY_MT_CAPSULE

private const val EXTENSION_CLASS = "Lapp/mtcapsule/extension/settings/SettingsBackupPatch;"

/**
 * `MainAppActivity` is declared by name in AndroidManifest.xml
 * (`android:name="com.pryshedko.mtisland.MainAppActivity"`), so unlike almost everything
 * else in this app it is *not* renamed by R8 - manifest components can't be, or the OS
 * couldn't start them. That makes matching on its literal class name a far more stable
 * anchor across versions than any string- or filter-based fingerprint, and it is why this
 * patch does not need a Fingerprints.kt entry of its own: there is nothing to search for,
 * the target class name is already known.
 *
 * `onCreate(Bundle)` is hooked to call into the extension, which registers two app
 * shortcuts (long-press the launcher icon) and, if the activity was (re)launched from one
 * of them, performs the export/import right there. See SettingsBackupPatch's file-level
 * comment for why shortcuts were chosen over a new Compose settings-list row, and for what
 * "settings" turned out to mean on disk (a Preferences DataStore file, not
 * SharedPreferences).
 */
private val mainActivityOnCreateFingerprint = Fingerprint(
    definingClass = "Lcom/pryshedko/mtisland/MainAppActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;")
)

@Suppress("unused")
val exportImportSettingsPatch = bytecodePatch(
    name = "Export/Import Settings",
    description = "Adds long-press app shortcuts (\"Export Settings\" / \"Import Settings\") " +
        "that back up MT Capsule's App Settings to a JSON file in the app's external " +
        "files directory and restore them from it.",
    default = true
) {
    compatibleWith(COMPATIBILITY_MT_CAPSULE)
    extendWith("extensions/extension.mpe")

    execute {
        val onCreateMethod = mainActivityOnCreateFingerprint.methodOrNull
            ?: throw PatchException("Failed to match MainAppActivity.onCreate")

        // Inserted before anything else in onCreate. Only ever touches p0 (the Activity
        // instance itself), so it never needs a spare register and can't collide with
        // whatever onCreate's own already-declared .locals count assumes is free.
        onCreateMethod.addInstruction(
            0,
            "invoke-static { p0 }, $EXTENSION_CLASS->onMainActivityCreate(Landroid/app/Activity;)V"
        )
    }
}
