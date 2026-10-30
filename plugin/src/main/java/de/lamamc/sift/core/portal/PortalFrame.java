package de.lamamc.sift.core.portal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A rectangular portal frame standing upright along x or z (PortalFrameScanner of the original): border
 * of frame blocks, 3 to 23 blocks per side, interior free. Scanning starts at any frame block.
 */
public record PortalFrame(Axis axis, int minX, int minY, int minZ, int width, int height) {

    public enum Axis { X, Z }

    public static final int MIN_SIZE = 3;
    public static final int MAX_SCAN = 23;

    /** What the scanner needs to know about the blocks. */
    public interface Grid {
        boolean isFrame(int x, int y, int z);

        /** Free interior: air-like or already a portal surface. */
        boolean isOpen(int x, int y, int z);
    }

    /** The frame through this block, along x first, then z. */
    public static Optional<PortalFrame> scan(Grid grid, int x, int y, int z) {
        Optional<PortalFrame> onX = scan(grid, x, y, z, Axis.X);
        return onX.isPresent() ? onX : scan(grid, x, y, z, Axis.Z);
    }

    private static Optional<PortalFrame> scan(Grid grid, int x, int y, int z, Axis axis) {
        int dx = axis == Axis.X ? 1 : 0;
        int dz = axis == Axis.Z ? 1 : 0;
        int ax = x;
        int ay = y;
        int az = z;
        int steps = 0;
        while (grid.isFrame(ax, ay - 1, az)) {
            ay--;
            if (++steps > MAX_SCAN) {
                return Optional.empty();
            }
        }
        steps = 0;
        while (grid.isFrame(ax - dx, ay, az - dz)) {
            ax -= dx;
            az -= dz;
            if (++steps > MAX_SCAN) {
                return Optional.empty();
            }
        }
        int width = 1;
        while (grid.isFrame(ax + dx * width, ay, az + dz * width)) {
            if (++width > MAX_SCAN) {
                return Optional.empty();
            }
        }
        int height = 1;
        while (grid.isFrame(ax, ay + height, az)) {
            if (++height > MAX_SCAN) {
                return Optional.empty();
            }
        }
        if (width < MIN_SIZE || height < MIN_SIZE) {
            return Optional.empty();
        }
        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                int bx = ax + dx * w;
                int by = ay + h;
                int bz = az + dz * w;
                boolean border = w == 0 || w == width - 1 || h == 0 || h == height - 1;
                if (border ? !grid.isFrame(bx, by, bz) : !grid.isOpen(bx, by, bz)) {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(new PortalFrame(axis, ax, ay, az, width, height));
    }

    /** Interior blocks, from the edges to the centre (the order the original fills the surface in). */
    public List<int[]> interiorEdgesFirst() {
        List<int[]> interior = new ArrayList<>();
        for (int w = 1; w < width - 1; w++) {
            for (int h = 1; h < height - 1; h++) {
                interior.add(position(w, h));
            }
        }
        double centerW = (width - 1) * 0.5;
        double centerH = (height - 1) * 0.5;
        interior.sort((a, b) -> {
            int c = Double.compare(distance(b, centerW, centerH), distance(a, centerW, centerH));
            return c != 0 ? c : Integer.compare(b[1], a[1]);
        });
        return interior;
    }

    public int interiorSize() {
        return (width - 2) * (height - 2);
    }

    /** Whether a block lies inside the frame (the portal surface). */
    public boolean containsInterior(int x, int y, int z) {
        int w = axis == Axis.X ? x - minX : z - minZ;
        int depth = axis == Axis.X ? z - minZ : x - minX;
        int h = y - minY;
        return depth == 0 && w > 0 && w < width - 1 && h > 0 && h < height - 1;
    }

    /** Whether a block is part of the border. */
    public boolean containsFrame(int x, int y, int z) {
        int w = axis == Axis.X ? x - minX : z - minZ;
        int depth = axis == Axis.X ? z - minZ : x - minX;
        int h = y - minY;
        return depth == 0 && w >= 0 && w < width && h >= 0 && h < height
                && (w == 0 || w == width - 1 || h == 0 || h == height - 1);
    }

    /** Centre of the bottom of the opening (where entities step through), block coordinates as doubles. */
    public double[] bottomCenter() {
        double middle = (width - 1) * 0.5;
        return new double[] {minX + 0.5 + (axis == Axis.X ? middle : 0), minY + 1.0, minZ + 0.5 + (axis == Axis.Z ? middle : 0)};
    }

    private int[] position(int w, int h) {
        return new int[] {minX + (axis == Axis.X ? w : 0), minY + h, minZ + (axis == Axis.Z ? w : 0)};
    }

    private double distance(int[] pos, double centerW, double centerH) {
        int w = axis == Axis.X ? pos[0] - minX : pos[2] - minZ;
        return Math.hypot(w - centerW, pos[1] - minY - centerH);
    }

    /** "axis minX minY minZ width height" for storage. */
    public String serialize() {
        return axis + " " + minX + " " + minY + " " + minZ + " " + width + " " + height;
    }

    public static PortalFrame parse(String text) {
        String[] p = text.trim().split(" ");
        return new PortalFrame(Axis.valueOf(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                Integer.parseInt(p[4]), Integer.parseInt(p[5]));
    }
}
