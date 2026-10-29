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
 * <p>Usage: {@code Main --jar <path|url> --out <pack-src> [--cache <dir>] [--vanilla-blocks <blocks.json>]
 * [--minecraft <version>]}. The vanilla client jar of that version (default 26.3) is downloaded from
 * Mojang into the cache; it provides the vanilla blockstates that the pack overrides.
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
        Path clientJar = obtainClientJar(options.getOrDefault("--minecraft", "26.3"), cache);
        try (ZipFile zip = new ZipFile(jar.toFile()); ZipFile client = new ZipFile(clientJar.toFile())) {
            importDatapack(zip, mapping, report, out.resolve("datapack"), out.resolve("item-data"),
                    german(out.resolve("resourcepack-overlay/assets/the_sift/lang/de_de.json")));
            Path pack = out.resolve("resourcepack");
            importAssets(zip, report, pack);
            writeBlockstateOverrides(zip, client, mapping, report, pack);
            new PackValidator(pack, path -> client.getEntry(path) != null, report).validate();
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

    private static void importDatapack(ZipFile zip, BlockMapping mapping, ImportReport report, Path root,
                                       Path itemData, Map<String, String> german) throws IOException {
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
        for (var e : new DatapackImporter(mapping, report, biomeEffects()).run(json).entrySet()) {
            writeJson(data.resolve(e.getKey()), e.getValue());
        }
        // recipes and item tags for the plugin; chest loot, advancements and songs into the datapack
        ItemDataImporter items = new ItemDataImporter(mapping, german, report);
        for (var e : items.datapackFiles(json).entrySet()) {
            writeJson(data.resolve(e.getKey()), e.getValue());
        }
        deleteTree(itemData);
        for (var e : items.pluginFiles(json).entrySet()) {
            writeJson(itemData.resolve(e.getKey()), e.getValue());
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

    /** The hand-written German lang file (pack-src/resourcepack-overlay). */
    private static Map<String, String> german(Path langFile) throws IOException {
        Map<String, String> german = new TreeMap<>();
        if (Files.isRegularFile(langFile)) {
            JsonParser.parseString(Files.readString(langFile)).getAsJsonObject().entrySet()
                    .forEach(e -> german.put(e.getKey(), e.getValue().getAsString()));
        }
        return german;
    }

    /** "biome-effects" of worldgen-overrides.yml. */
    @SuppressWarnings("unchecked")
    static Map<String, String> biomeEffects() {
        try (InputStream in = Main.class.getResourceAsStream("/worldgen-overrides.yml")) {
            if (in == null) {
                return Map.of();
            }
            Map<String, Object> root = new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(
                    new org.yaml.snakeyaml.LoaderOptions())).load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            Map<String, String> effects = new TreeMap<>();
            ((Map<String, Object>) root.getOrDefault("biome-effects", Map.of()))
                    .forEach((k, v) -> effects.put(k, String.valueOf(v)));
            return effects;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static void writeBlockstateOverrides(ZipFile mod, ZipFile client, BlockMapping mapping,
                                                 ImportReport report, Path pack) throws IOException {
        deleteTree(pack.resolve("assets/minecraft"));
        BlockstateOverrides overrides = new BlockstateOverrides(mapping,
                id -> readJsonEntry(mod, ASSET_PREFIX + "blockstates/" + id + ".json"), report);
        Map<String, JsonElement> files = overrides.generate(
                block -> readJsonEntry(client, "assets/minecraft/blockstates/" + block + ".json"));
        for (var e : files.entrySet()) {
            writeJson(pack.resolve(e.getKey()), e.getValue());
        }
    }

    private static JsonObject readJsonEntry(ZipFile zip, String name) {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            return null;
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** The vanilla client jar of a version, via Mojang's version manifest. */
    private static Path obtainClientJar(String version, Path cache) throws IOException, InterruptedException {
        Path target = cache.resolve("client-" + version + ".jar");
        if (Files.isRegularFile(target)) {
            return target;
        }
        String manifest = httpGet("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
        String versionUrl = null;
        for (JsonElement v : JsonParser.parseString(manifest).getAsJsonObject().getAsJsonArray("versions")) {
            if (v.getAsJsonObject().get("id").getAsString().equals(version)) {
                versionUrl = v.getAsJsonObject().get("url").getAsString();
            }
        }
        if (versionUrl == null) {
            throw new IOException("Minecraft version " + version + " not in Mojang's version manifest");
        }
        String clientUrl = JsonParser.parseString(httpGet(versionUrl)).getAsJsonObject()
                .getAsJsonObject("downloads").getAsJsonObject("client").get("url").getAsString();
        return download(clientUrl, target);
    }

    private static String httpGet(String url) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + ": " + url);
            }
            return response.body();
        }
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
        return Files.isRegularFile(target) ? target : download(location, target);
    }

    private static Path download(String url, Path target) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        System.out.println("Downloading " + url);
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
            HttpResponse<Path> response = client.send(HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofFile(partial));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(partial);
                throw new IOException("Download failed with HTTP " + response.statusCode() + ": " + url);
            }
        }
        Files.move(partial, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
