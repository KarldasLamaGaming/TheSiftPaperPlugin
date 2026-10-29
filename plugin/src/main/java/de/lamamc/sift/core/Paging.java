package de.lamamc.sift.core;

/** Page arithmetic for menus: pages are 0-based, an empty list still has one (empty) page. */
public final class Paging {

    private Paging() {
    }

    public static int pageCount(int total, int perPage) {
        return Math.max(1, (total + perPage - 1) / perPage);
    }

    /** The page clamped into the valid range. */
    public static int clamp(int page, int total, int perPage) {
        return Math.max(0, Math.min(page, pageCount(total, perPage) - 1));
    }

    /** Index into the full list for a slot on a page, or -1 if the slot is empty. */
    public static int index(int page, int slot, int total, int perPage) {
        if (slot < 0 || slot >= perPage) {
            return -1;
        }
        int index = clamp(page, total, perPage) * perPage + slot;
        return index < total ? index : -1;
    }
}
