package de.lamamc.sift.core.mob;

/**
 * What a right-click on an Echo Golem does (mobInteract of the original EchoGolemEntity): an echo shard binds an
 * unbonded golem in any state (searching, carrying, sitting, bowing) and is used up; on a bonded golem it does
 * nothing and is kept. Any other click makes a carrying golem drop its soul block, but a bonded golem only
 * listens to its own player.
 */
public final class GolemBinding {

    public enum Result { BIND, ALREADY_BONDED, RELEASE_SOUL, NONE }

    private GolemBinding() {
    }

    /**
     * @param echoShard  the clicking hand holds a vanilla echo shard (not a Sift item on an echo shard base)
     * @param bonded     the golem already has a player
     * @param byOwner    the clicking player is that player
     * @param carrying   the golem carries a soul block
     */
    public static Result decide(boolean echoShard, boolean bonded, boolean byOwner, boolean carrying) {
        if (echoShard) {
            return bonded ? Result.ALREADY_BONDED : Result.BIND;
        }
        if (carrying && (!bonded || byOwner)) {
            return Result.RELEASE_SOUL;
        }
        return Result.NONE;
    }

    /** Whether the echo shard is used up (never in creative mode, only when binding). */
    public static boolean consumesShard(Result result, boolean creative) {
        return result == Result.BIND && !creative;
    }
}
