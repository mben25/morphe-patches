#!/usr/bin/env python3
"""
Builds a local-only copy of the StayFree Chrome extension that pairs with the StayFree Android
app patched with "Local device sync" (mben-patches) instead of api.stayfreeapps.com.

    python3 build_local_extension.py <unpacked extension dir> <output dir>

The original extension is left untouched. Changes in the output copy:
  * local-sync-firewall.js runs before StayFree code everywhere (service worker, extension pages,
    content scripts): sync calls go to the phone, all other StayFree/analytics traffic is dropped;
  * the AI-chat collector, retail-ad scraper, website auto-connect and Google Ads "setting up"
    content scripts are removed;
  * the web-usage uploader no longer requires the "data collection" opt-in (its only destination
    is now the phone, and the phone only keeps uploads from paired devices);
  * telemetry removed in code (the firewall still blocks it on the network): Bugsnag never reports
    or tracks sessions, Google Analytics events are never sent, the page-view / desktop / AI /
    retail-ad / query-param / subscription / brand-mention uploads and the opt-out ping are no-ops,
    and the brand-mention and panel ad-crawl collectors are never started;
  * redirects removed: no stayfreeapps.com tab on install (the dashboard opens directly), no
    "goodbye" page on uninstall, and stayfreeapps.com/extension-dashboard is no longer hijacked;
  * Google sign-in removed: the auth provider is never created, the "GoogleLogin" feature flag is
    forced off (Profile / Inbox Cleaner sidebar items, onboarding sign-in slide, Fix Problems
    sign-in step), the Settings "sign in on Profile" banner is off, the Profile, Manage accounts
    and Inbox Cleaner (Gmail) pages redirect to Settings, and the "identity" permission is dropped;
  * phone discovery: extension pages find the patched app on the Wi-Fi by themselves (HTTP probe of
    port 8787, LocalSend-style) and the pairing panel shows why it failed instead of "Something
    went wrong"; the English steps point to the app's in-app QR scanner instead of the camera app;
  * an options page (local-sync.html) to find or enter the phone's address;
  * _metadata (Web Store signature) and update_url removed so Chrome loads it unpacked;
  * extension name shortened to "StayFree" (was the Web Store long name) in chrome://extensions
    and the toolbar.
"""
import json
import re
import shutil
import sys
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent
FIREWALL = "local-sync-firewall.js"
OPTIONS_FILES = ("local-sync.html", "local-sync.js")
DROPPED_CONTENT_SCRIPTS = {
    "content-scripts/gen-ai-collector.js",
    "content-scripts/ad-finder.js",
    # stayfreeapps.com/extension-connect-device -> dashboard (cloud pairing link)
    "content-scripts/auto-connect-redirect.js",
    # Google Ads conversion page opened on install
    "content-scripts/setting-up.js",
}

# `enabled:async()=>{...;return <remoteConfig>.uploadWebUsage&&<hasConsentedAndAdult>(<consent>)}`
UPLOAD_GATE = re.compile(r"return (\w+)\.uploadWebUsage&&\w+\(\w+\)\}")

# Google sign-in. Each entry: (description, pattern, replacement, expected match count).
GOOGLE_LOGIN_PATCHES = (
    # dashboard entry: `defineAppConfig({...},{googleAuth:createGoogleAuth()})` -> no provider, so
    # nothing restores or refreshes a Google session (the provider is an optional inject elsewhere).
    ("Google auth provider", re.compile(r",\{googleAuth:[\w$]+\(\)\}\)"), ")", 1),
    # `useFeatureFlag("GoogleLogin")` gates the Profile and Inbox Cleaner sidebar items, the
    # onboarding sign-in slide and the Fix Problems sign-in step.
    ("GoogleLogin feature flag", re.compile(r'[\w$]+\("GoogleLogin"\)'), "!1", 3),
    # Settings > Devices A/B test that shows "Sign in with your Google account to pair devices".
    ("Settings sign-in banner", re.compile(r'=>[\w$]+\.value==="redirect"\)'), "=>!1)", 1),
    # "Don't have a Google account?" before the Pair New Device link (Settings, Fix Problems), in
    # every bundled locale. vue-i18n renders a static `s:""` as an empty string.
    (
        "doNotHaveAccount text",
        re.compile(r'(doNotHaveAccount:\{t:0,b:\{t:2,i:\[\{t:3\}\],s:)"[^"]*"'),
        r'\1""',
        15,
    ),
    # Pages that need a Google session: send direct links to Settings instead of a broken page.
    (
        "Profile / Inbox Cleaner routes",
        re.compile(
            r"\{path:([\w$]+)\.(profile|profileManage|inboxCleaner),component:\(\)=>[\w$]+\(\(\)=>"
            r'import\("\./Dashboard(?:Profile|ProfileManageAccounts|InboxCleaner)-[\w-]+\.js"\),'
            r"__vite__mapDeps\(\[[\d,]*\]\)\)\}"
        ),
        r"{path:\1.\2,redirect:\1.settings}",
        3,
    ),
)

