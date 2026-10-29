package de.lamamc.sift.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionCheckTest {

    @Test
    void sameVersionAndHotfixesAreCompatible() {
        assertTrue(VersionCheck.compatible("26.3", "26.3"));
        assertTrue(VersionCheck.compatible("26.3", "26.3.1"));
    }

    @Test
    void otherVersionsAreNot() {
        assertFalse(VersionCheck.compatible("26.3", "26.2"));
        assertFalse(VersionCheck.compatible("26.3", "26.4"));
        assertFalse(VersionCheck.compatible("26.3", "26.30"));
        assertFalse(VersionCheck.compatible("26.3", "26.3-pre-2"));
        assertFalse(VersionCheck.compatible("26.3", null));
    }
}
