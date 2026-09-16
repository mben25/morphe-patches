# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Morphe patch bundle (fork of `MorpheApp/morphe-patches-template`). It compiles Kotlin
patch definitions into an `.mpp` bundle that Morphe Manager / Morphe Desktop applies to an
APK. Currently ships one patch: **Unlock Pro** for MT Capsule (`com.pryshedko.mtisland`,
verified against 15.7, minSdk 32).

## Build & verify

```bash
./gradlew buildAndroid                 # build the bundle -> patches/build/libs/patches-*.mpp
./gradlew :patches:buildAndroid clean  # what CI runs to verify compilation
./gradlew :patches:applyToApk --args "<path-to.apk> [outputDexDir] [--patches=mtcapsule|brave|brave-slim]"
./gradlew :patches:generatePatchesList # regenerates patches-list.json (release only)
```

There is no test suite. `applyToApk` **is** the test: it runs the real patcher against a
local APK and exits non-zero if any patch fails to resolve or execute. Default APK path if
no arg is given is `../mtcapsule15.7.apk`. Always run it after touching fingerprints — a
compiling patch that no longer resolves is the normal failure mode.

Gradle resolves the `app.morphe.patches` plugin and `app.morphe:morphe-patcher` from
GitHub Packages, so a GitHub PAT is required: set `gpr.user`/`gpr.key` in
`~/.gradle/gradle.properties`, or `GITHUB_ACTOR`/`GITHUB_TOKEN` in the env.

## Layout

- [patches/src/main/kotlin/app/mtcapsule/patches/](patches/src/main/kotlin/app/mtcapsule/patches/) — the patches. One package per target app (`mtisland/`), plus `shared/` for cross-patch constants.
- [Constants.kt](patches/src/main/kotlin/app/mtcapsule/patches/shared/Constants.kt) — the `Compatibility` object (package name, icon color, `AppTarget` list). Every patch's `compatibleWith(...)` points here; adding a supported version means editing this one file.
- [patches/src/main/kotlin/util/](patches/src/main/kotlin/util/) — build tooling, not shipped behaviour. `ApplyToApk.kt` is the local verifier; `PatchListGenerator.kt` is invoked by release.
- [extensions/extension/](extensions/extension/) — Android library compiled to `extension.mpe` and injected as dex by patches that need real Java code. Still the untouched template sample (`app.template.extension`); no current patch uses it.

Note the Gradle project name is `mtcapsule-patches` and the module is `:patches`, but
`buildAndroid` at the root works too.

## Adding patches for a new app

**Never edit another app's existing patches when adding a new app.** Each target app owns
its own package (`mtisland/` for MT Capsule) — a new app means a new sibling package with
its own patches and its own `Compatibility`. Existing apps' patch files, fingerprints, and
compatibility entries stay byte-for-byte untouched.

If the new app appears to need a change in existing code, do not make it: prefer
duplicating the small piece into the new package, and if the shared code genuinely has to
change, stop and ask first. Silent edits to a working app's patches are how a bundle
regresses on an app nobody was testing.

## Brave (`app/brave/patches/`)

Ported from the `kveld` bundle (`app.morphe.patches.brave`) and verified against
**BraveMonoarm64.apk 1.94.117** (versionCode 429411704).

**The APK matters.** Brave ships as an app bundle with isolated splits: the base split has
only ~4.6k classes (stubs plus `org.chromium.base`), while everything these patches
fingerprint — `PrefService`, `MinidumpUploadServiceImpl`, `BraveOriginPreferences`,
`NotificationSchedulerTask` — lives in the chrome split. Feeding Manager a base-split APK
fails on the first fingerprint (`Failed to match the fingerprint … BraveBlockTelemetryPatch`).
Use the standalone `BraveMonoarm64.apk` from
`github.com/brave/brave-browser/releases/tag/v<version>`; that is what `COMPATIBILITY_BRAVE`
(`ApkFileType.APK`) documents.

Verify with:

```bash
./gradlew :patches:applyToApk --args "<BraveMonoarm64.apk> build/apply-brave --patches=brave"
./gradlew :patches:applyToApk --args "<BraveMonoarm64.apk> build/apply-slim --patches=brave-slim"
```

`braveHostsBlockerPatch` rewrites telemetry hosts inside `lib/arm64-v8a/libchrome.so`. It
**scans** for the host strings instead of using dumped file offsets (the upstream version
did, and threw on any binary those offsets did not match). Hosts occur both standalone
(`\0host\0`) and as URL authorities (`\0https://host/path\0`); both are overwritten with
`0.0.0.0` plus NUL padding, so a URL collapses to `https://0.0.0.0`. 1.94.117 yields 12
rewrites across 10 host names.

## How the patches work

Patches are declared as top-level `val`s via `bytecodePatch { ... }` and discovered by
reflection, hence `@Suppress("unused")`. `execute { }` runs against the decoded dex.

Everything is resolved against **obfuscated** R8 output, so the pattern used here matters:

1. One anchor fingerprint that searches the whole app, matched on stable strings
   (`GetOrCreateSecretKeyFingerprint` matches on `"AndroidKeyStore"`, `"pro_version_key"`, …).
2. `originalClassDef` from that anchor is then passed to `Fingerprint.match(classDef)` to
   scope every subsequent lookup to the same class. Inline `Fingerprint(...)` objects
   declared at the call site are normal for these scoped lookups; only reusable/anchor
   fingerprints live in [Fingerprints.kt](patches/src/main/kotlin/app/mtcapsule/patches/mtisland/Fingerprints.kt).
3. Class references are chased through matched instructions —
   `match.instructionMatches[i].getMethodCalled().definingClass` — rather than named,
   since names are obfuscated (`Lfo1;`) and change between versions.

Obfuscated parameter/return types are written as bare `"L"`. `filters` with
`InstructionLocation.MatchAfterImmediately()` / `MatchAfterWithin(n)` express ordering
between instructions.

When inserting instructions by index, iterate **reversed** so earlier insertions don't
shift the indexes still to be patched (see the emit-override loop in
[UnlockProPatch.kt](patches/src/main/kotlin/app/mtcapsule/patches/mtisland/UnlockProPatch.kt)).

The `unlockProPatch` comments record hard-won specifics (why the setter must also be
patched, why the boxing call is the wrong hook point, why R8 merges the collector). Keep
that level of comment when changing bytecode-level logic — it is what makes a regression
diagnosable on the next app version.

## Release process

Do not hand-manage releases.

- Work on `dev`. Merge `dev` → `main` (merge commit, never squash) for a stable release.
- [Semantic commits](https://kapeli.com/cheat_sheets/Semantic_Commits.docset/Contents/Resources/Documents/index) only: `feat:` and `fix:` cut a release, `chore:` does not.
- `release.yml` + `.releaserc` handle everything. Don't write new release scripts; modify these.
- Generated, never hand-edited or hand-committed: `patches-list.json`, `patches-bundle.json`, `CHANGELOG.md`, and the `<!-- PATCHES_START -->` block in [README.md](README.md).
- Never force-push a semantic-release commit — it breaks all future releases. Fix a broken release by cutting a new one.
