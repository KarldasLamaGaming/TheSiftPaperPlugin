package de.lamamc.sift.core.temple;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * The block plan of a temple (phase 4, alpha.41a), relative to its centre: x and z from -20 to 20, y 0 = the level
 * players stand on (the floor is y -1). A decayed siftslate temple: square court with broken outer walls and corner
 * towers, a round arena (radius 12) with the Seelen-Altar in the middle and broken pillars around it, a hall in the
 * south with the Sculk Enchanter and two chests, and two sealed chambers (east: wither, west: warden) that stay empty
 * until their trials come (option B, after the go-live). Blocks: Sift ids, "air", or vanilla states "minecraft:…".
 * The decay comes from the seed and the site, so every temple looks a little different and is the same every time.
 */
public final class TempleLayout {

    public static final int RADIUS = 20;
    /** Height of the cleared air above the floor. */
    public static final int CLEAR_HEIGHT = 12;
    public static final int ARENA_RADIUS = 12;

    public record Pos(int x, int y, int z) {
    }

    /** Inclusive box. */
    public record Box(Pos min, Pos max) {

        public boolean contains(int x, int y, int z) {
            return x >= min.x && x <= max.x && y >= min.y && y <= max.y && z >= min.z && z <= max.z;
        }
    }

    /**
     * @param blocks   position -> block (later entries of the plan already replaced earlier ones)
     * @param altar    the block players right-click to wake the boss (alpha.42)
     * @param chambers "wither" and "warden" -> their inner, empty space
     */
    public record Layout(Map<Pos, String> blocks, Pos altar, Pos enchanter, List<Pos> chests, Map<String, Box> chambers) {
    }

    /** Sift ids the temple is built from (protected until the boss is defeated). */
    public static final List<String> PALETTE = List.of("siftslate_bricks", "chiseled_siftslate", "reinforced_siftslate",
            "cobbled_siftslate", "siftslate_growth", "soul_block", "sculk_enchanter");

    private TempleLayout() {
    }

    public static Layout build(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        Map<Pos, String> b = new LinkedHashMap<>();
        // floor, slightly decayed; the arena rim, the soul ring and the altar base
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double r = Math.sqrt(x * x + z * z);
                double roll = random.nextDouble();
                String floor = roll < 0.10 ? "cobbled_siftslate" : roll < 0.16 ? "siftslate_growth" : "siftslate_bricks";
                if (r >= 11.5 && r < 12.5) {
                    floor = "chiseled_siftslate";
                } else if (r >= 7.5 && r < 8.3 && (x + z) % 2 == 0) {
                    floor = "soul_block";
                }
                b.put(new Pos(x, -1, z), floor);
            }
        }
        // outer wall: broken heights, gaps, corner towers, open entrance in the south
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                boolean edge = Math.abs(x) == RADIUS || Math.abs(z) == RADIUS;
                if (!edge) {
                    continue;
                }
                boolean corner = Math.abs(x) == RADIUS && Math.abs(z) == RADIUS;
                if (z == RADIUS && Math.abs(x) <= 3) {
                    continue; // entrance
                }
                int height = corner ? 8 : random.nextDouble() < 0.12 ? 0 : 2 + random.nextInt(4);
                for (int y = 0; y < height; y++) {
                    b.put(new Pos(x, y, z), corner ? "chiseled_siftslate" : y == height - 1 && random.nextDouble() < 0.3
                            ? "cobbled_siftslate" : "siftslate_bricks");
                }
            }
        }
        // broken pillars around the arena
        for (int k = 0; k < 8; k++) {
            double angle = Math.toRadians(22.5 + 45 * k);
            int x = (int) Math.round(Math.cos(angle) * 14.5);
            int z = (int) Math.round(Math.sin(angle) * 14.5);
            int height = 3 + random.nextInt(5);
            for (int y = 0; y < height; y++) {
                b.put(new Pos(x, y, z), y == height - 1 ? "cobbled_siftslate" : "chiseled_siftslate");
            }
        }
        // the Seelen-Altar
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                b.put(new Pos(x, 0, z), "reinforced_siftslate");
            }
        }
        b.put(new Pos(0, 1, 0), "chiseled_siftslate");
        Pos altar = new Pos(0, 2, 0);
        b.put(altar, "soul_block");
        // sealed chambers (east: wither, west: warden), empty inside
        Map<String, Box> chambers = new LinkedHashMap<>();
        chambers.put("wither", chamber(b, 1));
        chambers.put("warden", chamber(b, -1));
        // the hall in the south: Sculk Enchanter on a dais, two chests
        for (int x = -1; x <= 1; x++) {
            for (int z = 16; z <= 18; z++) {
                b.put(new Pos(x, -1, z), "chiseled_siftslate");
            }
        }
        Pos enchanter = new Pos(0, 0, 17);
        b.put(enchanter, "sculk_enchanter");
        List<Pos> chests = List.of(new Pos(-3, 0, 18), new Pos(3, 0, 18));
        for (Pos chest : chests) {
            b.put(chest, "minecraft:chest[facing=north]");
        }
        return new Layout(b, altar, enchanter, chests, chambers);
    }

    /** A sealed box of reinforced siftslate, x 13..19 (side 1) or -19..-13 (side -1), z -3..3, y -1..5. */
    private static Box chamber(Map<Pos, String> b, int side) {
        int x1 = side > 0 ? 13 : -19;
        int x2 = side > 0 ? 19 : -13;
        for (int x = x1; x <= x2; x++) {
            for (int y = -1; y <= 5; y++) {
                for (int z = -3; z <= 3; z++) {
                    boolean shell = x == x1 || x == x2 || y == -1 || y == 5 || z == -3 || z == 3;
                    b.put(new Pos(x, y, z), shell ? "reinforced_siftslate" : "air");
                }
            }
        }
        // the seal on the side facing the arena
        int sealX = side > 0 ? x1 : x2;
        b.put(new Pos(sealX, 2, 0), "chiseled_siftslate");
        b.put(new Pos(sealX, 1, 0), "chiseled_siftslate");
        b.put(new Pos(sealX, 3, 0), "chiseled_siftslate");
        return new Box(new Pos(x1 + 1, 0, -2), new Pos(x2 - 1, 4, 2));
    }
}
