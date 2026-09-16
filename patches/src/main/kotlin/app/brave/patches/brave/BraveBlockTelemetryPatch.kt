package app.brave.patches.brave

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patcher.patch.resourcePatch
import app.brave.patches.shared.Constants
import app.brave.patches.shared.requireChromeDex
import org.w3c.dom.Element
import java.io.RandomAccessFile

private fun ByteArray.indexOfSequence(needle: ByteArray, startIndex: Int): Int {
    if (needle.isEmpty()) return startIndex
    outer@ for (i in startIndex..size - needle.size) {
        for (j in needle.indices) {
            if (this[i + j] != needle[j]) continue@outer
        }
        return i
    }
    return -1
}

// ── Resource Patch: Sets defaultValue="false" on telemetry switches in XML ─────
private val braveTelemetryResourcePatch = resourcePatch(
    name = "Brave Telemetry Resource Defaults",
    description = "Sets default values of P3A, Stats, and WDP switches to false in XML preferences.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_BRAVE)

    execute {
        val telemetrySwitches = listOf(
            "privacy_preserving_analytics_switch",
            "statistics_reporting_switch",
            "web_discovery_project_switch",
        )

        val targetFiles = get("res").walkTopDown()
            .filter { it.isFile && it.extension == "xml" }
            .filter { file ->
                val content = file.readText()
                telemetrySwitches.any { key -> content.contains(key) }
            }
            .toList()

        var modifiedAttrs = 0
        var modifiedFiles = 0

        for (file in targetFiles) {
            var fileTouched = false
            document(file.absolutePath).use { doc ->
                val elements = doc.getElementsByTagName("*")
                for (i in 0 until elements.length) {
                    val node = elements.item(i) as? Element ?: continue
                    val key = node.getAttribute("android:key").takeIf { it.isNotEmpty() }
                        ?: node.getAttribute("key")
                    if (key in telemetrySwitches) {
                        when {
                            node.hasAttribute("android:defaultValue") -> {
                                node.setAttribute("android:defaultValue", "false")
                                modifiedAttrs++
                                fileTouched = true
                            }
                            node.hasAttribute("defaultValue") -> {
                                node.setAttribute("defaultValue", "false")
                                modifiedAttrs++
                                fileTouched = true
                            }
                        }
                    }
                }
            }
            if (fileTouched) modifiedFiles++
        }

        println("[Block Telemetry] Set $modifiedAttrs preference defaults to false across $modifiedFiles XML layout files")
    }
}

// ── Hosts Blocker Patch: Redirects all 10 telemetry domain strings to 0.0.0.0 in libchrome.so ─────
private val braveHostsBlockerPatch = rawResourcePatch(
    name = "Brave Hosts Blocker Layer",
    description = "Redirects telemetry and diagnostic domain strings to 0.0.0.0 in libchrome.so as a second layer of defense.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_BRAVE)

    execute {
        val soFile = get("lib/arm64-v8a/libchrome.so")
        if (!soFile.exists()) {
            return@execute
        }

        // Hosts are located by scanning rather than by hard-coded file offsets: the
        // offsets shift on every Brave build (and differ between the mono APK and the
        // bundle's base split), which made the previous version throw on any binary
        // that was not the exact build the offsets were dumped from.
        val telemetryHosts = listOf(
            "star-randsrv.bsg.brave.com",
            "collector.bsg.brave.com",
            "usage-ping.brave.com",
            "patterns.wdp.brave.com",
            "collector.wdp.brave.com",
            "star.wdp.brave.com",
            "quorum.wdp.brave.com",
            "cr.brave.com",
            "crashpad.chromium.org",
            "variations.brave.com",
        )

        val redirectionIp = "0.0.0.0".toByteArray(Charsets.US_ASCII)
        val binary = soFile.readBytes()

        // The hosts sit in the binary's C string pool either standalone ("\0host\0") or as
        // the authority of a URL ("\0https://host/path\0"). Both forms are valid targets:
        // writing "0.0.0.0" plus NUL padding over the authority turns the URL into
        // "https://0.0.0.0" (the NUL terminates it before the path). Requiring a delimiter
        // on both sides keeps the scan from hitting a host that is only a substring of a
        // longer label (e.g. "cr.brave.com" inside "ocr.brave.com").
        val schemeSeparator = "://".toByteArray(Charsets.US_ASCII)

        fun occurrencesOf(host: String): List<Int> {
            val needle = host.toByteArray(Charsets.US_ASCII)
            val hits = mutableListOf<Int>()
            var from = 0
            while (true) {
                val index = binary.indexOfSequence(needle, from)
                if (index < 0) break
                from = index + 1

                val endIndex = index + needle.size
                if (endIndex >= binary.size) continue
                val terminator = binary[endIndex]
                if (terminator != 0.toByte() &&
                    terminator != '/'.code.toByte() &&
                    terminator != ':'.code.toByte()
                ) {
                    continue
                }

                val startsString = index == 0 || binary[index - 1] == 0.toByte()
                val followsScheme = index >= schemeSeparator.size &&
                    (index - schemeSeparator.size until index).withIndex()
                        .all { (offset, position) -> binary[position] == schemeSeparator[offset] }
                if (!startsString && !followsScheme) continue

                hits.add(index)
            }
            return hits
        }

        var writtenHosts = 0
        val missingHosts = mutableListOf<String>()

        RandomAccessFile(soFile, "rw").use { raf ->
            for (host in telemetryHosts) {
                val offsets = occurrencesOf(host)
                if (offsets.isEmpty()) {
                    missingHosts.add(host)
                    continue
                }

                for (offset in offsets) {
                    // Replacement: "0.0.0.0" then NUL padding out to the original length,
                    // so neighbouring string constants keep their offsets.
                    val replacement = ByteArray(host.length)
                    System.arraycopy(redirectionIp, 0, replacement, 0, redirectionIp.size)

                    raf.seek(offset.toLong())
                    raf.write(replacement)
                    writtenHosts++
                }
            }
        }

        if (writtenHosts == 0) {
            throw PatchException(
                "No telemetry host strings found in libchrome.so. " +
                    "Is this the Brave APK for ${Constants.BRAVE_TARGET_VERSION}?",
            )
        }

        println("[Block Telemetry] Redirected $writtenHosts host string(s) to 0.0.0.0 in libchrome.so")
        if (missingHosts.isNotEmpty()) {
            println("[Block Telemetry] Not present in this build (skipped): ${missingHosts.joinToString(", ")}")
        }
    }
}

