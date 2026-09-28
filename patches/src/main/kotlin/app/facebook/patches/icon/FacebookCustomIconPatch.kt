package app.facebook.patches.icon

import app.facebook.patches.shared.Constants
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element

private const val ANDROID_NAME = "android:name"
private const val ANDROID_ICON = "android:icon"

/** The launcher alias Facebook ships enabled. Its icon is what the home screen shows. */
private const val DEFAULT_LAUNCHER_ALIAS = "com.facebook.katana.LoginActivity"

/**
 * Facebook's alternate app icons. Each one is a disabled `<activity-alias>` named
 * `LoginActivity.<suffix>` whose `android:icon` points at the icon. The in-app "App icon" screen
 * enables one of them and locks the rest behind a server-side entitlement.
 */
private val ICONS = linkedMapOf(
    "Fab" to "fab_ic",
    "Vaporwave" to "vaporwave_ic",
    "Fierce" to "fierce_ic",
    "Loved" to "loved_ic",
    "Dreamy" to "dreamy_ic",
    "Quiet cool" to "quiet_cool",
    "World Cup" to "worldcup_ic",
)

@Suppress("unused")
val facebookCustomIconPatch = resourcePatch(
    name = "Custom app icon",
    description = "Sets Facebook's launcher icon to one of its alternate app icons, including the ones " +
        "the in-app App icon screen keeps locked.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_FACEBOOK)

    val icon by stringOption(
        key = "icon",
        default = "fab_ic",
        values = ICONS,
        title = "Icon",
        description = "Which of Facebook's alternate icons the launcher shows.",
        required = true,
    )

    execute {
        val suffix = icon!!
        val sourceAlias = "$DEFAULT_LAUNCHER_ALIAS.$suffix"

        document("AndroidManifest.xml").use { manifest ->
            val aliases = manifest.getElementsByTagName("activity-alias").let { nodes ->
                (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
            }
            fun alias(name: String) = aliases.singleOrNull { it.getAttribute(ANDROID_NAME) == name }

            // Copy the reference as the manifest spells it, since the icons sit in a resource type
            // the decoder has no name for.
            val iconRef = alias(sourceAlias)?.getAttribute(ANDROID_ICON)?.takeIf { it.isNotEmpty() }
                ?: throw PatchException("Custom app icon: no icon alias $sourceAlias in the manifest")
            val launcher = alias(DEFAULT_LAUNCHER_ALIAS)
                ?: throw PatchException("Custom app icon: no launcher alias $DEFAULT_LAUNCHER_ALIAS in the manifest")

            // The home screen shows the enabled launcher alias's icon. The application's icon is
            // what Settings, the recents screen and notifications fall back to.
            launcher.setAttribute(ANDROID_ICON, iconRef)
            (manifest.getElementsByTagName("application").item(0) as? Element)
                ?.setAttribute(ANDROID_ICON, iconRef)
        }
    }
}
