package de.lamamc.sift.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatapackInstallerTest {

    @TempDir
    Path tmp;

    private Path jarWith(String... entries) throws IOException {
        Path jar = tmp.resolve("plugin.jar");
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(entry.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return jar;
    }

    @Test
    void copiesTheDatapackFolderAndReplacesOldContent() throws IOException {
        Path jar = jarWith("paper-plugin.yml", "datapack/pack.mcmeta",
                "datapack/data/the_sift/dimension/the_sift.json");
        Path target = tmp.resolve("out");
        Files.createDirectories(target.resolve("data/old"));
        Files.writeString(target.resolve("data/old/stale.json"), "{}");

        int count = DatapackInstaller.install(jar, target);

        assertEquals(2, count);
        assertTrue(Files.isRegularFile(target.resolve("pack.mcmeta")));
        assertTrue(Files.isRegularFile(target.resolve("data/the_sift/dimension/the_sift.json")));
        assertFalse(Files.exists(target.resolve("data/old")));
        assertFalse(Files.exists(target.resolve("paper-plugin.yml")));
    }

    @Test
    void failsWithoutPackMeta() throws IOException {
        Path jar = jarWith("datapack/data/x.json");
        assertThrows(IOException.class, () -> DatapackInstaller.install(jar, tmp.resolve("out")));
    }
}
