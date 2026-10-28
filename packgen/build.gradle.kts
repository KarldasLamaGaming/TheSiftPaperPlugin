import java.security.MessageDigest

plugins {
    id("sift.java-conventions")
    application
}

dependencies {
    implementation(libs.gson)
    implementation(libs.snakeyaml)
    implementation(libs.adventure.nbt)
}

application {
    mainClass.set("de.lamamc.sift.packgen.Main")
}

// Imports the mod assets and data into pack-src/. The jar is not part of the repository:
//   ./gradlew :packgen:importSift -PsiftJar=<path or https URL>
// Without -PsiftJar the Modrinth download of Mielon's The Sift 1.1.1 for 26.3 is used.
tasks.register<JavaExec>("importSift") {
    group = "sift"
    description = "Imports Mielon's The Sift into pack-src/ (resource pack and datapack sources)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    workingDir = rootDir
    val jar = providers.gradleProperty("siftJar")
        .orElse("https://cdn.modrinth.com/data/j0IfTxBq/versions/JqpmzEMO/mielon%27s-the-sift-1.1.1-mc26.3-fabric.jar")
    val blocksReport = providers.gradleProperty("vanillaBlocks")
    argumentProviders.add(CommandLineArgumentProvider {
        val args = mutableListOf("--jar", jar.get(), "--out", rootDir.resolve("pack-src").path,
            "--cache", layout.buildDirectory.dir("sift-cache").get().asFile.path)
        if (blocksReport.isPresent) {
            args += listOf("--vanilla-blocks", blocksReport.get())
        }
        args
    })
}

// The generated datapack is committed; the tests check it, so they have to see changes to it
tasks.test {
    inputs.dir(rootDir.resolve("pack-src")).withPropertyName("packSrc").optional()
    systemProperty("sift.packSrc", rootDir.resolve("pack-src").path)
}

// The resource pack players download: imported assets + generated overrides (pack-src/resourcepack)
// and hand-written files (pack-src/resourcepack-overlay, e.g. de_de.json). Reproducible, so the SHA-1
// only changes with the content.
val resourcePack = tasks.register<Zip>("resourcePack") {
    group = "sift"
    description = "Builds the resource pack zip and its SHA-1"
    archiveFileName.set("the-sift-pack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("pack"))
    from(rootDir.resolve("pack-src/resourcepack"))
    from(rootDir.resolve("pack-src/resourcepack-overlay"))
    duplicatesStrategy = DuplicatesStrategy.FAIL
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    doLast {
        val zip = archiveFile.get().asFile
        val digest = MessageDigest.getInstance("SHA-1")
        zip.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        zip.resolveSibling(zip.name + ".sha1").writeText(hex + "\n")
        logger.lifecycle("Resource pack ${zip.name}: SHA-1 $hex")
    }
}

tasks.assemble {
    dependsOn(resourcePack)
}
