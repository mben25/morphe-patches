package app.stayfree.patches.sync

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.stayfree.patches.shared.Constants.COMPATIBILITY_STAYFREE

@Suppress("unused")
val assumeInternetConnectionPatch = bytecodePatch(
    name = "Assume device is online",
    description = "Fixes \"Something went wrong, please try again…\" when pairing with the browser " +
        "extension. StayFree gates all sync behind a captive-portal check that has to reach " +
        "Google, which fails on a de-Googled or firewalled ROM; local pairing never needs the " +
        "internet, so report the device as online.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_STAYFREE)

    execute {
        // Every sync entry point starts with the same gate:
        //
        //     NETWORK_CONNECTION_LOST == statusFlow.value -> return
        //
        // and that status is written from NetworkUtils.hasInternetConnection(), which GETs
        // https://clients3.google.com/generate_204 (falling back to the Qualcomm mirror) and
        // demands an exact HTTP 204. When neither answers 204, the flow holds
        // NETWORK_CONNECTION_LOST and the QR pairing coroutine returns on its first instruction —
        // before any Retrofit client is built — so the UI only ever shows the generic failure.
        //
        // Patch the check rather than the three read sites: it is a self-contained public `()Z`
        // in a library R8 leaves unobfuscated, while the gates live in merged lambdas whose
        // shape moves between versions. Its only other callers are the Glide icon fetchers,
        // which use it to decide whether to attempt a download — an attempt that simply fails
        // the way it would without the pre-check.
        //
        // This is the right answer even on a device that *is* online: pairing is served by the
        // local sync server on 127.0.0.1, which needs no internet access at all.
        val method = HasInternetConnectionFingerprint.methodOrNull
            ?: throw PatchException("NetworkUtils.hasInternetConnection not found")

        method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