// ── Bytecode Patch: Blocks Crash uploads, Variations seed, and forces telemetry gates to false
@Suppress("unused")
val braveBlockTelemetryPatch = bytecodePatch(
    name = "Block Brave Telemetry",
    description = "Blocks P3A product analytics, Brave Stats usage pings, crash dump uploads, WDP, and Variations seed fetching.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_BRAVE)

    dependsOn(braveTelemetryResourcePatch, braveHostsBlockerPatch)

    execute {
        requireChromeDex()

        val hookedMethods = mutableListOf<String>()

        // 1. Crash Upload: Primary point
        Fingerprint(
            definingClass = "Lorg/chromium/chrome/browser/crash/MinidumpUploadServiceImpl;",
            name = "tryUploadCrashDumpWithLocalId",
            returnType = "V",
            parameters = listOf("Ljava/lang/String;"),
        ).method.apply {
            addInstructions(0, "return-void")
            hookedMethods.add("MinidumpUploadServiceImpl.tryUploadCrashDumpWithLocalId")
        }

        // 2. Crash Upload: Defense in depth
        Fingerprint(
            definingClass = "Lorg/chromium/chrome/browser/crash/ChromeMinidumpUploadJobService;",
            name = "onStartJob",
            returnType = "Z",
            parameters = listOf("Landroid/app/job/JobParameters;"),
        ).method.apply {
            addInstructions(0, "const/4 v0, 0x0\nreturn v0")
            hookedMethods.add("ChromeMinidumpUploadJobService.onStartJob")
        }

        // 3. Variations: Abort HTTP connection before socket opens
        val variationsFp = Fingerprint(
            returnType = "Ljava/net/HttpURLConnection;",
            strings = listOf("https://variations.brave.com/seed"),
        )
        variationsFp.method.apply {
            addInstructions(
                0,
                """
                    new-instance v0, Ljava/io/IOException;
                    const-string v1, "Blocked by Morphe"
                    invoke-direct {v0, v1}, Ljava/io/IOException;-><init>(Ljava/lang/String;)V
                    throw v0
                """,
            )
            val className = variationsFp.originalClassDef.type.substringAfterLast('/').removeSuffix(";")
            hookedMethods.add("$className.$name")
        }

        // 4. PrefService.e(String): Strict conditional check for P3A, Stats, and WDP
        Fingerprint(
            definingClass = "Lorg/chromium/components/prefs/PrefService;",
            name = "e",
            returnType = "Z",
            parameters = listOf("Ljava/lang/String;"),
        ).method.apply {
            addInstructions(
                0,
                """
                    const-string v0, "brave.p3a.enabled"
                    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :not_p3a
                    const/4 v0, 0x0
                    return v0
                    :not_p3a

                    const-string v0, "brave.stats.reporting_enabled"
                    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :not_stats
                    const/4 v0, 0x0
                    return v0
                    :not_stats

                    const-string v0, "brave.web_discovery_enabled"
                    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :not_wdp
                    const/4 v0, 0x0
                    return v0
                    :not_wdp
                """,
            )
            hookedMethods.add("PrefService.e")
        }

        val targetClasses = hookedMethods.map { it.substringBefore('.') }.distinct()
        println("[Block Telemetry] Hooked ${hookedMethods.size} bytecode telemetry methods across ${targetClasses.size} classes")
    }
}
