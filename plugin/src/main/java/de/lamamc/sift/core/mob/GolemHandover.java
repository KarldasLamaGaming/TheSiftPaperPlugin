package de.lamamc.sift.core.mob;

/**
 * Whether a bonded Echo Golem may hand its soul block to a player now (completePlayerSoulDelivery of the original,
 * with the checks of alpha.19.2): the player must be its owner, online, in the same world, within reach and in
 * survival or adventure mode. Otherwise the golem keeps the block and carries on. Whether the block goes into the
 * inventory or (inventory full) in front of the player's feet is decided by the inventory itself; both count as
 * handed over.
 */
public final class GolemHandover {

    public enum Mode { SURVIVAL, ADVENTURE, CREATIVE, SPECTATOR }

    public enum Result {
        GIVE, KEEP_OFFLINE, KEEP_OTHER_WORLD, KEEP_FAR, KEEP_MODE, KEEP_NOT_OWNER, NOTHING;

        /** German reason for /sift debug golems. */
        public String reason() {
            return switch (this) {
                case GIVE -> "übergeben";
                case KEEP_OFFLINE -> "Spieler offline, behalten";
                case KEEP_OTHER_WORLD -> "Spieler in einer anderen Welt, behalten";
                case KEEP_FAR -> "Spieler zu weit weg, behalten";
                case KEEP_MODE -> "Spieler im Kreativ- oder Zuschauermodus, behalten";
                case KEEP_NOT_OWNER -> "nicht der gebundene Spieler, behalten";
                case NOTHING -> "trägt nichts";
            };
        }
    }

    private GolemHandover() {
    }

    public static Result decide(boolean hasSoul, boolean online, boolean isOwner, boolean sameWorld, double distance,
                                double reach, Mode mode) {
        if (!hasSoul) {
            return Result.NOTHING;
        }
        if (!online) {
            return Result.KEEP_OFFLINE;
        }
        if (!isOwner) {
            return Result.KEEP_NOT_OWNER;
        }
        if (!sameWorld) {
            return Result.KEEP_OTHER_WORLD;
        }
        if (mode == Mode.CREATIVE || mode == Mode.SPECTATOR) {
            return Result.KEEP_MODE;
        }
        return distance <= reach ? Result.GIVE : Result.KEEP_FAR;
    }

    /** Where the block ended up after a GIVE: in the inventory, or dropped in front of the player (inventory full). */
    public static String outcome(boolean leftOver) {
        return leftOver ? "Inventar voll, vor die Füße gelegt" : "ins Inventar übergeben";
    }
}
