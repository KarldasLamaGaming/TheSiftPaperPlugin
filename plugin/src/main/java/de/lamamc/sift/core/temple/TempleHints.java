package de.lamamc.sift.core.temple;

/**
 * Words for the way to a temple (phase 4, alpha.41b): the eight compass directions in German and a rounded
 * distance, for the rumour notes in chests and the Sift compass. Minecraft: north is -z, east is +x.
 */
public final class TempleHints {

    private static final String[] DIRECTIONS = {"Norden", "Nordosten", "Osten", "Südosten", "Süden", "Südwesten", "Westen",
            "Nordwesten"};

    private TempleHints() {
    }

    /** The compass direction from (fromX, fromZ) to (toX, toZ); "hier" when both are the same column. */
    public static String direction(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        if (dx == 0 && dz == 0) {
            return "hier";
        }
        // angle clockwise from north: north (0, -1) = 0°, east (1, 0) = 90°
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = (int) Math.floorMod(Math.round(degrees / 45.0), 8L);
        return DIRECTIONS[sector];
    }

    /** The distance rounded to a multiple of step (at least step), so a note does not give the exact spot. */
    public static int roundedDistance(double fromX, double fromZ, double toX, double toZ, int step) {
        double distance = Math.hypot(toX - fromX, toZ - fromZ);
        if (step <= 1) {
            return (int) Math.round(distance);
        }
        return (int) Math.max(step, Math.round(distance / step) * (long) step);
    }
}
