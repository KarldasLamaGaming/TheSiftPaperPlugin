package de.lamamc.sift.core.mob;

import java.util.HashMap;
import java.util.Map;

/**
 * Soul blocks the golems failed to reach (SoulFailureMemory of the original, shared by all golems): each
 * failure makes the block wait longer (100, 400, then 2400 ticks, at least the given minimum); 12000 ticks after
 * that the failures are forgotten. Positions are packed longs.
 */
public final class SoulFailureMemory {

    private record Entry(int failures, long retryAfter, long forgetAfter) {
    }

    private static final int MAX_FAILURES = 4;
    private static final long FORGET = 12000;

    private final Map<Long, Entry> entries = new HashMap<>();

    public void fail(long pos, long now, int minimumPenalty) {
        Entry previous = entries.get(pos);
        int failures = previous != null && previous.forgetAfter() > now ? Math.min(MAX_FAILURES, previous.failures() + 1) : 1;
        long retry = now + Math.max(minimumPenalty, EchoGolemRules.failurePenalty(failures));
        entries.put(pos, new Entry(failures, retry, retry + FORGET));
    }

    /** Still waiting after a failure. */
    public boolean rejected(long pos, long now) {
        Entry entry = entries.get(pos);
        if (entry == null) {
            return false;
        }
        if (entry.forgetAfter() <= now) {
            entries.remove(pos);
            return false;
        }
        return entry.retryAfter() > now;
    }

    public void forget(long pos) {
        entries.remove(pos);
    }

    public int failures(long pos) {
        Entry entry = entries.get(pos);
        return entry == null ? 0 : entry.failures();
    }

    public void purge(long now) {
        entries.values().removeIf(entry -> entry.forgetAfter() <= now);
    }
}
