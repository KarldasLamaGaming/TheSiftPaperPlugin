package de.lamamc.sift.bootstrap;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Copies the datapack shipped in the plugin jar to a folder on disk, from where Paper discovers it.
 * The folder is replaced completely on every start, it only ever holds generated content.
 */
public final class DatapackInstaller {

    /** Folder inside the plugin jar that holds the datapack (pack.mcmeta and data/). */
    public static final String JAR_FOLDER = "datapack";

    private DatapackInstaller() {
    }

    /** Copies {@code source/JAR_FOLDER} (a jar file or, in tests, a directory) to {@code target}; returns the file count. */
    public static int install(Path source, Path target) throws IOException {
        deleteTree(target);
        if (Files.isDirectory(source)) {
            return copyTree(source.resolve(JAR_FOLDER), target);
        }
        try (FileSystem jar = FileSystems.newFileSystem(URI.create("jar:" + source.toUri()), Map.of())) {
            return copyTree(jar.getPath("/" + JAR_FOLDER), target);
        }
    }

    private static int copyTree(Path from, Path to) throws IOException {
        if (!Files.isRegularFile(from.resolve("pack.mcmeta"))) {
            throw new IOException("No datapack (pack.mcmeta) at " + from);
        }
        int count = 0;
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                // resolve via string: the jar file system and the default one do not mix paths
                Path dest = to.resolve(from.relativize(file).toString());
                Files.createDirectories(dest.getParent());
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                count++;
            }
        }
        return count;
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
