package app.stayfree.patches.login

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

internal const val ONBOARDING_PAGE_TYPE = "Lcom/burockgames/timeclocker/common/enums/OnboardingPageType;"

/**
 * Onboarding ViewModel constructor (`Lcr7;-><init>(Lds0;)V` in 20.14.1). When no page list is
 * cached yet it builds
 * `mutableListOf(IntroPage, TermsPage, OnboardingGoogleAccountPage, PermissionUsagePage)` and
 * then appends device-specific pages after a xiaomi/redmi/poco manufacturer check — those three
 * strings together in a constructor are unique to it.
 */
object OnboardingPagesConstructorFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf("L"),
    strings = listOf("xiaomi", "redmi", "poco"),
    filters = listOf(
        fieldAccess(definingClass = ONBOARDING_PAGE_TYPE, name = "OnboardingGoogleAccountPage"),
        methodCall(name = "mutableListOf"),
    ),
)
