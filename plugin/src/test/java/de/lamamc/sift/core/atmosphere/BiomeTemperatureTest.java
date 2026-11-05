package de.lamamc.sift.core.atmosphere;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BiomeTemperatureTest {

    @Test
    void shippedSnowyPeaksDoNotSnowLowDownUntilCooled() throws Exception {
        String json = Files.readString(Path.of("../pack-src/datapack/data/the_sift/worldgen/biome/ichor_snowy_peaks.json"));
        double shipped = JsonParser.parseString(json).getAsJsonObject().get("temperature").getAsDouble();
        assertFalse(BiomeTemperature.snows(shipped), "0.18: vanilla snows only high up");
        String cooled = BiomeTemperature.set(json, 0.0);
        var biome = JsonParser.parseString(cooled).getAsJsonObject();
        assertEquals(0.0, biome.get("temperature").getAsDouble());
        assertTrue(BiomeTemperature.snows(0.0));
        assertTrue(biome.get("has_precipitation").getAsBoolean(), "everything else stays");
        assertEquals(JsonParser.parseString(json).getAsJsonObject().get("effects"), biome.get("effects"));
        assertEquals(cooled, BiomeTemperature.set(cooled, 0.0), "unchanged when already set");
    }
}
