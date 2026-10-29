package de.lamamc.sift.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Decides whether a station may use the items in it, so that protected Sift items (Siftite) are never
 * treated like the vanilla item they are built on: no vanilla recipe, no vanilla smithing upgrade, no
 * anvil repair with vanilla materials, no trades. Every decision for inputs without protected items is
 * "allowed", so vanilla items (real netherite included) and vanilla recipes behave as before.
 */
public final class SiftiteGuardRules {

    /** An item in a station slot. {@code siftId} is null for vanilla items and empty slots. */
    public record Input(String siftId, boolean isProtected, boolean enchantedBook, boolean empty) {

        public static final Input EMPTY = new Input(null, false, false, true);

        public static Input vanilla() {
            return new Input(null, false, false, false);
        }

        public static Input book() {
            return new Input(null, false, true, false);
        }

        public static Input sift(String id, boolean isProtected) {
            return new Input(id, isProtected, false, false);
        }
    }

    private final String repairItem;

    /** @param repairItem Sift id of the item that repairs Siftite gear on the anvil */
    public SiftiteGuardRules(String repairItem) {
        this.repairItem = repairItem;
    }

    private static boolean anyProtected(List<Input> inputs) {
        return inputs.stream().filter(Objects::nonNull).anyMatch(Input::isProtected);
    }

    /** Crafting table, crafter, furnaces: only Sift recipes may use protected items. */
    public boolean recipeAllowed(boolean siftRecipe, List<Input> inputs) {
        return siftRecipe || !anyProtected(inputs);
    }

    /**
     * Smithing table: Sift recipes decide themselves (their base only accepts vanilla netherite).
     * Vanilla recipes never take a protected template or addition; a protected base only for armor trims,
     * which keep the item as it is.
     */
    public boolean smithingAllowed(boolean siftRecipe, boolean trimRecipe, Input template, Input base, Input addition) {
        if (siftRecipe) {
            return true;
        }
        if (template.isProtected() || addition.isProtected()) {
            return false;
        }
        return !base.isProtected() || trimRecipe;
    }

    /**
     * Anvil: renaming and enchanted books work on protected items; combining needs two of the same Sift
     * item, repairing needs the Sift repair item. Protected items never repair or merge into vanilla items.
     */
    public boolean anvilAllowed(Input left, Input right) {
        if (!left.isProtected() && !right.isProtected()) {
            return true;
        }
        if (!left.isProtected()) {
            return false; // a vanilla item on the left would consume or absorb the Sift item
        }
        if (right.empty() || right.enchantedBook()) {
            return true;
        }
        return Objects.equals(left.siftId(), right.siftId()) || repairItem.equals(right.siftId());
    }

    /** Grindstone: one item alone (disenchanting) is fine; two only if they are the same Sift item. */
    public boolean grindstoneAllowed(Input first, Input second) {
        if (!first.isProtected() && !second.isProtected()) {
            return true;
        }
        if (first.empty() || second.empty()) {
            return true;
        }
        return Objects.equals(first.siftId(), second.siftId());
    }

    /** Stonecutter, loom, cartography table, villager trades: nothing of the Sift uses protected items there. */
    public boolean otherStationAllowed(Input... inputs) {
        return !anyProtected(Arrays.asList(inputs));
    }
}
