package de.lamamc.sift.packgen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Imports Mielon's The Sift (Fabric jar) into {@code pack-src/}: the datapack with dimension and worldgen
 * (blocks mapped to vanilla stand-ins) and the assets of the resource pack.
 *
 * <p>Usage: {@code Main --jar <path|url> --out <pack-src> [--cache <dir>] [--vanilla-blocks <blocks.json>]}
 */
public final class Main {

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Data pack and resource pack formats of Minecraft 26.3 (version.json of the 26.3 server jar). */
    static final int DATA_FORMAT = 121;
    static final int RESOURCE_FORMAT = 97;

    private static final String DATA_PREFIX = "data/the_sift/";
    private static final String ASSET_PREFIX = "assets/the_sift/";
    /** Client-only assets that a resource pack cannot use (custom shaders, GeckoLib, custom particles). */
    private static final List<String> SKIPPED_ASSETS = List.of("shaders/", "geckolib/", "particles/", "blockstates/");

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        Path out = Path.of(require(options, "--out"));
        Path cache = Path.of(options.getOrDefault("--cache", "build/sift-cache"));
        Path jar = obtainJar(require(options, "--jar"), cache);

        BlockMapping mapping = BlockMapping.loadDefault();
        ImportReport report = new ImportReport();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            importDatapack(zip, mapping, report, out.resolve("datapack"));
            importAssets(zip, report, out.resolve("resourcepack"));
        }
        writeResolvedMapping(mapping, out.resolve("block-mapping.json"));
        if (options.containsKey("--vanilla-blocks")) {
            VanillaBlocks.load(Path.of(options.get("--vanilla-blocks")))
                    .validate(mapping, report, out.resolve("datapack/data"));
        }
        Files.writeString(out.resolve("IMPORT-REPORT.md"), report.toMarkdown("Import report: Mielon's The Sift"));

        if (!report.errors().isEmpty()) {
            report.errors().forEach(e -> System.err.println("ERROR " + e));
            System.exit(1);
        }
        System.out.println("Imported " + jar.getFileName() + " into " + out);
    }

    private static void importDatapack(ZipFile zip, BlockMapping mapping, ImportReport report, Path root)
            throws IOException {
        Map<String, JsonElement> json = new TreeMap<>();
        Map<String, byte[]> structures = new TreeMap<>();
        for (ZipEntry entry : zip.stream().toList()) {
            String name = entry.getName();
            if (entry.isDirectory() || !name.startsWith(DATA_PREFIX)) {
                if (!entry.isDirectory() && name.startsWith("data/")) {
                    report.note("Not imported in this step", name);
                }
                continue;
            }
            String path = name.substring(DATA_PREFIX.length());
            if (name.endsWith(".json")) {
                try (InputStream in = zip.getInputStream(entry)) {
                    json.put(path, JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                }
            } else if (name.endsWith(".nbt") && path.startsWith("structure/")) {
                try (InputStream in = zip.getInputStream(entry)) {
                    structures.put(path, in.readAllBytes());
                }
            } else {
                report.note("Not imported in this step", name);
            }
        }

        Path data = root.resolve("data/the_sift");
        deleteTree(data);
        for (var e : new DatapackImporter(mapping, report).run(json).entrySet()) {
            writeJson(data.resolve(e.getKey()), e.getValue());
        }
        StructureImporter structureImporter = new StructureImporter(mapping, report);
        for (var e : structures.entrySet()) {
            Path target = data.resolve(e.getKey());
            Files.createDirectories(target.getParent());
            Files.write(target, structureImporter.run(e.getKey(), e.getValue()));
        }
        writePackMeta(root, "The Sift: Dimension und Weltgenerierung (nach Mielon's The Sift, MIT)", DATA_FORMAT);
        copyLicense(zip, root);
    }

    private static void importAssets(ZipFile zip, ImportReport report, Path root) throws IOException {
        Path assets = root.resolve("assets/the_sift");
        deleteTree(assets);
        for (ZipEntry entry : zip.stream().toList()) {
            String name = entry.getName();
            if (entry.isDirectory() || !name.startsWith(ASSET_PREFIX)) {
                continue;
            }
            String path = name.substring(ASSET_PREFIX.length());
            if (SKIPPED_ASSETS.stream().anyMatch(path::startsWith)) {
                report.note("Assets left out of the resource pack (client code only)", path);
                continue;
            }
            Path target;
            if (path.equals("icon.png")) {
                target = root.resolve("pack.png");
            } else {
                target = assets.resolve(path);
            }
            Files.createDirectories(target.getParent());
            try (InputStream in = zip.getInputStream(entry)) {
                Files.write(target, in.readAllBytes());
            }
        }
        writePackMeta(root, "The Sift für Lama Kingdom (Assets: Mielon's The Sift, MIT)", RESOURCE_FORMAT);
        copyLicense(zip, root);
    }

    private static void writeResolvedMapping(BlockMapping mapping, Path target) throws IOException {
        JsonObject root = new JsonObject();
        JsonObject repurposed = new JsonObject();
        mapping.repurposed().values().forEach(entries -> entries.forEach(entry ->
                repurposed.addProperty(entry.custom().toString(), entry.vanilla().toString())));
        JsonObject substitutes = new JsonObject();
        mapping.substitutes().forEach((id, state) -> substitutes.addProperty(id, state.toString()));
        root.add("repurposed", repurposed);
        root.add("substitute", substitutes);
        writeJson(target, root);
    }

    private static void writePackMeta(Path root, String description, int format) throws IOException {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", description);
        pack.addProperty("min_format", format);
        pack.addProperty("max_format", format);
        JsonObject meta = new JsonObject();
        meta.add("pack", pack);
        writeJson(root.resolve("pack.mcmeta"), meta);
    }

    private static void copyLicense(ZipFile zip, Path root) throws IOException {
        ZipEntry license = zip.getEntry("LICENSE_the_sift");
        if (license != null) {
            try (InputStream in = zip.getInputStream(license)) {
                Files.write(root.resolve("LICENSE-the_sift.txt"), in.readAllBytes());
            }
        }
    }

    static void writeJson(Path target, JsonElement json) throws IOException {
        Files.createDirectories(target.getParent());
        Files.writeString(target, GSON.toJson(json) + "\n");
    }

    private static Path obtainJar(String location, Path cache) throws IOException, InterruptedException {
        if (!location.startsWith("https://")) {
            return Path.of(location);
        }
        String fileName = URI.create(location).getPath();
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1).replace("%27", "");
        Path target = cache.resolve(fileName);
        if (Files.isRegularFile(target)) {
            return target;
        }
        Files.createDirectories(cache);
        System.out.println("Downloading " + location);
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
            HttpResponse<Path> response = client.send(HttpRequest.newBuilder(URI.create(location)).build(),
                    HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(target);
                throw new IOException("Download failed with HTTP " + response.statusCode() + ": " + location);
            }
        }
        return target;
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

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            options.put(args[i], args[i + 1]);
        }
        return options;
    }

    private static String require(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing option " + key);
        }
        return value;
    }
}
