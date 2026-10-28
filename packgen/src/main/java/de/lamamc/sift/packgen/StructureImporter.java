package de.lamamc.sift.packgen;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;

/** Rewrites the block palettes of structure templates (.nbt) to vanilla stand-ins. */
public final class StructureImporter {

    private final BlockMapping mapping;
    private final ImportReport report;

    public StructureImporter(BlockMapping mapping, ImportReport report) {
        this.mapping = mapping;
        this.report = report;
    }

    public byte[] run(String path, byte[] gzipped) {
        try {
            CompoundBinaryTag root = BinaryTagIO.unlimitedReader()
                    .read(new ByteArrayInputStream(gzipped), BinaryTagIO.Compression.GZIP);
            CompoundBinaryTag result = (CompoundBinaryTag) sorted(rewrite(path, root));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BinaryTagIO.writer().write(result, out, BinaryTagIO.Compression.GZIP);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not convert structure " + path, e);
        }
    }

    /** Copies the tag with compound keys in sorted order, so repeated imports produce identical files. */
    static BinaryTag sorted(BinaryTag tag) {
        if (tag instanceof CompoundBinaryTag compound) {
            CompoundBinaryTag.Builder builder = CompoundBinaryTag.builder();
            compound.keySet().stream().sorted().forEach(key -> builder.put(key, sorted(compound.get(key))));
            return builder.build();
        }
        if (tag instanceof ListBinaryTag list) {
            List<BinaryTag> elements = new ArrayList<>();
            list.forEach(element -> elements.add(sorted(element)));
            return ListBinaryTag.listBinaryTag(list.elementType(), elements);
        }
        return tag;
    }

    CompoundBinaryTag rewrite(String path, CompoundBinaryTag root) {
        Set<Integer> customIndices = new HashSet<>();
        if (root.get("palette") instanceof ListBinaryTag palette) {
            root = root.put("palette", rewritePalette(path, palette, customIndices));
        }
        if (root.get("palettes") instanceof ListBinaryTag palettes) {
            List<BinaryTag> converted = new ArrayList<>();
            for (BinaryTag palette : palettes) {
                converted.add(rewritePalette(path, (ListBinaryTag) palette, customIndices));
            }
            root = root.put("palettes", ListBinaryTag.listBinaryTag(BinaryTagTypes.LIST, converted));
        }
        if (root.get("blocks") instanceof ListBinaryTag blocks) {
            List<BinaryTag> converted = new ArrayList<>();
            for (BinaryTag tag : blocks) {
                CompoundBinaryTag block = (CompoundBinaryTag) tag;
                // block entity data of a mod block (portal, sonorous deepslate) means nothing to the stand-in
                if (customIndices.contains(block.getInt("state")) && block.get("nbt") != null) {
                    block = block.remove("nbt");
                    report.note("Removed block entity data in structures", path);
                }
                converted.add(block);
            }
            root = root.put("blocks", ListBinaryTag.listBinaryTag(BinaryTagTypes.COMPOUND, converted));
        }
        if (root.get("entities") instanceof ListBinaryTag entities) {
            List<BinaryTag> kept = new ArrayList<>();
            for (BinaryTag tag : entities) {
                String id = ((CompoundBinaryTag) tag).getCompound("nbt").getString("id");
                if (id.startsWith(BlockMapping.MOD_NAMESPACE + ":")) {
                    report.note("Removed entities in structures", id + " in " + path);
                } else {
                    kept.add(tag);
                }
            }
            root = root.put("entities", ListBinaryTag.listBinaryTag(BinaryTagTypes.COMPOUND, kept));
        }
        return root;
    }

    /** The mod adds properties (e.g. ichorlogged) to vanilla blocks; vanilla does not know them. */
    private CompoundBinaryTag stripModProperties(String path, CompoundBinaryTag entry) {
        CompoundBinaryTag properties = entry.getCompound("Properties");
        CompoundBinaryTag cleaned = properties;
        for (String key : properties.keySet()) {
            if (mapping.isStrippedProperty(key)) {
                cleaned = cleaned.remove(key);
                report.note("Removed mod properties of vanilla blocks", key + " in " + path);
            }
        }
        if (cleaned == properties) {
            return entry;
        }
        return cleaned.isEmpty() ? entry.remove("Properties") : entry.put("Properties", cleaned);
    }

    private ListBinaryTag rewritePalette(String path, ListBinaryTag palette, Set<Integer> customIndices) {
        List<BinaryTag> converted = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            CompoundBinaryTag entry = palette.getCompound(i);
            String name = entry.getString("Name");
            if (!mapping.isMapped(name)) {
                if (name.startsWith(BlockMapping.MOD_NAMESPACE + ":")) {
                    report.error(path + ": unmapped block " + name);
                }
                converted.add(stripModProperties(path, entry));
                continue;
            }
            Map<String, String> props = new TreeMap<>();
            CompoundBinaryTag properties = entry.getCompound("Properties");
            for (String key : properties.keySet()) {
                props.put(key, properties.getString(key));
            }
            if (!mapping.survivesRotation(name)) {
                report.note("Templates with directional stand-in states (place unrotated or remap after rotating)",
                        path + ": " + name);
            }
            BlockState state = mapping.resolve(BlockState.of(name, props));
            CompoundBinaryTag.Builder newProps = CompoundBinaryTag.builder();
            state.properties().forEach((k, v) -> newProps.put(k, StringBinaryTag.stringBinaryTag(v)));
            CompoundBinaryTag.Builder newEntry = CompoundBinaryTag.builder()
                    .putString("Name", state.id());
            if (!state.properties().isEmpty()) {
                newEntry.put("Properties", newProps.build());
            }
            converted.add(newEntry.build());
            customIndices.add(i);
        }
        return ListBinaryTag.listBinaryTag(BinaryTagTypes.COMPOUND, converted);
    }
}
