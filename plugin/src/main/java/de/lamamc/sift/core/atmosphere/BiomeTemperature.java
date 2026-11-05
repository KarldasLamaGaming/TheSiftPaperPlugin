package de.lamamc.sift.core.atmosphere;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Ichor snowfall (alpha.32). The original lets it snow in the whole of {@code ichor_snowy_peaks} by replacing
 * vanilla's temperature check (ServerLevelWeatherMixin). Vanilla only snows below a temperature of 0.15, and
 * the biome has 0.18, which reaches that only above about y 104. Lowering the biome's temperature in the
 * installed datapack lets vanilla snow there at any height, client snowfall included.
 */
public final class BiomeTemperature {

    private BiomeTemperature() {
    }

    /** The biome JSON with {@code temperature} set, or the input unchanged if it already has that value. */
    public static String set(String biomeJson, double temperature) {
        JsonObject biome = JsonParser.parseString(biomeJson).getAsJsonObject();
        if (biome.has("temperature") && biome.get("temperature").getAsDouble() == temperature) {
            return biomeJson;
        }
        biome.addProperty("temperature", temperature);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(biome);
    }

    /** Vanilla's snow check (Biome#coldEnoughToSnow) without the height adjustment: below 0.15. */
    public static boolean snows(double temperature) {
        return temperature < 0.15;
    }
}
