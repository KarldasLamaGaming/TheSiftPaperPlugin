package de.lamamc.sift.world.atmosphere;

import de.lamamc.sift.core.atmosphere.IntroRule;
import de.lamamc.sift.world.SiftWorlds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The intro sound of the original (alpha.29, config atmosphere.intro-sound.*): {@code the_sift:sift_intro},
 * ambient, volume 0.4, played once per player on the first stay in the Sift (SiftTeleportManager
 * #playFirstIntro). Remembered in the player's persistent data, so it survives restarts.
 */
public final class SiftIntro implements Listener {

    public static final NamespacedKey PLAYED = new NamespacedKey("the_sift", "intro_played");
    public static final String SOUND = "the_sift:sift_intro";
    private static final int STEP = 5;

    private final boolean enabled;
    private final float volume;
    private final int delayTicks;
    private final Map<UUID, Integer> counted = new HashMap<>();
    private long played;

    public SiftIntro(ConfigurationSection config) {
        this.enabled = config == null || config.getBoolean("enabled", true);
        this.volume = config == null ? 0.4f : (float) config.getDouble("volume", 0.4);
        this.delayTicks = config == null ? 20 : Math.max(0, config.getInt("delay-ticks", 20));
    }

    public boolean enabled() {
        return enabled;
    }

    public long playedSinceStart() {
        return played;
    }

    public void start(Plugin plugin) {
        if (!enabled) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, STEP);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (hasPlayed(player)) {
                continue;
            }
            boolean inSift = SiftWorlds.isSift(player.getWorld());
            int count = IntroRule.count(counted.getOrDefault(player.getUniqueId(), 0), inSift, inSift && groundBelow(player), STEP);
            if (IntroRule.play(false, count, delayTicks)) {
                play(player);
                counted.remove(player.getUniqueId());
            } else if (count == 0) {
                counted.remove(player.getUniqueId());
            } else {
                counted.put(player.getUniqueId(), count);
            }
        }
    }

    /** Plays the intro for this player now and remembers it. */
    public void play(Player player) {
        player.playSound(player, SOUND, SoundCategory.AMBIENT, volume, 1.0f);
        player.getPersistentDataContainer().set(PLAYED, PersistentDataType.BYTE, (byte) 1);
        played++;
    }

    public static boolean hasPlayed(Player player) {
        return player.getPersistentDataContainer().has(PLAYED, PersistentDataType.BYTE);
    }

    /** Forgets that the player heard the intro (/sift debug intro reset). */
    public static void reset(Player player) {
        player.getPersistentDataContainer().remove(PLAYED);
    }

    private static boolean groundBelow(Player player) {
        Block feet = player.getLocation().getBlock();
        for (int dy = 0; dy <= 8; dy++) {
            Block b = feet.getRelative(0, -dy, 0);
            if (b.getY() < player.getWorld().getMinHeight()) {
                return false;
            }
            if (b.getType().isSolid()) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        counted.remove(event.getPlayer().getUniqueId());
    }
}
