package de.lamamc.sift.world.portal;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.core.portal.PortalMelody;
import de.lamamc.sift.core.portal.PortalState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Consoles of sonorous deepslate (SonorousConsoles, NoteBlockMixin, SonorousIgnition of the original): a
 * note block on sonorous deepslate in note mode has 8 sounds instead of the vanilla notes. Right-click
 * picks the next sound, hitting it plays it. Playing the tune on the consoles of a registered portal wakes
 * them: colour burst, the consoles play the tune back, and the portal opens. In horn mode they only glitch.
 */
public final class SonorousConsoles implements Listener {

    public static final String BLOCK_ID = "sonorous_deepslate";
    /** Colours of the 8 sounds (SonorousColors of the original). */
    private static final int[] COLORS = {11546150, 3949738, 16351261, 8991416, 1481884, 8439583, 16701501, 15961002};
    private static final Key GLITCH = Key.key("the_sift", "sonorous_autoplay_glitch");

    private final Plugin plugin;
    private final CustomBlockRegistry blocks;
    private final PortalService portals;
    private final int[] sequence;
    private final Map<String, PortalMelody> melodies = new HashMap<>();
    private final Set<String> igniting = new HashSet<>();

    public SonorousConsoles(Plugin plugin, CustomBlockRegistry blocks, PortalService portals, int[] sequence) {
        this.plugin = plugin;
        this.blocks = blocks;
        this.portals = portals;
        this.sequence = sequence;
    }

    public enum Mode { HORN, NOTE, NONE }

    /** Mode of the sonorous deepslate under a note block. */
    public Mode modeBelow(Block noteBlock) {
        return mode(noteBlock.getRelative(BlockFace.DOWN));
    }

    public Mode mode(Block block) {
        CustomBlock custom = blocks.of(block.getBlockData());
        if (custom == null || !custom.id().equals(BLOCK_ID)) {
            return Mode.NONE;
        }
        return "note".equals(custom.properties().get("mode")) ? Mode.NOTE : Mode.HORN;
    }

    /** Switches a sonorous deepslate block to note or horn mode. */
    public void setMode(Block block, Mode mode) {
        CustomBlock state = blocks.state(BLOCK_ID, Map.of("mode", mode == Mode.NOTE ? "note" : "horn"));
        if (state != null) {
            block.setBlockData(state.data(), false);
        }
    }

    private static int sound(Block noteBlock) {
        return ((NoteBlock) noteBlock.getBlockData()).getNote().getId() % 8 + 1;
    }

