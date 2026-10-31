package de.lamamc.sift.core.mob;

import java.util.random.RandomGenerator;

/**
 * Numbers of the Blub's hopping, timers and taming (BlubEntity of the original 1.1.1), with the values
 * from config.yml mobs.blub.
 */
public record BlubRules(double jumpPower, double fastJumpPower, double climbJumpPower, double fastSpeed,
                        int[] landingDelays, double hopVelocity, int tameOneIn, int angerTicks, double angerRadius,
                        int[] flapInterval, int flapTicks, int[] idleSoundInterval, double feedHeal) {

    public static BlubRules defaults() {
        return new BlubRules(0.3, 0.36, 0.5, 1.1, new int[] {8, 6, 4}, 0.2, 3, 600, 16, new int[] {140, 540}, 20,
                new int[] {180, 540}, 4);
    }

    /** Speed of the hopping animation for the horizontal speed in blocks per tick. */
    public static double hoppingAnimationSpeed(double horizontalSpeed) {
        return Math.max(0.72, Math.min(2.35, 0.72 + horizontalSpeed * 7.0));
    }

    /** Moving for the animation: in the air or still sliding (horizontal speed² above 0.001). */
    public static boolean movingForAnimation(boolean onGround, double horizontalSpeedSquared) {
        return !onGround || horizontalSpeedSquared > 0.001;
    }

    /** Jump height: higher when the path climbs or the Blub bumps into a block, a bit higher when fast. */
    public double jumpPower(double speedModifier, boolean climbing) {
        if (climbing) {
            return climbJumpPower;
        }
        return speedModifier > fastSpeed ? fastJumpPower : jumpPower;
    }

    /** Ticks on the ground after landing before the next hop (faster = shorter). */
    public int landingDelay(double speedModifier) {
        return speedModifier >= 1.15 ? landingDelays[2] : speedModifier >= 1.05 ? landingDelays[1] : landingDelays[0];
    }

    /** Horizontal velocity of a hop (blocks per tick) for the movement speed attribute and speed modifier. */
    public double hopSpeed(double movementSpeed, double speedModifier) {
        return hopVelocity * (movementSpeed / 0.3) * speedModifier;
    }

    public boolean rollTame(RandomGenerator random) {
        return tameOneIn <= 1 || random.nextInt(tameOneIn) == 0;
    }

    public int nextFlap(RandomGenerator random) {
        return between(flapInterval, random);
    }

    public int nextIdleSound(RandomGenerator random) {
        return between(idleSoundInterval, random);
    }

    private static int between(int[] range, RandomGenerator random) {
        return range[0] + (range[1] > range[0] ? random.nextInt(range[1] - range[0] + 1) : 0);
    }
}
