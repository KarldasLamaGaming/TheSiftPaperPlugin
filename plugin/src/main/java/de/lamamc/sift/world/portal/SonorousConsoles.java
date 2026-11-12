package de.lamamc.sift.world.portal;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.core.portal.KeyActivation;
import de.lamamc.sift.core.portal.PortalState;
import java.util.ArrayList;
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
 * note block on sonorous deepslate in note mode has 8 sounds instead of the vanilla notes; right-click picks the
 * next sound, hitting it plays it. Since alpha.37 the matching portal key opens a portal in one go: right-click
 * on one of its consoles wakes them one after another (colour bursts), the trigger sounds, the consoles play their
 * sounds back with light beams and the portal opens. There is no tune to play any more; consoles in horn mode
 * only glitch.
 */
public final class SonorousConsoles implements Listener {

    public static final String BLOCK_ID = "sonorous_deepslate";
    /** Colours of the 8 sounds (SonorousColors of the original). */
    private static final int[] COLORS = {11546150, 3949738, 16351261, 8991416, 1481884, 8439583, 16701501, 15961002};
    private static final Key GLITCH = Key.key("the_sift", "sonorous_autoplay_glitch");
    /** Ticks between two consoles waking up after the key (like the waves of the Singer). */
    private static final int WAKE_STEP = 6;

    private final Plugin plugin;
    private final CustomBlockRegistry blocks;
    private final PortalService portals;
    /** World key -> Sift id of the key that opens the shrine portals there. */
    private final Map<String, String> keys;
    /** Sift id of the key of the Ancient City portals. */
    private final String ancientCityKey;
    private boolean keysEnabled = true;
    private boolean consume = true;
    private final Set<String> igniting = new HashSet<>();

    public SonorousConsoles(Plugin plugin, CustomBlockRegistry blocks, PortalService portals, Map<String, String> keys,
                            String ancientCityKey) {
        this.keys = Map.copyOf(keys);
        this.ancientCityKey = ancientCityKey;
        this.plugin = plugin;
        this.blocks = blocks;
        this.portals = portals;
    }

    /** The key this portal takes (null: none configured). */
    public String keyFor(World world, SiftPortal portal) {
        return portal.kind() == SiftPortal.Kind.ANCIENT_CITY ? ancientCityKey : keys.get(world.getKey().toString());
    }

    /** portals.keys.enabled and portals.keys.consume. */
    public void setKeys(boolean enabled, boolean consume) {
        this.keysEnabled = enabled;
        this.consume = consume;
    }

    private boolean isKey(String id) {
        return id != null && (keys.containsValue(id) || id.equals(ancientCityKey));
    }

    public enum Mode { HORN, NOTE, NONE }

    /** Mode of the sonorous deepslate under a note block. */
    public Mode modeBelow(Block noteBlock) {
        return mode(noteBlock.getRelative(BlockFace.DOWN));
    }

