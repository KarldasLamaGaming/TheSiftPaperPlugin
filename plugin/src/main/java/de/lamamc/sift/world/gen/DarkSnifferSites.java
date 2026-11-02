package de.lamamc.sift.world.gen;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Where the surface sculk of new Sift chunks wants a Dark Sniffer (SiftDeferredSnifferSpawner of the
 * original): worldgen threads add one plan per selected blob, the main thread spawns from it.
 */
public final class DarkSnifferSites {

    /** Candidate floor blocks (sculk without decoration) of one blob; one Dark Sniffer per plan. */
    public record Plan(List<int[]> floors, long seed, int attempts) {
    }

    public static final Queue<Plan> QUEUE = new ConcurrentLinkedQueue<>();

    private DarkSnifferSites() {
    }
}
