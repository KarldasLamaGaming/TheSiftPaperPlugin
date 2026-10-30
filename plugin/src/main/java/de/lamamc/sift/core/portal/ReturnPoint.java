package de.lamamc.sift.core.portal;

import java.util.Locale;

/** Where an entity entered the Sift (world key and position with view), to bring it back there. */
public record ReturnPoint(String world, double x, double y, double z, float yaw, float pitch) {

    public String serialize() {
        return String.format(Locale.ROOT, "%s %.3f %.3f %.3f %.1f %.1f", world, x, y, z, yaw, pitch);
    }

    /** The stored point, or null for missing or broken text. */
    public static ReturnPoint parse(String text) {
        if (text == null) {
            return null;
        }
        String[] p = text.trim().split(" ");
        if (p.length != 6) {
            return null;
        }
        try {
            return new ReturnPoint(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
