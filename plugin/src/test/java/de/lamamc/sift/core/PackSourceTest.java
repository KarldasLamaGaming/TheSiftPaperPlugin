package de.lamamc.sift.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PackSourceTest {

    @Test
    void urlGetsTheVersion() {
        assertEquals("https://github.com/x/y/releases/download/v0.1.0-alpha.1/the-sift-pack.zip",
                PackSource.resolveUrl(" https://github.com/x/y/releases/download/v{version}/the-sift-pack.zip ",
                        "0.1.0-alpha.1"));
    }

    @Test
    void sha1MustBeFortyHexDigits() {
        assertEquals("3235a34947808b2c51bb3f6b74623724d9cee153",
                PackSource.normalizeSha1(" 3235A34947808B2C51BB3F6B74623724D9CEE153 "));
        assertNull(PackSource.normalizeSha1(""));
        assertNull(PackSource.normalizeSha1("abc"));
        assertNull(PackSource.normalizeSha1(null));
    }

    @Test
    void hexIsLowerCase() {
        assertEquals("00ff10", PackSource.hex(new byte[] {0, (byte) 0xff, 0x10}));
    }
}
