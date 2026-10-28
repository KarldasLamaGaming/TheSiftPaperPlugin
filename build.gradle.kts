allprojects {
    group = "de.lamamc.sift"
    // the release workflow passes the tag without "v" (e.g. -PreleaseVersion=0.1.0-alpha.1); the plugin
    // builds the default resource pack URL from this version
    version = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-SNAPSHOT")
}
