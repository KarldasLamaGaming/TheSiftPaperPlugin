package de.lamamc.sift.world.portal;

import de.lamamc.sift.core.portal.PortalFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * The visible, walkable part of an open portal: invisible light blocks fill the opening (they glow like the
 * original's portal block and mark the surface), one item display shows the mist across the opening.
 * Opening fills from the edges to the centre, closing empties from the centre outwards (PortalGrowth).
 */
public final class PortalSurface {

    public static final NamespacedKey DISPLAY_KEY = new NamespacedKey("the_sift", "portal_surface");
    static final Key OPEN_SOUND = Key.key("the_sift", "the_sift_portal_open");
    static final Key CLOSE_SOUND = Key.key("the_sift", "the_sift_portal_close");
    static final Key AMBIENT_SOUND = Key.key("the_sift", "block.sift_portal.ambient");
    static final Key TRIGGER_SOUND = Key.key("the_sift", "block.sift_portal.trigger");

    private final Plugin plugin;
    private final int lightLevel;

    public PortalSurface(Plugin plugin, int lightLevel) {
        this.plugin = plugin;
        this.lightLevel = Math.clamp(lightLevel, 0, 15);
    }

    /** Free for the surface: air, sculk veins and glow lichen (like the original), or the surface itself. */
    public static boolean isOpenInterior(Block block) {
        Material type = block.getType();
        return type.isAir() || type == Material.SCULK_VEIN || type == Material.GLOW_LICHEN || type == Material.LIGHT;
    }

    /** Fills the opening block by block; {@code animated = false} fills at once (main portal on start). */
    public void open(World world, SiftPortal portal, boolean animated) {
        List<int[]> queue = portal.frame().interiorEdgesFirst();
        play(world, portal.frame(), OPEN_SOUND, 4.0f);
        if (!animated) {
            queue.forEach(p -> place(world, p, false));
            showMist(world, portal);
            return;
        }
        Random random = new Random(portal.id().hashCode());
        new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                if (index >= queue.size()) {
                    showMist(world, portal);
                    cancel();
                    return;
                }
                place(world, queue.get(index++), true);
                // the original sets a second block in 55 % of the ticks and pauses in 20 %
                if (index < queue.size() && random.nextInt(100) < 55) {
                    place(world, queue.get(index++), true);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Empties the opening from the centre outwards. */
    public void close(World world, SiftPortal portal) {
        hideMist(world, portal);
        List<int[]> queue = new ArrayList<>(portal.frame().interiorEdgesFirst()).reversed();
        play(world, portal.frame(), CLOSE_SOUND, 4.0f);
        new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                for (int i = 0; i < 2 && index < queue.size(); i++) {
                    int[] p = queue.get(index++);
                    Block block = world.getBlockAt(p[0], p[1], p[2]);
                    if (block.getType() == Material.LIGHT) {
                        block.setType(Material.AIR, false);
                        particles(world, p, 5);
                    }
                }
                if (index >= queue.size()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Puts back light blocks and the mist (after a restart or when someone broke into the surface). */
    public void repair(World world, SiftPortal portal) {
        for (int[] p : portal.frame().interiorEdgesFirst()) {
            Block block = world.getBlockAt(p[0], p[1], p[2]);
            if (block.getType() != Material.LIGHT && isOpenInterior(block)) {
                place(world, p, false);
            }
        }
        if (mist(world, portal).isEmpty()) {
            showMist(world, portal);
        }
    }

    private void place(World world, int[] p, boolean effects) {
        Block block = world.getBlockAt(p[0], p[1], p[2]);
        if (!isOpenInterior(block)) {
            return;
        }
        Light light = (Light) Material.LIGHT.createBlockData();
        light.setLevel(lightLevel);
        block.setBlockData(light, false);
        if (effects) {
            particles(world, p, 5);
            world.playSound(Sound.sound(AMBIENT_SOUND, Sound.Source.BLOCK, 0.3f, 1.15f), p[0] + 0.5, p[1] + 0.5, p[2] + 0.5);
        }
    }

    private static void particles(World world, int[] p, int count) {
        world.spawnParticle(Particle.END_ROD, p[0] + 0.5, p[1] + 0.5, p[2] + 0.5, count, 0.32, 0.32, 0.32, 0.025);
    }

    static void play(World world, PortalFrame frame, Key sound, float volume) {
        double[] c = center(frame);
        world.playSound(Sound.sound(sound, Sound.Source.BLOCK, volume, 1.0f), c[0], c[1], c[2]);
    }

    /** Centre of the opening in world coordinates. */
    static double[] center(PortalFrame frame) {
        boolean alongX = frame.axis() == PortalFrame.Axis.X;
        return new double[] {
            frame.minX() + (alongX ? frame.width() / 2.0 : 0.5),
            frame.minY() + frame.height() / 2.0,
            frame.minZ() + (alongX ? 0.5 : frame.width() / 2.0)};
    }

    private void showMist(World world, SiftPortal portal) {
        PortalFrame frame = portal.frame();
        double[] c = center(frame);
        ItemStack mist = ItemStack.of(Material.PAPER);
        mist.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, new NamespacedKey("the_sift", "sift_portal_surface"));
        world.spawn(new Location(world, c[0], c[1], c[2]), ItemDisplay.class, display -> {
            display.setItemStack(mist);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(2.0f);
            float angle = frame.axis() == PortalFrame.Axis.X ? 0f : (float) (Math.PI / 2);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(angle, 0, 1, 0),
                    new Vector3f(frame.width() - 2, frame.height() - 2, 1), new AxisAngle4f()));
            display.getPersistentDataContainer().set(DISPLAY_KEY, PersistentDataType.STRING, portal.id());
        });
    }

    private void hideMist(World world, SiftPortal portal) {
        mist(world, portal).forEach(Entity::remove);
    }

    private static List<Entity> mist(World world, SiftPortal portal) {
        double[] c = center(portal.frame());
        List<Entity> found = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(new Location(world, c[0], c[1], c[2]), 1.5, 1.5, 1.5)) {
            if (entity instanceof ItemDisplay
                    && portal.id().equals(entity.getPersistentDataContainer().get(DISPLAY_KEY, PersistentDataType.STRING))) {
                found.add(entity);
            }
        }
        return found;
    }

    /** Ambient mist particles and hum for an open portal (called now and then while players are near). */
    static void ambient(World world, SiftPortal portal, Random random) {
        List<int[]> interior = portal.frame().interiorEdgesFirst();
        for (int i = 0; i < 3; i++) {
            int[] p = interior.get(random.nextInt(interior.size()));
            world.spawnParticle(Particle.REVERSE_PORTAL, p[0] + random.nextDouble(), p[1] + random.nextDouble(),
                    p[2] + random.nextDouble(), 1, 0, 0, 0, 0.02);
        }
        if (random.nextInt(20) == 0) {
            int[] p = interior.get(random.nextInt(interior.size()));
            world.playSound(Sound.sound(AMBIENT_SOUND, Sound.Source.BLOCK, 0.4f, 1.0f), p[0] + 0.5, p[1] + 0.5, p[2] + 0.5);
        }
    }
}
