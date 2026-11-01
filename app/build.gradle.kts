import java.security.MessageDigest
import java.util.zip.GZIPInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val stockfishVersion = "19"
val stockfishArchiveName = "stockfish-android-arm64-universal.tar.gz"
val stockfishSha256 = "ebb24051aa4a222b4daaf049b882ecf1163d370c128fe02316602643f4d5e426"
val stockfishAssets = layout.buildDirectory.dir("generated/stockfish-assets")
val stockfishLibraries = layout.buildDirectory.dir("generated/stockfish-libraries")
val stockfishArchive = layout.buildDirectory.file("downloads/$stockfishArchiveName")

val prepareStockfish19 by tasks.registering {
    inputs.property("stockfishVersion", stockfishVersion)
    outputs.dirs(stockfishAssets, stockfishLibraries)

    doLast {
        val archive = stockfishArchive.get().asFile
        archive.parentFile.mkdirs()

        fun sha256(file: java.io.File): String =
            MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }

        if (!archive.exists() || sha256(archive) != stockfishSha256) {
            archive.delete()
            val url = uri(
                "https://github.com/official-stockfish/Stockfish/releases/download/sf_$stockfishVersion/$stockfishArchiveName"
            ).toURL()
            url.openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 180_000
            }.getInputStream().use { input ->
                archive.outputStream().use { output -> input.copyTo(output) }
            }
        }
        check(sha256(archive) == stockfishSha256) {
            "Stockfish $stockfishVersion download failed SHA-256 verification"
        }

        val destination = stockfishAssets.get().asFile
        project.delete(destination)
        val libraryDirectory = stockfishLibraries.get().dir("arm64-v8a").asFile
        project.delete(stockfishLibraries)
        libraryDirectory.mkdirs()
        val wantedFiles = setOf(
            "stockfish/stockfish-android-arm64-universal",
            "stockfish/Copying.txt"
        )

        GZIPInputStream(archive.inputStream().buffered()).use { gzip ->
            val header = ByteArray(512)
            while (true) {
                val read = gzip.readNBytes(header, 0, header.size)
                if (read == 0) break
                if (read != header.size) error("Invalid Stockfish archive")

                val name = header.copyOfRange(0, 100)
                    .takeWhile { it != 0.toByte() }
                    .toByteArray()
                    .toString(Charsets.UTF_8)
                val sizeText = header.copyOfRange(124, 136)
                    .takeWhile { it != 0.toByte() && it != ' '.code.toByte() }
                    .toByteArray()
                    .toString(Charsets.US_ASCII)
                    .trim()
                val size = sizeText.toLongOrNull(8) ?: 0L
                val remaining = size

                if (name in wantedFiles) {
                    val target = if (name.endsWith("universal")) {
                        libraryDirectory.resolve("libstockfish.so")
                    } else {
                        destination.resolve("stockfish19/Copying.txt").apply { parentFile.mkdirs() }
                    }
                    target.outputStream().buffered().use { output ->
                        var copied = 0L
                        val buffer = ByteArray(64 * 1024)
                        while (copied < size) {
                            val count = gzip.read(buffer, 0, minOf(buffer.size.toLong(), size - copied).toInt())
                            if (count < 0) error("Truncated Stockfish archive entry: $name")
                            output.write(buffer, 0, count)
                            copied += count
                        }
                    }
                    if (name.endsWith("universal")) {
                        check(target.setExecutable(true, false)) {
                            "Could not mark the packaged Stockfish engine executable"
                        }
                    }
                } else {
                    var skipped = 0L
                    while (skipped < remaining) {
                        val count = gzip.skip(remaining - skipped)
                        if (count <= 0) {
                            if (gzip.read() < 0) error("Truncated Stockfish archive")
                            skipped++
                        } else {
                            skipped += count
                        }
                    }
                }

                val padding = (512 - size % 512) % 512
                var skippedPadding = 0L
                while (skippedPadding < padding) {
                    val count = gzip.skip(padding - skippedPadding)
                    if (count <= 0) {
                        if (gzip.read() < 0) error("Truncated Stockfish archive padding")
                        skippedPadding++
                    } else {
                        skippedPadding += count
                    }
                }
            }
        }
        check(libraryDirectory.resolve("libstockfish.so").isFile) {
            "Stockfish engine binary was not found in the release archive"
        }
    }
}

android {
    namespace = "com.example.chessinsight"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.chessinsight"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets.getByName("main").assets.srcDir(stockfishAssets)
    sourceSets.getByName("main").jniLibs.srcDir(stockfishLibraries)

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libstockfish.so"
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareStockfish19)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.github.bhlangonijr:chesslib:1.3.4")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
