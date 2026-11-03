package de.lamamc.sift.world.portal;

import de.lamamc.sift.core.portal.PortalFrame;
import de.lamamc.sift.core.portal.PortalState;
import java.util.ArrayList;
import java.util.List;

/**
 * A registered portal: a structure portal (leads into the Sift) or the exit of the Sift's main portal
 * (leads back). Consoles are the sonorous deepslate blocks that belong to it.
 */
public record SiftPortal(String id, Kind kind, PortalFrame frame, PortalState state, List<int[]> consoles, int[] tune) {

    /** Without an own tune (the fixed one applies). */
    public SiftPortal(String id, Kind kind, PortalFrame frame, PortalState state, List<int[]> consoles) {
        this(id, kind, frame, state, consoles, null);
    }

    public enum Kind {
        /** Overworld, Nether or End portal: travels into the Sift. */
        ENTRANCE,
        /** The main portal in the Sift: travels back to where the traveller came from. */
        SIFT_EXIT,
        /** Portal in the centre of an Ancient City (like the original): travels into the Sift, own longer tune. */
        ANCIENT_CITY
    }

    public SiftPortal withState(PortalState newState) {
        return new SiftPortal(id, kind, frame, newState, consoles, tune);
    }

    public SiftPortal withTune(int[] newTune) {
        return new SiftPortal(id, kind, frame, state, consoles, newTune == null ? null : newTune.clone());
    }

    /** "id kind state | frame | x,y,z;x,y,z" and, with an own tune, " | tune 3-1-…" */
    String serialize() {
        StringBuilder text = new StringBuilder(id + " " + kind + " " + state + " | " + frame.serialize() + " |");
        for (int i = 0; i < consoles.size(); i++) {
            int[] c = consoles.get(i);
            text.append(i == 0 ? " " : ";").append(c[0]).append(',').append(c[1]).append(',').append(c[2]);
        }
        if (tune != null) {
            text.append(" | tune ").append(de.lamamc.sift.core.portal.ConsoleSounds.text(tune));
        }
        return text.toString();
    }

    static SiftPortal parse(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 3 && parts.length != 4) {
            return null;
        }
        try {
            String[] head = parts[0].trim().split(" ");
            List<int[]> consoles = new ArrayList<>();
            for (String c : parts[2].trim().split(";")) {
                if (!c.isBlank()) {
                    String[] xyz = c.split(",");
                    consoles.add(new int[] {Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])});
                }
            }
            int[] tune = null;
            if (parts.length == 4 && parts[3].trim().startsWith("tune ")) {
                tune = java.util.Arrays.stream(parts[3].trim().substring(5).split("-")).mapToInt(Integer::parseInt).toArray();
                if (!de.lamamc.sift.core.portal.PortalTunes.valid(tune)) {
                    tune = null;
                }
            }
            return new SiftPortal(head[0], Kind.valueOf(head[1]), PortalFrame.parse(parts[1]), PortalState.valueOf(head[2]), consoles,
                    tune);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
