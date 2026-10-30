package de.lamamc.sift.core.portal;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The portal shrine as a list of block roles relative to its corner (x, z 0..14; y 0 = floor): a 15 × 15
 * floor, a frame of reinforced deepslate (7 wide, 8 high, opening 5 × 6) along x at the back, eight consoles
 * in two rows of four in front of it with note blocks on top, corner pillars and low walls. The palette of
 * each dimension turns roles into blocks; the overworld ruin loses some wall and pillar blocks.
 */
public final class ShrineLayout {

    public static final int SIZE = 15;
    /** Free space above the floor that the builder clears. */
    public static final int CLEAR_HEIGHT = 10;
    public static final int FRAME_X = 4;
    public static final int FRAME_Z = 10;
    public static final int FRAME_WIDTH = 7;
    public static final int FRAME_HEIGHT = 8;
    private static final int[] CONSOLE_X = {4, 6, 8, 10};
    private static final int[] CONSOLE_Z = {4, 6};

    public enum Role {
        FLOOR, FLOOR_ALT, EDGE, PILLAR, PILLAR_TOP, WALL, DECOR, FRAME, CONSOLE, NOTE_BLOCK
    }

    public record Placement(int x, int y, int z, Role role) {
    }

    private ShrineLayout() {
    }

    /** All placements; {@code ruined} drops a share of wall and pillar blocks (deterministic per seed). */
    public static List<Placement> build(boolean ruined, long seed) {
        Random random = new Random(seed);
        List<Placement> blocks = new ArrayList<>();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
                Role floor = edge ? Role.EDGE : random.nextInt(5) == 0 ? Role.DECOR : (x + z) % 2 == 0 ? Role.FLOOR : Role.FLOOR_ALT;
                blocks.add(new Placement(x, 0, z, floor));
            }
        }
        // corner pillars
        for (int[] c : new int[][] {{0, 0}, {SIZE - 1, 0}, {0, SIZE - 1}, {SIZE - 1, SIZE - 1}}) {
            int height = ruined ? 2 + random.nextInt(3) : 4;
            for (int y = 1; y <= height; y++) {
                blocks.add(new Placement(c[0], y, c[1], Role.PILLAR));
            }
            if (!ruined || height == 4) {
                blocks.add(new Placement(c[0], height + 1, c[1], Role.PILLAR_TOP));
            }
        }
        // low walls: two high at the back, one high at the sides
        for (int x = 1; x < SIZE - 1; x++) {
            for (int y = 1; y <= 2; y++) {
                if (!ruined || random.nextInt(4) != 0) {
                    blocks.add(new Placement(x, y, SIZE - 1, Role.WALL));
                }
            }
        }
        for (int z = 2; z < SIZE - 2; z++) {
            for (int x : new int[] {0, SIZE - 1}) {
                if (!ruined || random.nextInt(3) != 0) {
                    blocks.add(new Placement(x, 1, z, Role.WALL));
                }
            }
        }
        // the frame, along x
        for (int w = 0; w < FRAME_WIDTH; w++) {
            for (int h = 0; h < FRAME_HEIGHT; h++) {
                if (w == 0 || w == FRAME_WIDTH - 1 || h == 0 || h == FRAME_HEIGHT - 1) {
                    blocks.add(new Placement(FRAME_X + w, 1 + h, FRAME_Z, Role.FRAME));
                }
            }
        }
        // consoles with a note block on top
        for (int z : CONSOLE_Z) {
            for (int x : CONSOLE_X) {
                blocks.add(new Placement(x, 1, z, Role.CONSOLE));
                blocks.add(new Placement(x, 2, z, Role.NOTE_BLOCK));
            }
        }
        return blocks;
    }

    /** The frame relative to the shrine corner. */
    public static PortalFrame frame() {
        return new PortalFrame(PortalFrame.Axis.X, FRAME_X, 1, FRAME_Z, FRAME_WIDTH, FRAME_HEIGHT);
    }

    /** The frame of a shrine whose corner (floor level) is at x, y, z. */
    public static PortalFrame frameAt(int x, int y, int z) {
        return new PortalFrame(PortalFrame.Axis.X, x + FRAME_X, y + 1, z + FRAME_Z, FRAME_WIDTH, FRAME_HEIGHT);
    }

    /** Console positions relative to the shrine corner. */
    public static List<int[]> consoles() {
        List<int[]> consoles = new ArrayList<>();
        for (int z : CONSOLE_Z) {
            for (int x : CONSOLE_X) {
                consoles.add(new int[] {x, 1, z});
            }
        }
        return consoles;
    }
}
