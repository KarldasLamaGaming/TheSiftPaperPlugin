package de.lamamc.sift.world.portal;

import de.lamamc.sift.core.portal.PortalFrame;
import de.lamamc.sift.core.portal.PortalState;
import java.util.ArrayList;
import java.util.List;

/**
 * A registered portal: a structure portal (leads into the Sift) or the exit of the Sift's main portal
 * (leads back). Consoles are the sonorous deepslate blocks that belong to it.
 */
public record SiftPortal(String id, Kind kind, PortalFrame frame, PortalState state, List<int[]> consoles) {

    public enum Kind {
        /** Overworld, Nether or End portal: travels into the Sift. */
        ENTRANCE,
        /** The main portal in the Sift: travels back to where the traveller came from. */
        SIFT_EXIT
    }

    public SiftPortal withState(PortalState newState) {
        return new SiftPortal(id, kind, frame, newState, consoles);
    }

    /** "id kind state | frame | x,y,z;x,y,z" */
    String serialize() {
        StringBuilder text = new StringBuilder(id + " " + kind + " " + state + " | " + frame.serialize() + " |");
        for (int i = 0; i < consoles.size(); i++) {
            int[] c = consoles.get(i);
            text.append(i == 0 ? " " : ";").append(c[0]).append(',').append(c[1]).append(',').append(c[2]);
        }
        return text.toString();
    }

    static SiftPortal parse(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 3) {
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
            return new SiftPortal(head[0], Kind.valueOf(head[1]), PortalFrame.parse(parts[1]), PortalState.valueOf(head[2]), consoles);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
