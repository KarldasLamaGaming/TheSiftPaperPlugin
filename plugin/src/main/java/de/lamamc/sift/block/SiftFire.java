package de.lamamc.sift.block;

import de.lamamc.sift.core.FireRules;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.plugin.Plugin;

/**
 * Lets Sift blocks burn like the original (FlammableBlockRegistry: willow logs 5/5, planks 5/20, foliage
 * 30/60, sapling 60/100). The server does not know the stand-in states as flammable, so for every fire
 * near such blocks the plugin runs the burn and spread part of the vanilla fire tick itself, and keeps
 * the fire from going out while a flammable Sift block is next to it.
 */
public final class SiftFire implements Listener {

    private static final BlockFace[] NEIGHBOURS = {BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN, BlockFace.UP,
            BlockFace.NORTH, BlockFace.SOUTH};

    private final Plugin plugin;
    private final CustomBlockRegistry registry;
    private final BlockSettings settings;
    private final Random random = new Random();
    private final Set<Block> tracked = new HashSet<>();

    public SiftFire(Plugin plugin, CustomBlockRegistry registry, BlockSettings settings) {
        this.plugin = plugin;
        this.registry = registry;
        this.settings = settings;
    }

    private int burnOdds(Block block) {
        CustomBlock custom = registry.at(block);
        BlockSettings.Flammability f = custom == null ? null : settings.flammability(custom.id());
        return f == null ? 0 : f.burn();
    }

    private int igniteOdds(Block block) {
        CustomBlock custom = registry.at(block);
        BlockSettings.Flammability f = custom == null ? null : settings.flammability(custom.id());
        return f == null ? 0 : f.ignite();
    }

    private boolean nextToFlammable(Block fire) {
        for (BlockFace face : NEIGHBOURS) {
            if (burnOdds(fire.getRelative(face)) > 0 || igniteOdds(fire.getRelative(face)) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Flammable Sift blocks within reach of the fire's spread (3 × 6 × 3 box, one block around it). */
    private boolean flammableNearby(Block fire) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 5; dy++) {
                    if (igniteOdds(fire.getRelative(dx, dy, dz)) > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---- tracking ------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        trackLater(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (event.getNewState().getType() == Material.FIRE) {
            trackLater(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == Material.FIRE) {
            trackLater(event.getBlockPlaced());
        }
    }

    private void trackLater(Block block) {
        Bukkit.getScheduler().runTask(plugin, () -> track(block));
    }

    private void track(Block block) {
        if (block.getType() == Material.FIRE && flammableNearby(block) && tracked.add(block)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> tick(block), FireRules.tickDelay(random));
        }
    }

    /** Vanilla removes fire without flammable neighbours; Sift neighbours count as flammable. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.FIRE && !rainOn(block) && nextToFlammable(block)) {
            event.setCancelled(true);
            track(block);
        }
    }

    // ---- the fire tick ---------------------------------------------------------------------------------

    private void tick(Block fire) {
        if (!fire.getWorld().isChunkLoaded(fire.getX() >> 4, fire.getZ() >> 4) || fire.getType() != Material.FIRE
                || !flammableNearby(fire)) {
            tracked.remove(fire);
            return;
        }
        if (canSpreadFire(fire)) {
            int age = fire.getBlockData() instanceof Ageable ageable ? ageable.getAge() : 0;
            for (BlockFace face : NEIGHBOURS) {
                int chance = face == BlockFace.UP || face == BlockFace.DOWN ? 250 : 300;
                burnOut(fire, fire.getRelative(face), chance, age);
            }
            spread(fire, age);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> tick(fire), FireRules.tickDelay(random));
    }

    private void burnOut(Block fire, Block target, int chance, int age) {
        int odds = burnOdds(target);
        FireRules.BurnOut result = FireRules.burnOut(random, chance, odds, age, rainOn(target));
        if (result == FireRules.BurnOut.NOTHING) {
            return;
        }
        BlockBurnEvent event = new BlockBurnEvent(target, fire);
        if (!event.callEvent()) {
            return;
        }
        if (result == FireRules.BurnOut.FIRE) {
            setFire(target, FireRules.nextAge(random, age));
        } else {
            target.setType(Material.AIR);
        }
    }

    private void spread(Block fire, int age) {
        int difficulty = fire.getWorld().getDifficulty().ordinal();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 4; dy++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    Block target = fire.getRelative(dx, dy, dz);
                    if (!target.getType().isAir()) {
                        continue;
                    }
                    int odds = 0;
                    for (BlockFace face : NEIGHBOURS) {
                        odds = Math.max(odds, igniteOdds(target.getRelative(face)));
                    }
                    if (FireRules.spreads(random, odds, age, dy, difficulty, rainOn(target))) {
                        BlockIgniteEvent event = new BlockIgniteEvent(target, BlockIgniteEvent.IgniteCause.SPREAD, fire);
                        if (event.callEvent()) {
                            setFire(target, FireRules.nextAge(random, age));
                        }
                    }
                }
            }
        }
    }

    private void setFire(Block block, int age) {
        block.setType(Material.FIRE);
        if (block.getBlockData() instanceof Ageable ageable) {
            ageable.setAge(age);
            block.setBlockData(ageable);
        }
        track(block);
    }

    /** ServerLevel#canSpreadFireAround: fire_spread_radius_around_player -1 = everywhere. */
    private static boolean canSpreadFire(Block fire) {
        World world = fire.getWorld();
        Integer radius = world.getGameRuleValue(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER);
        if (radius == null || radius == -1) {
            return true;
        }
        double max = (double) radius * radius;
        return world.getPlayers().stream().anyMatch(p -> p.getLocation().distanceSquared(fire.getLocation()) <= max);
    }

    private static boolean rainOn(Block block) {
        World world = block.getWorld();
        if (!world.hasStorm()) {
            return false;
        }
        for (BlockFace face : new BlockFace[] {BlockFace.SELF, BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH}) {
            Block b = block.getRelative(face);
            if (world.getHighestBlockYAt(b.getX(), b.getZ()) <= b.getY()) {
                return true;
            }
        }
        return false;
    }
}
