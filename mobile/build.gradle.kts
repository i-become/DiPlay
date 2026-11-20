import java.util.Properties
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.KeyStore

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Optional local-only input. CI and ordinary source builds contain no accessory identity.
val localAuthenticationAssets = providers.environmentVariable("DIPLAY_AUTH_ASSETS_DIR")
    .orNull?.let { file(it).canonicalFile }

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { stream -> load(stream) }
}

fun localOrEnv(env: String, propKey: String): String =
    providers.environmentVariable(env).orNull ?: localProperties.getProperty(propKey).orEmpty()

android {
    namespace = "com.shilapi.xcertplay"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.shihab.diplay"
        minSdk = 25
        targetSdk = 37
        versionCode = 1402
        versionName = "1.4.2"
        testInstrumentationRunner = "com.shihab.diplay.test.IntegrityInstrumentation"

    }


    localAuthenticationAssets?.let { sourceSets.getByName("main").assets.directories.add(it.path) }

    signingConfigs {
        create("release") {
            val keystorePath = localOrEnv("ANDROID_KEYSTORE_PATH", "storeFile").ifBlank {
                ".private/diplay-local.jks"
            }
            storeFile = file(keystorePath).takeIf { it.isAbsolute && it.isFile }
                ?: rootProject.file(keystorePath)
            storePassword = localOrEnv("ANDROID_KEYSTORE_PASSWORD", "storePassword")
            keyAlias = localOrEnv("ANDROID_KEY_ALIAS", "keyAlias")
            keyPassword = localOrEnv("ANDROID_KEY_PASSWORD", "keyPassword")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".hudtest"
            versionNameSuffix = "-hud-test"
        }
        release {
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.getByName("release")
        }
    }
    if (providers.gradleProperty("integrityReleaseTest").orNull == "true") testBuildType = "release"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // Match the app's seven languages, including both Chinese scripts.
        // Region variants must be listed explicitly; language-only filters do not keep them.
        localeFilters += setOf(
            "en", "en-rAU", "en-rCA", "en-rGB", "en-rIN", "en-rXC",
            "zh", "zh-rCN", "zh-rHK", "zh-rTW",
            "ar", "ru", "es", "es-rUS", "uk",
        )
    }
    packaging {
        jniLibs.useLegacyPackaging = true
        resources {
            // Only lightweight Bouncy Castle primitives are used; R8 removes all Picnic
            // classes, but cannot remove their unreferenced algorithm lookup tables.
            excludes += setOf(
                "org/bouncycastle/pqc/crypto/picnic/lowmcL1.bin.properties",
                "org/bouncycastle/pqc/crypto/picnic/lowmcL3.bin.properties",
                "org/bouncycastle/pqc/crypto/picnic/lowmcL5.bin.properties",
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(project(":common"))
    // Keep the injected ICCOA SDK visible to R8 as a compile-time library. The real SDK dex is
    // appended after R8, so these stubs must not be packaged as application classes.
    compileOnly(files("../common/libs/icar-sdk-stubs.jar"))
    implementation("androidx.room:room-runtime:2.4.3")
    // The injected Carlink dex parses its handshake with this donor runtime.
    // compileOnly SDK stubs cannot contribute transitive dependencies.
    implementation("com.google.protobuf:protobuf-java:3.25.1")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// AGP does not package a raw SDK dex dependency. Append the verified ICCOA dex to the
// final application APK so CarPlay remains a normal library path and Carlink stays optional.
val carlinkDex = file("libs/icar-sdk-corrected.dex")
fun sha256(file: File): ByteArray = MessageDigest.getInstance("SHA-256").digest(file.readBytes())

listOf("debug", "release").forEach { buildType ->
    val injectTask = tasks.register("injectCarlinkDex${buildType.replaceFirstChar(Char::uppercase)}") {
        notCompatibleWithConfigurationCache("APK post-processing uses Android build tools")
        doLast {
            check(carlinkDex.isFile) { "Missing Carlink SDK dex: $carlinkDex" }
            val apkRoot = layout.buildDirectory.dir("outputs/apk/$buildType").get().asFile
            val apks = apkRoot.walkTopDown().filter { it.isFile && it.extension == "apk" }.toList()
            check(apks.isNotEmpty()) { "No $buildType APK found under $apkRoot" }
            val sdkPath = providers.environmentVariable("ANDROID_HOME").orNull
                ?: providers.environmentVariable("ANDROID_SDK_ROOT").orNull
                ?: localProperties.getProperty("sdk.dir")
                ?: error("Set ANDROID_HOME or local.properties sdk.dir")
            val buildTools = file(sdkPath).resolve("build-tools").listFiles()
                ?.filter { it.isDirectory && it.resolve("zipalign${if (org.gradle.internal.os.OperatingSystem.current().isWindows) ".exe" else ""}").isFile }
                ?.maxByOrNull { it.name }
                ?: error("Android build tools with zipalign are unavailable")
            val windows = org.gradle.internal.os.OperatingSystem.current().isWindows
            val zipalign = buildTools.resolve("zipalign${if (windows) ".exe" else ""}")
            val apksigner = buildTools.resolve("apksigner${if (windows) ".bat" else ""}")
            check(apksigner.isFile) { "apksigner is missing from $buildTools" }
            val signing = android.signingConfigs.getByName(buildType)
            val keystore = signing.storeFile?.takeIf(File::isFile)
                ?: error("A valid $buildType signing keystore is required after Carlink dex injection")
            check(!signing.storePassword.isNullOrBlank() && !signing.keyAlias.isNullOrBlank() && !signing.keyPassword.isNullOrBlank()) {
                "Complete $buildType signing credentials are required after Carlink dex injection"
            }
            val storePassword = signing.storePassword!!
            val keyAlias = signing.keyAlias!!
            val keyPassword = signing.keyPassword!!
            if (buildType == "release") {
                // Fail packaging on accidental certificate changes instead of shipping a locked-out app.
                val pinnedSource = rootProject.file("shared/src/main/jni/diplay_integrity.c").readText()
                val pin = Regex("official\\[\\] = \"([0-9a-f]{64})\"").find(pinnedSource)?.groupValues?.get(1)
                    ?: error("Native release certificate pin is missing")
                val passwordChars = storePassword.toCharArray()
                val certificate = try {
                    KeyStore.getInstance(keystore, passwordChars).getCertificate(keyAlias)
                } finally { passwordChars.fill('\u0000') }
                check(certificate != null) { "Release signing certificate is missing" }
                val actual = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
                check(actual == pin) { "Release signing certificate differs from the native pin. Review key rotation before packaging." }
                val authorityHeader = rootProject.file("shared/src/main/jni/seal_authority.h").readText()
                val authority = Regex("0x([0-9a-f]{2})").findAll(authorityHeader)
                    .map { it.groupValues[1].toInt(16).toByte() }.toList().toByteArray()
                check(authority.contentEquals(certificate.publicKey.encoded)) {
                    "Content-seal public key differs from release signing key. Review authority rotation before packaging."
                }
            }
            val python = if (windows) "python" else "python3"
            val dexTool = rootProject.file("scripts/carlink_dex.py")
            fun runTool(vararg args: String) {
                val builder = ProcessBuilder(*args).redirectErrorStream(true)
                builder.environment().apply {
                    put("DIPLAY_SEAL_KEYSTORE", keystore.absolutePath)
                    put("DIPLAY_SEAL_STORE_PASSWORD", storePassword)
                    put("DIPLAY_SEAL_ALIAS", keyAlias)
                    put("DIPLAY_SEAL_KEY_PASSWORD", keyPassword)
                }
                val process = builder.start()
                process.inputStream.bufferedReader().useLines { lines -> lines.forEach(::println) }
                check(process.waitFor() == 0) { "Tool failed: ${args.first()}" }
            }
            for (apk in apks) {
                val preparedDex = layout.buildDirectory.file("carlink/$buildType/prepared.dex").get().asFile
                // Keep DiPlay's Guava implementation. Remove only the donor's duplicate
                // third-party definitions, then serialize with the SDK's dexlib2 tool.
                runTool(python, dexTool.absolutePath, apk.absolutePath, carlinkDex.absolutePath, preparedDex.absolutePath,
                    "--sdk-dir", sdkPath, "--java", File(System.getProperty("java.home"), "bin/java${if (windows) ".exe" else ""}").absolutePath)
                val previousSdkEntries = preparedDex.resolveSibling("prepared.entries").readLines().toSet()
                val raw = File(apk.parentFile, apk.nameWithoutExtension + "-unsigned-injected.apk")
                val aligned = File(apk.parentFile, apk.nameWithoutExtension + "-aligned-injected.apk")
                val signed = File(apk.parentFile, apk.nameWithoutExtension + "-signed-injected.apk")
                ZipFile(apk).use { zin ->
                    val entries = zin.entries().asSequence().toList()
                    val originalDex = entries.filter { it.name.matches(Regex("classes\\d*\\.dex")) }
                    val duplicateRuntime = previousSdkEntries
                    val dexNames = originalDex.map { it.name }.filterNot { it in duplicateRuntime }.toSet()
                    val dexName = (2..99).map { "classes${it}.dex" }.first { it !in dexNames }
                    ZipOutputStream(BufferedOutputStream(FileOutputStream(raw))).use { zout ->
                        entries.filter { !it.name.startsWith("META-INF/") && it.name !in duplicateRuntime }.forEach { entry ->
                            val out = ZipEntry(entry.name)
                            out.method = entry.method
                            if (entry.method == ZipEntry.STORED) { out.size = entry.size; out.crc = entry.crc }
                            zout.putNextEntry(out); zin.getInputStream(entry).use { it.copyTo(zout) }; zout.closeEntry()
                        }
                        zout.putNextEntry(ZipEntry(dexName)); preparedDex.inputStream().use { it.copyTo(zout) }; zout.closeEntry()
                    }
                }
                val java = File(System.getProperty("java.home"), "bin/java${if (windows) ".exe" else ""}").absolutePath
                val sealTool = rootProject.file("scripts/ApkContentSeal.java").absolutePath
                if (buildType == "release") runTool(java, sealTool, "seal", raw.absolutePath)
                runTool(zipalign.absolutePath, "-f", "4", raw.absolutePath, aligned.absolutePath)
                runTool(apksigner.absolutePath, "sign", "--ks", keystore.absolutePath,
                        "--ks-pass", "env:DIPLAY_SEAL_STORE_PASSWORD", "--ks-key-alias", keyAlias,
                        "--key-pass", "env:DIPLAY_SEAL_KEY_PASSWORD", "--out", signed.absolutePath, aligned.absolutePath)
                runTool(apksigner.absolutePath, "verify", "--verbose", signed.absolutePath)
                if (buildType == "release") runTool(java, sealTool, "verify", signed.absolutePath)
                check(apk.delete() && signed.renameTo(apk)) { "Could not replace APK with signed Carlink build: $apk" }
                raw.delete(); aligned.delete(); signed.delete()
                runTool(python, dexTool.absolutePath, apk.absolutePath, carlinkDex.absolutePath, "--verify", "--audit-members",
                    "--sdk-dir", sdkPath, "--java", File(System.getProperty("java.home"), "bin/java${if (windows) ".exe" else ""}").absolutePath)
            }
        }
    }
    afterEvaluate {
        tasks.matching { it.name == "assemble${buildType.replaceFirstChar(Char::uppercase)}" }
            .configureEach { finalizedBy(injectTask) }
    }
}

// No implicit import. Only the two explicitly selected local runtime assets are allowed.
val credentialAssets = files(android.sourceSets.flatMap { source ->
    source.assets.directories.map { directory ->
        fileTree(directory) {
            include("**/offline-mfi/**", "**/*.pk8", "**/*.p7b", "**/*.key",
                "**/*.pem", "**/*.p12", "**/*.pfx", "**/*.jks", "**/*.keystore")
        }
    }
})
val rejectBundledCredentials by tasks.registering {
    group = "verification"
    description = "Reject unexpected credential files in APK assets."
    val filesToCheck = credentialAssets
    val allowed = localAuthenticationAssets?.let { dir ->
        listOf("identity.pk8", "certificate.p7b").map { dir.resolve("offline-mfi/$it").canonicalFile }.toSet()
    } ?: emptySet()
    inputs.files(filesToCheck)
    doLast {
        check(allowed.all { it.isFile }) { "Explicit local authentication assets are incomplete" }
        val unexpected = filesToCheck.files.filter { it.canonicalFile !in allowed }
        check(unexpected.isEmpty()) { "Unexpected credential files in APK assets" }
    }
}
tasks.named("preBuild") { dependsOn(rejectBundledCredentials) }

// Car-test packages must be standalone. Keep ordinary source/CI builds identity-free.
val verifyStandaloneAuthentication by tasks.registering {
    group = "verification"
    description = "Require the explicit runtime authentication input for a standalone car-test APK."
    val directory = localAuthenticationAssets
    doLast {
        check(directory != null) {
            "Standalone car builds require DIPLAY_AUTH_ASSETS_DIR; assembleDebug alone is source-only."
        }
        check(listOf("identity.pk8", "certificate.p7b").all {
            directory.resolve("offline-mfi/$it").let { file -> file.isFile && file.length() > 0 }
        }) { "Standalone CarPlay authentication files are missing or empty" }
    }
}
tasks.named("preBuild") { mustRunAfter(verifyStandaloneAuthentication) }
tasks.register("assembleStandaloneDebug") {
    group = "build"
    description = "Build a standalone car-test APK with explicitly provisioned authentication."
    dependsOn(verifyStandaloneAuthentication, "assembleDebug")
}