    public Mode mode(Block block) {
        CustomBlock custom = blocks.at(block);
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

    /** Sound 1..8 of a note block on a console. */
    public static int sound(Block noteBlock) {
        return de.lamamc.sift.core.portal.ConsoleSounds.soundOf(((NoteBlock) noteBlock.getBlockData()).getNote().getId());
    }

    private static void showSound(Player player, int sound) {
        player.sendActionBar(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                "<gray>Klang <white><bold>" + sound + "</bold></white> von " + de.lamamc.sift.core.portal.ConsoleSounds.COUNT));
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

    /** One console to note mode with its burst. */
    public void wakeConsole(Block console) {
        if (mode(console) == Mode.NONE) {
            return;
        }
        setMode(console, Mode.NOTE);
        Block note = console.getRelative(BlockFace.UP);
        burst(note, note.getType() == Material.NOTE_BLOCK ? sound(note) : 1);
    }

    /** A sound wave of the Singer reaches a console: it lights up but stays asleep (only a key wakes it). */
    public void echo(Block console) {
        if (mode(console) == Mode.NONE) {
            return;
        }
        Block note = console.getRelative(BlockFace.UP);
        burst(note, note.getType() == Material.NOTE_BLOCK ? sound(note) : 1);
    }

    /** /sift portal lock: all consoles back to horn mode, the portal asleep (a key is needed again). */
    public void lock(World world, SiftPortal portal) {
        for (int[] c : portal.consoles()) {
            Block console = world.getBlockAt(c[0], c[1], c[2]);
            if (mode(console) == Mode.NOTE) {
                setMode(console, Mode.HORN);
            }
        }
        igniting.remove(portal.id());
        portals.registry().put(world, portal.withState(portal.state().lock()));
    }

    /** Whether the keys may open portals (portals.keys.enabled). */
    public boolean keysEnabled() {
        return keysEnabled;
    }

    /** Right-click with a portal key on a console (or its note block): the matching key opens the portal. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onKey(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        org.bukkit.inventory.ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        String key = de.lamamc.sift.item.SiftItems.idOf(held);
        if (!isKey(key)) {
            return;
        }
        Block console = block.getType() == Material.NOTE_BLOCK ? block.getRelative(BlockFace.DOWN) : block;
        if (mode(console) == Mode.NONE) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        World world = block.getWorld();
        Optional<SiftPortal> portal = portals.registry().withConsole(world, console.getX(), console.getY(), console.getZ());
        if (portal.isPresent() && igniting.contains(portal.get().id())) {
            return; // already opening
        }
        KeyActivation.Result result = KeyActivation.decide(keysEnabled, portal.isPresent(),
                portal.map(SiftPortal::state).orElse(null), key, portal.map(p -> keyFor(world, p)).orElse(null));
        switch (result) {
            case DISABLED -> player.sendActionBar(Component.text("Schlüssel sind abgeschaltet; nur ein Admin öffnet Portale."));
            case NO_PORTAL -> player.sendActionBar(Component.text("Diese Konsole gehört zu keinem Portal."));
            case ALREADY_OPEN -> player.sendActionBar(Component.text("Dieses Portal ist schon offen."));
            case WRONG_KEY -> {
                glitch(console);
                player.sendActionBar(Component.text("Dieser Schlüssel passt nicht zu diesem Portal."));
            }
            case OPEN -> {
                PortalUnlocks unlocks = portals.unlocks();
                if (unlocks != null && !unlocks.allows(player, portal.get(), world)) {
                    glitch(console);
                    unlocks.refuse(player, portal.get(), world); // the key stays in the hand
                    return;
                }
                if (KeyActivation.consumes(consume, player.getGameMode() == org.bukkit.GameMode.CREATIVE, portal.get().state())) {
                    held.subtract();
                }
                activate(world, portal.get(), console);
                player.sendActionBar(Component.text("Die Konsolen erwachen."));
            }
        }
    }

    /**
     * Opens a portal with the full ceremony (key or /sift portal activate): the consoles wake one after another from
     * the one touched, the trigger sounds, then the ignition plays and the surface opens.
     */
    public void activate(World world, SiftPortal portal, Block from) {
        if (portal.consoles().isEmpty()) {
            portals.open(world, portal);
            return;
        }
        String id = portal.id();
        igniting.add(id);
        List<Block> group = new ArrayList<>(portal.consoles().stream().map(c -> world.getBlockAt(c[0], c[1], c[2])).toList());
        List<Block> order = new ArrayList<>(group);
        if (from != null) {
            order.sort(java.util.Comparator.comparingDouble(b -> b.getLocation().distanceSquared(from.getLocation())));
        }
        new BukkitRunnable() {
            private int step;

            @Override
            public void run() {
                if (!igniting.contains(id)) {
                    cancel(); // locked meanwhile
                    return;
                }
                if (step < order.size()) {
                    wakeConsole(order.get(step++));
                    return;
                }
                cancel();
                portals.registry().get(world, id).ifPresentOrElse(p -> {
                    portals.registry().put(world, p.withState(p.state().unlock()));
                    PortalSurface.play(world, p.frame(), PortalSurface.TRIGGER_SOUND, 1.0f);
                    ignite(world, p, group);
                }, () -> igniting.remove(id));
            }
        }.runTaskTimer(plugin, 1L, WAKE_STEP);
    }

    /** Right-click picks the next of the 8 sounds; hitting plays it. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.NOTE_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        String held = de.lamamc.sift.item.SiftItems.idOf(event.getPlayer().getInventory().getItemInMainHand());
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isKey(held)) {
            return; // handled by onKey
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
                player.sendActionBar(Component.text("Die Konsole schläft. Der passende Schlüssel öffnet das Portal."));
                return;
            }
            NoteBlock data = (NoteBlock) block.getBlockData();
            int next = de.lamamc.sift.core.portal.ConsoleSounds.next(sound(block));
            data.setNote(new org.bukkit.Note(de.lamamc.sift.core.portal.ConsoleSounds.noteOf(next)));
            block.setBlockData(data, false);
            playSound(block, next);
            showSound(player, next);
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK && mode == Mode.NOTE) {
            showSound(player, sound(block));
        }
    }

    /** Burst, play-back of the consoles' sounds with light beams, then the portal opens (SonorousIgnition, autoplay). */
    private void ignite(World world, SiftPortal portal, List<Block> group) {
        String key = portal.id();
        igniting.add(key);
        // every console plays its sound once, in the stored order (since alpha.37 there is no tune)
        List<Block> notes = group.stream().map(b -> b.getRelative(BlockFace.UP)).filter(b -> b.getType() == Material.NOTE_BLOCK).toList();
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
