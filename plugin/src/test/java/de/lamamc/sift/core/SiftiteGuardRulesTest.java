package de.lamamc.sift.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.lamamc.sift.core.SiftiteGuardRules.Input;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The protection applies only to Siftite (protected Sift items): every case with real netherite and
 * other vanilla items is allowed, so vanilla recipes and netherite work unchanged.
 */
class SiftiteGuardRulesTest {

    private final SiftiteGuardRules rules = new SiftiteGuardRules("siftite_ingot");

    private static final Input VANILLA = Input.vanilla(); // e.g. netherite sword, netherite ingot, diamond
    private static final Input BOOK = Input.book();
    private static final Input EMPTY = Input.EMPTY;
    private static final Input SWORD = Input.sift("siftite_sword", true);
    private static final Input OTHER_SWORD = Input.sift("siftite_sword", true);
    private static final Input HELMET = Input.sift("siftite_helmet", true);
    private static final Input INGOT = Input.sift("siftite_ingot", true);
    private static final Input TEMPLATE = Input.sift("siftite_upgrade_smithing_template", true);
    private static final Input CHAROITE = Input.sift("charoite", false); // Sift item, not Siftite

    // ---- vanilla and netherite unchanged ---------------------------------------------------------

    @Test
    void vanillaRecipesWithVanillaItemsAreAllowed() {
        assertTrue(rules.recipeAllowed(false, List.of(VANILLA, VANILLA, EMPTY)));
        assertTrue(rules.recipeAllowed(false, List.of(VANILLA, CHAROITE))); // only Siftite is protected
    }

    @Test
    void netheriteSmithingAnvilAndGrindstoneAreUnchanged() {
        // diamond gear + netherite template + netherite ingot, and armor trims on netherite
        assertTrue(rules.smithingAllowed(false, false, VANILLA, VANILLA, VANILLA));
        assertTrue(rules.smithingAllowed(false, true, VANILLA, VANILLA, VANILLA));
        // netherite sword repaired with a netherite ingot, two netherite swords combined, book, rename
        assertTrue(rules.anvilAllowed(VANILLA, VANILLA));
        assertTrue(rules.anvilAllowed(VANILLA, BOOK));
        assertTrue(rules.anvilAllowed(VANILLA, EMPTY));
        assertTrue(rules.grindstoneAllowed(VANILLA, VANILLA));
        assertTrue(rules.otherStationAllowed(VANILLA, VANILLA));
    }

    // ---- Siftite is not netherite -----------------------------------------------------------------

    @Test
    void siftiteIsRefusedByVanillaRecipes() {
        assertFalse(rules.recipeAllowed(false, List.of(INGOT, INGOT, INGOT, VANILLA))); // e.g. paper-based book
        assertFalse(rules.recipeAllowed(false, List.of(SWORD, OTHER_SWORD)));           // vanilla repair_item
        assertTrue(rules.recipeAllowed(true, List.of(INGOT)));                          // Sift recipes (nuggets)
    }

    @Test
    void siftiteIsRefusedByVanillaSmithing() {
        // Siftite template or ingot in the netherite upgrade
        assertFalse(rules.smithingAllowed(false, false, TEMPLATE, VANILLA, VANILLA));
        assertFalse(rules.smithingAllowed(false, false, VANILLA, VANILLA, INGOT));
        // Siftite gear as base of a vanilla upgrade; trims are fine (they keep the item)
        assertFalse(rules.smithingAllowed(false, false, VANILLA, HELMET, VANILLA));
        assertTrue(rules.smithingAllowed(false, true, VANILLA, HELMET, VANILLA));
        // the Siftite upgrade itself
        assertTrue(rules.smithingAllowed(true, false, TEMPLATE, VANILLA, INGOT));
    }

    @Test
    void siftiteIsNotRepairedOrMergedLikeNetherite() {
        assertFalse(rules.anvilAllowed(SWORD, VANILLA));      // netherite ingot (or any base item) as repair
        assertFalse(rules.anvilAllowed(VANILLA, SWORD));      // netherite sword absorbing a Siftite sword
        assertFalse(rules.anvilAllowed(VANILLA, INGOT));      // netherite gear repaired with Siftite
        assertFalse(rules.anvilAllowed(SWORD, HELMET));
        assertTrue(rules.anvilAllowed(SWORD, INGOT));         // repair with Siftite ingot
        assertTrue(rules.anvilAllowed(SWORD, OTHER_SWORD));   // combine two Siftite swords
        assertTrue(rules.anvilAllowed(SWORD, BOOK));
        assertTrue(rules.anvilAllowed(SWORD, EMPTY));         // rename
    }

    @Test
    void grindstoneAndOtherStations() {
        assertTrue(rules.grindstoneAllowed(SWORD, EMPTY));
        assertTrue(rules.grindstoneAllowed(SWORD, OTHER_SWORD));
        assertFalse(rules.grindstoneAllowed(SWORD, VANILLA));
        assertFalse(rules.otherStationAllowed(INGOT, VANILLA)); // e.g. trading Siftite as paper
        assertTrue(rules.otherStationAllowed(CHAROITE));
    }
}
