package de.lamamc.sift.core.mob;

/**
 * The numbers of the Echo Golem (EchoGolemEntity of the original 1.1.1). Times in ticks, distances in blocks,
 * speeds as navigation multipliers on its movement speed.
 *
 * @param pickupTakeTick     the soul block becomes air (pickup animation)
 * @param pickupEndTick      the golem starts carrying
 * @param bowGiveTick        a bonded golem hands the soul block to its player
 * @param bowEndTick         a bow ends (also when the Singer took the block)
 * @param bowTimeout         an unanswered bow towards a Singer ends
 * @param sitTicks           sitting after it lost its soul block
 * @param ignoreCanyonTicks  a canyon it just delivered from is ignored
 * @param blockedCanyonTicks a canyon it could not get into is avoided
 * @param emptyCanyonTicks   a canyon without reachable souls is avoided
 * @param landmarkInterval   how often it looks for a canyon while exploring
 * @param landmarkRange      how far it looks for canyons
 * @param distantCanyon      canyons farther away than this only steer its heading
 * @param scanInterval       how often it scans for soul blocks
 * @param scanRadius         scan radius (horizontal) without a target; vertical {@code scanHeight}
 * @param targetScanRadius   scan radius while it has a target; vertical {@code targetScanHeight}
 * @param handReach          gap between body and soul block it can pick up from
 * @param approachOffset     the approach point lies this far outside the canyon entrance
 * @param singerInterval     how often an unbonded carrier looks for (or calls) a Singer
 * @param singerSearch       radius of that search
 * @param ownerRange         a bonded carrier walks to its player within this range, otherwise patrols
 * @param bowDistance        it bows to its player within this distance
 * @param patrolLeash        patrol stays this close to its anchor
 * @param patrolMin          shortest patrol step
 * @param patrolMax          longest patrol step
 */
public record EchoGolemRules(int pickupTakeTick, int pickupEndTick, int bowGiveTick, int bowEndTick, int bowTimeout,
                             int sitTicks, int ignoreCanyonTicks, int blockedCanyonTicks, int emptyCanyonTicks,
                             int landmarkInterval, double landmarkRange, double distantCanyon, int scanInterval, double scanRadius,
                             double scanHeight, double targetScanRadius, double targetScanHeight, double handReach,
                             double approachOffset, int singerInterval, double singerSearch, double ownerRange, double bowDistance,
                             double patrolLeash, int patrolMin, int patrolMax, Speeds speeds) {

    /** Navigation speed multipliers per route phase. */
    public record Speeds(double explore, double approach, double enter, double descend, double soul, double exit, double owner,
                         double singer, double patrol, double patrolReturn) {

        public static Speeds defaults() {
            return new Speeds(0.78, 0.88, 0.84, 0.82, 0.9, 0.88, 0.82, 0.86, 0.74, 0.76);
        }
    }

    public static EchoGolemRules defaults() {
        return new EchoGolemRules(18, 34, 26, 48, 100, 600, 600, 2400, 6000, 45, 1024, 96, 12, 36, 26, 16, 12, 1.75, 7, 30, 52, 64,
                3, 64, 14, 60, Speeds.defaults());
    }

    /** Penalty for a soul block it failed to reach for the {@code failures}-th time (100, 400, then 2400 ticks). */
    public static int failurePenalty(int failures) {
        return failures <= 1 ? 100 : failures == 2 ? 400 : 2400;
    }

    /**
     * On the surface (EchoGolemEntity#hasReachedSurface): at most 2 blocks below the canyon entrance and either
     * under the open sky or within 12 blocks of the entrance. Without an entrance only the sky counts.
     */
    public static boolean onSurface(double y, boolean sky, double entranceY, double horizontalToEntrance, boolean hasEntrance) {
        if (!hasEntrance) {
            return sky;
        }
        return y >= entranceY - 2 && (sky || horizontalToEntrance <= 12);
    }
}