# "Pair with StayFree Android" page. Each entry: (description, pattern, replacement, expected).
PAIRING_PATCHES = (
    # The pairing-code panel only ever says "Something went wrong": show the firewall's own
    # reason instead (phone not found on the Wi-Fi, saved address not answering). ofetch wraps it
    # as `[POST] "<url>": <no response> StayFree local sync: <reason>`; anything else keeps the
    # generic text.
    (
        "pairing error message",
        re.compile(
            r'(__name:"PairingCodeBaseGenerator",props:\{spinnerContainerClass:\{\}\},setup\([\w$]+\)\{const\{)'
            r'(.{0,600}?)R\(([\w$]+),\{key:1,onRetry:([\w$]+)\(([\w$]+)\)\},null,8,\["onRetry"\]\)',
            re.S,
        ),
        r"\1error:__sfPairingError,\2R(\3,{key:1,"
        r'message:(m=>{const i=m?.indexOf("StayFree local sync: ")??-1;return i<0?void 0:m.slice(i+21)})'
        r'(\4(__sfPairingError)?.message),onRetry:\4(\5)},null,8,["message","onRetry"])',
        1,
    ),
    # English steps: the app comes from the patch bundle, and the phone scans with StayFree's own
    # scanner (In-app QR scanner patch), not the camera app.
    (
        "pairing step 1",
        re.compile(r's:"Open Play Store on your Android phone and install StayFree\."'),
        's:"Install the patched StayFree app on your Android phone and open it, on the same Wi-Fi as this computer."',
        1,
    ),
    (
        "pairing step 2",
        re.compile(r's:"Open the camera app on your phone"'),
        's:"In StayFree, go to Settings > Paired Devices > Pair New Device > Browser Extension to open its scanner"',
        1,
    ),
)

# API client methods that only send telemetry to api.stayfreeapps.com.
TELEMETRY_API_METHODS = (
    "uploadPageViews",
    "uploadDesktopUsage",
    "uploadRetailAds",
    "uploadGenAiChats",
    "uploadGenAiLinks",
    "uploadGenAiInteractions",
    "uploadQueryParams",
    "uploadSubscriptionStatus",
    "uploadBrandMentions",
    "optOut",
)
API_METHOD = re.compile(r"\b(" + "|".join(TELEMETRY_API_METHODS) + r"):[\w$]+(?=[,}])")


def stub_api_methods(match: re.Match) -> str:
    stubbed, count = API_METHOD.subn(r"\1:async()=>{}", match.group(0))
    if count != len(TELEMETRY_API_METHODS):
        fail(f"API client: stubbed {count} telemetry methods (expected {len(TELEMETRY_API_METHODS)})")
    return stubbed


# Telemetry in every bundle (service worker, extension pages, content scripts).
TELEMETRY_PATCHES = (
    # Bugsnag client: no automatic error reports, no session pings, no enabled release stage.
    (
        "Bugsnag start",
        re.compile(r'(apiKey:[\w$]+,appType:"Web Extension",)'),
        r"\1autoTrackSessions:!1,autoDetectErrors:!1,enabledReleaseStages:[],",
        5,
    ),
    # Google Analytics measurement protocol: sent unless the user explicitly declined.
    (
        "Google Analytics gate",
        re.compile(r'await [\w$]+\.prefs\.hasAcceptedDataCollection\(\)!=="declined"'),
        "!1",
        5,
    ),
    ("API client uploads", re.compile(r"return\{getAndroidAppInfo:[^}]*\}"), stub_api_methods, 4),
)

# Service worker only.
BACKGROUND_PATCHES = (
    # Brand-mention collector and the panel ad-crawl uploader (api-pm.stayfreeapps.com); keep only
    # the trailing local ads listener.
    (
        "brand mentions / panel crawl",
        re.compile(
            r"(function [\w$]+\(([\w$]+)\)\{)const [\w$]+=[\w$]+\.getValue\(\)\.brandMentions;"
            r"[^{}]*\{[^}]*\}\);const [\w$]+=[\w$]+\(\);[\w$]+\(\{async isEnabled\(\)\{[^}]*\},"
            r"[^}]*crawlUploadUrl:[^}]*\}\),([\w$]+)\(\2\)\}"
        ),
        r"\1\3(\2)}",
        1,
    ),
    # "https://stayfreeapps.com/goodbye" opened after uninstall.
    ("uninstall URL", re.compile(r",[\w$]+\.runtime\.setUninstallURL\([\w$]+\(\)\)\}"), "}", 1),
    # On install: open the dashboard right away instead of stayfreeapps.com/extension/setting-up.
    (
        "install setting-up tab",
        re.compile(
            r"settingUpTabId:\(await [\w$]+\.tabs\.create\(\{url:[\w$]+,active:!0\}\)\)\.id,"
            r"completed:!1,timeoutId:setTimeout\(\(\)=>\{([\w$]+)\(\)\},[\w$]+\)"
        ),
        r"settingUpTabId:void 0,completed:!1,timeoutId:setTimeout(()=>{\1()},0)",
        1,
    ),
    # Tabs on stayfreeapps.com/extension-dashboard were rewritten to the extension dashboard.
    (
        "extension-dashboard hop",
        re.compile(
            r"(async function [\w$]+\()[\w$]+,[\w$]+\)\{![\w$]+\|\|[\w$]+!==[\w$]+\(\)\|\|"
            r"await [\w$]+\.tabs\.update\([\w$]+,\{url:[\w$]+\(\)\}\)\}"
        ),
        r"\1){}",
        1,
    ),
)


