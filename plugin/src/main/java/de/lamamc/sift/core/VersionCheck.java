package de.lamamc.sift.core;

/**
 * Whether the server runs the Minecraft version the datapack was built for. Worldgen JSON and the data
 * pack format change between versions, so a mismatch would stop the server while loading registries.
 * Hotfix releases of the same version (26.3.1 for 26.3) keep the data format and are accepted.
 */
public final class VersionCheck {

    private VersionCheck() {
    }

    public static boolean compatible(String builtFor, String running) {
        if (builtFor == null || running == null) {
            return false;
        }
        String expected = builtFor.trim();
        String actual = running.trim();
        return actual.equals(expected) || actual.startsWith(expected + ".");
    }
}
