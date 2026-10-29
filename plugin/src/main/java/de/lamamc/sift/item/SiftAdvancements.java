package de.lamamc.sift.item;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.world.SiftWorlds;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Grants the advancements of the original that its code awards (criterion "event" of type impossible),
 * as far as the features exist, and unlocks the Sift recipes in the recipe book.
 */
public final class SiftAdvancements implements Listener {

    private final CustomBlockRegistry blocks;
    private final SiftRecipes recipes;
    private final boolean discoverRecipes;
    private final Logger logger;

    public SiftAdvancements(CustomBlockRegistry blocks, SiftRecipes recipes, boolean discoverRecipes, Logger logger) {
        this.blocks = blocks;
        this.recipes = recipes;
        this.discoverRecipes = discoverRecipes;
        this.logger = logger;
    }

    /** Awards {@code the_sift:<path>}; unknown advancements are logged once per call. */
    public void award(Player player, String path) {
        Advancement advancement = Bukkit.getAdvancement(new NamespacedKey("the_sift", path));
        if (advancement == null) {
            logger.warning("Advancement the_sift:" + path + " is missing (datapack not loaded?)");
            return;
        }
        var progress = player.getAdvancementProgress(advancement);
        progress.getRemainingCriteria().forEach(progress::awardCriteria);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        CustomBlock block = blocks.at(event.getBlock());
        if (block == null) {
            return;
        }
        switch (block.id()) {
            case "siftslate_charoite_ore" -> award(event.getPlayer(), "the_sift/kinda_purpleish");
            case "siftslate_siftite_ore" -> award(event.getPlayer(), "the_sift/sifted_nugget");
            default -> {
            }
        }
    }

    /** "Spicewood": setting wood on fire in the Sift (vanilla wood or Sift willow wood, as in the original). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onIgnite(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null || event.getItem() == null
                || !SiftWorlds.isSift(clicked.getWorld())) {
            return;
        }
        Material held = event.getItem().getType();
        if ((held == Material.FLINT_AND_STEEL || held == Material.FIRE_CHARGE) && isWood(clicked)
                && clicked.getRelative(event.getBlockFace()).isReplaceable()) {
            award(event.getPlayer(), "the_sift/spicewood");
        }
    }

    private boolean isWood(Block block) {
        CustomBlock custom = blocks.at(block);
        if (custom != null) {
            return custom.id().contains("willow") && !custom.id().contains("foliage") && !custom.id().contains("sapling");
        }
        return WOOD_TAGS.stream().map(key -> Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(key), Material.class))
                .anyMatch(tag -> tag != null && tag.isTagged(block.getType()));
    }

    private static final java.util.List<String> WOOD_TAGS = java.util.List.of("logs", "planks", "wooden_stairs",
            "wooden_slabs", "wooden_fences", "wooden_doors", "wooden_trapdoors", "wooden_buttons",
            "wooden_pressure_plates", "wooden_shelves");

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (discoverRecipes) {
            event.getPlayer().discoverRecipes(recipes.keys());
        }
    }
}
