package de.lamamc.sift.core.rift;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * One pass over the open rifts (alpha.23.1). The pass may open new rifts (a counter rift while someone
 * travels) or close them (commands, lifetime): it works on a snapshot, removes the finished ones afterwards,
 * and leaves rifts opened during the pass for the next tick.
 */
public final class RiftSweep {

    private RiftSweep() {
    }

    /** Visits a snapshot of {@code rifts}; those {@code visit} answers false for are removed afterwards. */
    public static <T> void run(List<T> rifts, Predicate<T> visit) {
        List<T> done = new ArrayList<>();
        for (T rift : List.copyOf(rifts)) {
            if (!visit.test(rift)) {
                done.add(rift);
            }
        }
        rifts.removeAll(done);
    }
}
