package de.lamamc.sift.packgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import org.junit.jupiter.api.Test;

class StructureImporterTest {

    @Test
    void paletteIsMappedAndModBlockEntityDataRemoved() {
        BlockMapping mapping = BlockMapping.loadDefault();
        ImportReport report = new ImportReport();
        CompoundBinaryTag root = CompoundBinaryTag.builder()
                .put("palette", ListBinaryTag.listBinaryTag(BinaryTagTypes.COMPOUND, List.of(
                        CompoundBinaryTag.builder().putString("Name", "minecraft:stone").build(),
                        CompoundBinaryTag.builder().putString("Name", "the_sift:sift_portal")
                                .put("Properties", CompoundBinaryTag.builder().putString("axis", "x").build())
                                .build())))
                .put("blocks", ListBinaryTag.listBinaryTag(BinaryTagTypes.COMPOUND, List.of(
                        CompoundBinaryTag.builder().putInt("state", 1)
                                .put("pos", ListBinaryTag.listBinaryTag(BinaryTagTypes.INT, List.of(
                                        IntBinaryTag.intBinaryTag(0), IntBinaryTag.intBinaryTag(0),
                                        IntBinaryTag.intBinaryTag(0))))
                                .put("nbt", CompoundBinaryTag.builder().putString("id", "the_sift:sift_portal").build())
                                .build())))
                .build();

        CompoundBinaryTag result = new StructureImporter(mapping, report).rewrite("test.nbt", root);

        ListBinaryTag palette = result.getList("palette");
        assertEquals("minecraft:stone", palette.getCompound(0).getString("Name"));
        assertEquals("minecraft:air", palette.getCompound(1).getString("Name"));
        assertNull(result.getList("blocks").getCompound(0).get("nbt"));
        assertTrue(report.errors().isEmpty());
    }

    @Test
    void modPropertiesOfVanillaBlocksAreRemoved() {
        CompoundBinaryTag root = CompoundBinaryTag.builder()
                .put("palette", ListBinaryTag.listBinaryTag(BinaryTagTypes.COMPOUND, List.of(
                        CompoundBinaryTag.builder().putString("Name", "minecraft:chest")
                                .put("Properties", CompoundBinaryTag.builder()
                                        .putString("ichorlogged", "false").putString("facing", "east").build())
                                .build())))
                .build();
        CompoundBinaryTag entry = new StructureImporter(BlockMapping.loadDefault(), new ImportReport())
                .rewrite("t.nbt", root).getList("palette").getCompound(0);
        assertNull(entry.getCompound("Properties").get("ichorlogged"));
        assertEquals("east", entry.getCompound("Properties").getString("facing"));
    }
}
