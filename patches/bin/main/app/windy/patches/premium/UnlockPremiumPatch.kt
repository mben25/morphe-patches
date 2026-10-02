package app.windy.patches.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.rawResourcePatch
import app.windy.patches.shared.Constants.COMPATIBILITY_WINDY

// ── Architecture ─────────────────────────────────────────────────────────────
//
// Windy (com.windyty.android) is a Capacitor web-hybrid app. All subscription logic
// lives in one minified ES-module bundle:
//
//   assets/public/v/<version>/mobile.js        (51.0.1 → "51.0.1.mob.0f9e")
//
// Only the home-screen widgets have a native premium check (see Fingerprints.kt).
//
// ── Origin of this patch ──────────────────────────────────────────────────────
//
// Merged from two third-party Windy patches:
//   * "doom"    — rawResourcePatch, same-length byte edits of mobile.js on disk.
//   * "hoodles" — bytecodePatch hooking BridgeWebViewClient.shouldInterceptRequest
//                 and regex-rewriting the mobile.js response stream at runtime via an
//                 injected extension, plus forcing the widget check to true.
//
// This port keeps doom's on-disk edits (no extension, no per-request stream copy, no
// regex on a 395 KB string on every launch) and hoodles' widget check.
//
// ── "Gray screen" investigation (2026-10-02, garnet / AlphaDroid) ─────────────
//
// Both third-party patches showed only a gray screen on launch. Attaching DevTools to
// the WebView showed the real error, thrown by leaflet-gl.js while creating the map:
//
//   webglcontextcreationerror: "disabled by site settings policy." Failed to initialize WebGL
//
// The UNPATCHED APK fails identically, and WebGL is refused on every origin
// (example.com, data:). The system WebView on that device is the "Cromite
// SystemWebView" Magisk module, and Cromite blocks WebGL by default through a content
// setting that the app cannot change. Windy 51 renders its map only through WebGL
// (leaflet-gl), so the screen stays gray. This is a WebView/device problem, not a
// patch problem: switch the WebView provider (disable the cromite-webview module).
// The JS edits below were checked separately in headless Chromium: the bundle loads,
// the UI renders and <body> gets `subs-premium`.
//
// ── Subscription model (mobile.js, 51.0.1 identifiers) ────────────────────────
//
// Store `P` (localStorage adapter mirrored to SharedPreferences):
//   P.get(key): cache.has(key) ? cache.get(key) : storage.get() ?? def
//
//   uu(info) "setTier"  : status 'active' → P.set('subscription', tier), lu(tier)
//                          adds body class `subs-${tier}`; otherwise → cu().
//   cu()     "clearTier": nulls subscription + subscriptionInfo, removes body class.
//   du()     "hasAny"   : P.get('subscription') !== null — minifest ?premium, tile zoom
//                          caps, 1h step, premium calendar, paywall components.
//   gr       flag       : !!P.get('subscription') — premiumOnly store getters.
//   body.subs-premium   : CSS hides "Go Premium" CTAs and shows premium UI.
//
// Not logged in, launch runs uu(null) → cu(), which clears everything. The edits make
// the store default to 'premium', force gr/du true, and stop cu() from clearing.

private data class JsPatch(val label: String, val original: String, val replacement: String) {
    init {
        // Same-length edits keep every other byte offset in the bundle unchanged.
        require(original.toByteArray(Charsets.UTF_8).size == replacement.toByteArray(Charsets.UTF_8).size) {
            "JsPatch '$label' byte-length mismatch — pad the replacement with spaces."
        }
    }
}

private val JS_PATCHES = listOf(
    // P1 — subscription store defaults.
    //   def:`premium` → P.get('subscription') returns 'premium' on a cache/storage miss.
    //   subscriptionInfo def:0 → falsy, so fu()/getIssue returns null (no "payment issue"
    //   popup). e=>1 / nativeSync:1 are the same truthy values, freeing bytes for `premium`.
    //   nativeSync stays on, so 'premium' also reaches SharedPreferences for the widgets.
    JsPatch(
        label = "subscription store default",
        original = "subscription:{def:null,allowed:e=>!0,save:!0,nativeSync:!0},subscriptionInfo:{def:null,allowed:ir},",
        replacement = "subscription:{def:`premium`,allowed:e=>1,save:!0,nativeSync:1},subscriptionInfo:{def:0,allowed:ir},",
    ),

    // P2 — gr flag init: true at module load; the once-listener becomes dead code.
    JsPatch(
        label = "gr premium flag init",
        original = "gr=!!P.get(`subscription`),gr||P.once(`subscription`,e=>gr=!!e)",
        replacement = "gr=!0,!0||P.once(`subscription`,e=>gr=!0)                      ",
    ),

    // P3 — du() hasAny: always true.
    JsPatch(
        label = "du hasAny gate",
        original = "du=()=>P.get(`subscription`)!==null",
        replacement = "du=()=>!0||P.get(`subscription`)   ",
    ),

    // P4 — cu(): drop the two P.set(...,null) calls so the 'premium' value is never
    // evicted from the cache nor overwritten in storage.
    JsPatch(
        label = "cu subscription store clear",
        original = "P.set(`subscription`,null),P.set(`subscriptionInfo`,null)",
        replacement = "void 0                                                   ",
    ),

    // P5 — cu(): remove → add, so `subs-premium` is (re)applied instead of stripped.
    // e is 'premium' here thanks to P1 + P4. classList.add is idempotent.
    JsPatch(
        label = "cu body class direction",
        original = "e&&document.body.classList.remove(`subs-\${e}`)",
        replacement = "e&&document.body.classList.add   (`subs-\${e}`)",
    ),
)

private fun ByteArray.indexOf(needle: ByteArray, from: Int = 0): Int {
    outer@ for (i in from..size - needle.size) {
        for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
        return i
    }
    return -1
}

/** Applies [JS_PATCHES] to mobile.js on disk. */
private val unlockPremiumBundlePatch = rawResourcePatch {
    execute {
        // The bundle directory name is versioned — find it instead of hardcoding it.
        val bundleFile = get("assets/public/v")
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .map { it.resolve("mobile.js") }
            .firstOrNull { it.exists() }
            ?: throw PatchException("Windy: assets/public/v/<version>/mobile.js not found.")

        val bytes = bundleFile.readBytes()

        for (patch in JS_PATCHES) {
            val original = patch.original.toByteArray(Charsets.UTF_8)
            val idx = bytes.indexOf(original)
            if (idx < 0) {
                throw PatchException(
                    "Windy: '${patch.label}' not found in mobile.js — bundle changed or already patched.",
                )
            }
            // Each pattern must be unique, otherwise we might be editing the wrong site.
            if (bytes.indexOf(original, idx + 1) >= 0) {
                throw PatchException("Windy: '${patch.label}' matched more than once in mobile.js.")
            }
            patch.replacement.toByteArray(Charsets.UTF_8).copyInto(bytes, idx)
        }

        bundleFile.writeBytes(bytes)
    }
}

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlocks Windy Premium UI and features client-side by patching the JS bundle " +
        "(store default 'premium', hasAny()=true, clearTier() neutralised, subs-premium body class) " +
        "and forces the widgets' native premium check to true. Server-side premium data still " +
        "requires a real subscription. Note: the map needs WebGL — it stays gray on WebViews that " +
        "block WebGL (e.g. Cromite SystemWebView).",
    default = true,
) {
    compatibleWith(COMPATIBILITY_WINDY)

    dependsOn(unlockPremiumBundlePatch)

    execute {
        // `.locals 2` in 51.0.1, so v0 is a plain local register.
        IsPremiumForWidgetFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