def fail(message: str) -> None:
    sys.exit(f"error: {message}")


def patch_manifest(out: Path) -> None:
    path = out / "manifest.json"
    manifest = json.loads(path.read_text(encoding="utf-8"))

    scripts = []
    for entry in manifest.get("content_scripts", []):
        js = [f for f in entry.get("js", []) if f not in DROPPED_CONTENT_SCRIPTS]
        if not js:
            continue  # ad-finder entry: only its stylesheet would be left
        entry["js"] = [FIREWALL] + js
        scripts.append(entry)
    manifest["content_scripts"] = scripts

    manifest["permissions"] = [p for p in manifest.get("permissions", []) if p != "identity"]
    manifest["options_page"] = "local-sync.html"
    manifest.pop("update_url", None)
    manifest["version_name"] = manifest.get("version", "") + " (local sync)"
    # Chrome Web Store long name; shorten it for the unpacked extensions list/toolbar.
    manifest["name"] = "StayFree"

    path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def patch_background(out: Path) -> None:
    path = out / "background.js"
    source = path.read_text(encoding="utf-8")

    source, count = UPLOAD_GATE.subn("return!0}", source)
    if count != 1:
        fail(f"web-usage upload gate matched {count} times in background.js (expected 1)")

    # Classic service worker: importScripts runs synchronously before the bundle evaluates.
    path.write_text(f'importScripts("/{FIREWALL}");\n' + source, encoding="utf-8")


def apply_patches(paths, patches, where: str) -> None:
    sources = {path: path.read_text(encoding="utf-8") for path in paths}
    for description, pattern, replacement, expected in patches:
        total = 0
        for path, source in sources.items():
            sources[path], count = pattern.subn(replacement, source)
            total += count
        if total != expected:
            fail(f"{description} matched {total} times in {where} (expected {expected})")
    for path, source in sources.items():
        path.write_text(source, encoding="utf-8")


def patch_google_login(out: Path) -> None:
    apply_patches(sorted((out / "chunks").glob("*.js")), GOOGLE_LOGIN_PATCHES, "chunks/")


def patch_pairing(out: Path) -> None:
    apply_patches(sorted((out / "chunks").glob("*.js")), PAIRING_PATCHES, "chunks/")


def patch_telemetry(out: Path) -> None:
    for name in DROPPED_CONTENT_SCRIPTS:
        (out / name).unlink(missing_ok=True)
    bundles = [out / "background.js", *sorted((out / "chunks").glob("*.js"))]
    bundles += sorted((out / "content-scripts").glob("*.js"))
    apply_patches(bundles, TELEMETRY_PATCHES, "extension scripts")
    apply_patches([out / "background.js"], BACKGROUND_PATCHES, "background.js")


def patch_pages(out: Path) -> None:
    for page in ("dashboard.html", "popup.html", "new-tab.html"):
        path = out / page
        html = path.read_text(encoding="utf-8")
        # A classic script executes before the deferred module scripts that boot the app.
        tag = f'<script src="/{FIREWALL}"></script>\n    '
        if tag.strip() in html:
            continue
        new_html, count = re.subn(r'(<script type="module")', tag + r"\1", html, count=1)
        if count != 1:
            fail(f"no module script found in {page}")
        path.write_text(new_html, encoding="utf-8")


def main() -> None:
    if len(sys.argv) != 3:
        fail("usage: build_local_extension.py <unpacked extension dir> <output dir>")
    src = Path(sys.argv[1]).resolve()
    out = Path(sys.argv[2]).resolve()
    if not (src / "manifest.json").is_file():
        fail(f"{src} is not an unpacked extension")
    if out == src or src in out.parents:
        fail("output must be outside the source extension")

    if out.exists():
        shutil.rmtree(out)
    shutil.copytree(src, out, ignore=shutil.ignore_patterns("_metadata"))

    for name in (FIREWALL, *OPTIONS_FILES):
        shutil.copy2(TOOL_DIR / name, out / name)

    patch_manifest(out)
    patch_background(out)
    patch_telemetry(out)
    patch_google_login(out)
    patch_pairing(out)
    patch_pages(out)
    print(f"Local-sync extension written to {out}")


if __name__ == "__main__":
    main()
