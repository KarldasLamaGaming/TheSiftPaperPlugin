package de.lamamc.sift.core;

import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** Pure helpers for the resource pack settings: URL template and SHA-1 values. */
public final class PackSource {

    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");

    private PackSource() {
    }

    /** Replaces {version} in the configured URL with the plugin version (the release tag without "v"). */
    public static String resolveUrl(String template, String version) {
        return template.trim().replace("{version}", version);
    }

    /** A configured SHA-1 in canonical form, or null if it is empty or not 40 hex digits. */
    public static String normalizeSha1(String value) {
        if (value == null) {
            return null;
        }
        String hex = value.trim().toLowerCase(Locale.ROOT);
        return SHA1.matcher(hex).matches() ? hex : null;
    }

    public static String hex(byte[] digest) {
        return HexFormat.of().formatHex(digest);
    }
}
