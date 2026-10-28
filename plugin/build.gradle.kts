plugins {
    id("sift.plugin-conventions")
    alias(libs.plugins.run.paper)
}

base {
    archivesName.set("TheSift")
}

tasks.runServer {
    minecraftVersion(libs.versions.minecraft.get())
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
}
