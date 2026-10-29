package de.lamamc.sift.core.worldgen;

import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Mirror and rotation of structure template positions and directional properties, as vanilla
 * StructureTemplate does it (mirror first, then rotation around a pivot). Used for templates whose
 * stand-in states must not be rotated by vanilla (willow trees, abandoned portals).
 */
public record TemplateTransform(Mirror mirror, Rotation rotation, int pivotX, int pivotZ) {

    public enum Mirror { NONE, LEFT_RIGHT, FRONT_BACK }

    public enum Rotation { NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90 }

    public static final TemplateTransform IDENTITY = new TemplateTransform(Mirror.NONE, Rotation.NONE, 0, 0);

    /** Random rotation and LEFT_RIGHT mirror half of the time, like the original features. */
    public static TemplateTransform random(Random random, int pivotX, int pivotZ) {
        Rotation rotation = Rotation.values()[random.nextInt(4)];
        Mirror mirror = random.nextBoolean() ? Mirror.NONE : Mirror.LEFT_RIGHT;
        return new TemplateTransform(mirror, rotation, pivotX, pivotZ);
    }

    /** Template-relative position → offset from the template origin (x, y, z). */
    public int[] apply(int x, int y, int z) {
        switch (mirror) {
            case LEFT_RIGHT -> z = -z;
            case FRONT_BACK -> x = -x;
            default -> {
            }
        }
        return switch (rotation) {
            case COUNTERCLOCKWISE_90 -> new int[] {pivotX - pivotZ + z, y, pivotX + pivotZ - x};
            case CLOCKWISE_90 -> new int[] {pivotX + pivotZ - z, y, pivotZ - pivotX + x};
            case CLOCKWISE_180 -> new int[] {pivotX + pivotX - x, y, pivotZ + pivotZ - z};
            case NONE -> new int[] {x, y, z};
        };
    }

    /** "axis" of pillars: quarter turns swap x and z. */
    public String axis(String axis) {
        boolean quarter = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
        if (!quarter) {
            return axis;
        }
        return switch (axis) {
            case "x" -> "z";
            case "z" -> "x";
            default -> axis;
        };
    }

    /** A horizontal direction ("north", "east", "south", "west") after mirror and rotation. */
    public String direction(String direction) {
        String d = direction;
        if (mirror == Mirror.LEFT_RIGHT) {
            d = switch (d) {
                case "north" -> "south";
                case "south" -> "north";
                default -> d;
            };
        } else if (mirror == Mirror.FRONT_BACK) {
            d = switch (d) {
                case "east" -> "west";
                case "west" -> "east";
                default -> d;
            };
        }
        int turns = switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
        String[] order = {"north", "east", "south", "west"};
        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(d)) {
                return order[(i + turns) % 4];
            }
        }
        return d;
    }

    /** Custom block properties after the transform (axis, horizontal "facing"). */
    public Map<String, String> properties(Map<String, String> properties) {
        Map<String, String> result = new TreeMap<>(properties);
        result.computeIfPresent("axis", (k, v) -> axis(v));
        result.computeIfPresent("facing", (k, v) -> direction(v));
        return result;
    }
}
