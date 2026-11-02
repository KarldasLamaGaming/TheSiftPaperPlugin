package de.lamamc.sift.core.mob;

/**
 * Rules for the invisible light block a carrying Echo Golem takes along: it is only moved when the golem's head
 * block changes, only set into air, and only removed where our own light block (registered, same level) still
 * stands, so a block someone placed meanwhile is never touched.
 */
public final class GolemLightRules {

    /** What to do with the light this tick. */
    public record Plan(boolean removeOld, boolean placeNew) {
        public static final Plan NOTHING = new Plan(false, false);
    }

    private GolemLightRules() {
    }

    /**
     * @param current   the block the light stands in now, or null
     * @param wanted    the block it should stand in now (head block while carrying), or null for no light
     */
    public static Plan plan(long[] current, long[] wanted) {
        if (current == null && wanted == null) {
            return Plan.NOTHING;
        }
        if (current != null && wanted != null && current[0] == wanted[0] && current[1] == wanted[1] && current[2] == wanted[2]) {
            return Plan.NOTHING;
        }
        return new Plan(current != null, wanted != null);
    }

    /** A light block may only be set into air (never water, plants or other blocks). */
    public static boolean canPlaceInto(boolean air) {
        return air;
    }

    /** A light block may only be removed when it is still ours: a light block of our level at a registered position. */
    public static boolean mayRemove(boolean isLightBlock, int level, int ourLevel, boolean registered) {
        return isLightBlock && level == ourLevel && registered;
    }

    /** Head block of a golem standing at y: the soul light sits 2.125 blocks above its feet (original locator). */
    public static int headBlockY(double feetY, double scale) {
        return (int) Math.floor(feetY + 2.125 * scale);
    }
}