    /** Every way a note block sounds (hit, redstone): the sonorous sound or the glitch instead of the note. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNote(NotePlayEvent event) {
        Block block = event.getBlock();
        Mode mode = modeBelow(block);
        if (mode == Mode.NONE) {
            return;
        }
        event.setCancelled(true);
        if (mode == Mode.HORN) {
            glitch(block);
        } else {
            playSound(block, sound(block));
        }
    }

    /** Right-click picks the next of the 8 sounds; hitting plays it and counts for the tune. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.NOTE_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Mode mode = modeBelow(block);
        if (mode == Mode.NONE) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (player.isSneaking() && !player.getInventory().getItemInMainHand().isEmpty()) {
                return; // placing a block on the console
            }
            event.setCancelled(true);
            if (mode == Mode.HORN) {
                glitch(block);
                player.sendActionBar(Component.text("Die Konsole schläft noch. Ein Schlüssel weckt sie."));
                return;
            }
            NoteBlock data = (NoteBlock) block.getBlockData();
            int next = (data.getNote().getId() % 8 + 1) % 8;
            data.setNote(new org.bukkit.Note(next));
            block.setBlockData(data, false);
            playSound(block, next + 1);
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK && mode == Mode.NOTE) {
            heard(player, block, sound(block));
        }
    }

    private void heard(Player player, Block noteBlock, int note) {
        World world = noteBlock.getWorld();
        Block console = noteBlock.getRelative(BlockFace.DOWN);
        Optional<SiftPortal> portal = portals.registry().withConsole(world, console.getX(), console.getY(), console.getZ());
        if (portal.isEmpty() || igniting.contains(portal.get().id())) {
            return; // a console that belongs to no portal only makes music
        }
        String key = world.getKey() + " " + portal.get().id();
        PortalMelody melody = melodies.computeIfAbsent(key, k -> new PortalMelody(sequence));
        PortalMelody.Match match = melody.play(note);
        if (match == PortalMelody.Match.NONE) {
            return;
        }
        if (match == PortalMelody.Match.CLOSING || portal.get().state() == PortalState.OPEN) {
            player.sendActionBar(Component.text("Dieses Portal bleibt offen."));
            return;
        }
        if (portal.get().state() == PortalState.LOCKED) {
            glitch(noteBlock);
            player.sendActionBar(Component.text("Die Konsolen dieses Portals schlafen noch."));
            return;
        }
        List<Block> group = portal.get().consoles().stream().map(c -> world.getBlockAt(c[0], c[1], c[2])).toList();
        ignite(world, portal.get(), group);
    }

    /** Burst, play-back of the tune on the console, then the portal opens (SonorousIgnition, autoplay). */
    private void ignite(World world, SiftPortal portal, List<Block> group) {
        String key = portal.id();
        igniting.add(key);
        List<Block> notes = new ArrayList<>();
        // play the tune back on the note blocks that carry each sound
        for (int note : sequence) {
            group.stream().map(b -> b.getRelative(BlockFace.UP)).filter(b -> b.getType() == Material.NOTE_BLOCK && sound(b) == note)
                    .findFirst().ifPresent(notes::add);
        }
        new BukkitRunnable() {
            private int tick;
            private int played;

            @Override
            public void run() {
                tick++;
                if (notes.stream().anyMatch(b -> b.getType() != Material.NOTE_BLOCK || modeBelow(b) != Mode.NOTE)) {
                    notes.stream().findFirst().ifPresent(SonorousConsoles.this::glitch);
                    finish();
                    return;
                }
                if (tick == 10) {
                    notes.forEach(b -> burst(b, sound(b)));
                } else if (tick >= 30 && (tick - 30) % 15 == 0) {
                    if (played < notes.size()) {
                        Block b = notes.get(played++);
                        playSound(b, sound(b));
                        burst(b, sound(b));
                        beam(b, sound(b));
                    } else {
                        portals.registry().get(world, portal.id()).filter(p -> p.state() == PortalState.UNLOCKED)
                                .ifPresent(p -> portals.open(world, p));
                        finish();
                    }
                }
            }

            private void finish() {
                igniting.remove(key);
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static void playSound(Block block, int note) {
        block.getWorld().playSound(Sound.sound(Key.key("the_sift", "block.sonorous_deepslate.sound_" + note), Sound.Source.RECORD, 3.0f, 1.0f),
                block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        block.getWorld().spawnParticle(Particle.DUST, block.getX() + 0.5, block.getY() + 1.2, block.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0,
                new Particle.DustOptions(Color.fromRGB(COLORS[note - 1]), 1.2f));
    }

    private void glitch(Block block) {
        block.getWorld().playSound(Sound.sound(GLITCH, Sound.Source.RECORD, 3.0f, 1.0f), block.getX() + 0.5, block.getY() + 0.5,
                block.getZ() + 0.5);
    }

    private static void burst(Block block, int note) {
        int base = COLORS[note - 1];
        for (int shade = 0; shade < 3; shade++) {
            block.getWorld().spawnParticle(Particle.DUST, block.getX() + 0.5, block.getY() + 1.3, block.getZ() + 0.5, 3, 0.45, 0.2, 0.45, 0,
                    new Particle.DustOptions(Color.fromRGB(lighten(base, shade * 0.19f)), 1.5f));
        }
    }

    private static void beam(Block block, int note) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(COLORS[note - 1]), 1.0f);
        for (double y = 1.0; y < 8.0; y += 0.35) {
            block.getWorld().spawnParticle(Particle.DUST, block.getX() + 0.5, block.getY() + y, block.getZ() + 0.5, 1, 0.03, 0, 0.03, 0, dust);
        }
    }

    private static int lighten(int rgb, float factor) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        r = Math.round(r + (255 - r) * factor);
        g = Math.round(g + (255 - g) * factor);
        b = Math.round(b + (255 - b) * factor);
        return r << 16 | g << 8 | b;
    }
}
