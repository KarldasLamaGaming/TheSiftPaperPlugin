package de.lamamc.sift.world.boss;

import de.lamamc.sift.core.boss.BossDrops;
import de.lamamc.sift.item.SiftItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Boss items from the vanilla bosses (phase 4, alpha.39, phase4.boss-items, rules in {@link BossDrops}): Herz des
 * Wächters from a warden and Kern des Withers from a wither killed by a player (added to the drops); a Drachenschuppe
 * for every player who damaged the ender dragon (into the inventory, overflow at the feet).
 */
public final class BossDropListener implements Listener {

    private final SiftItems items;
    private final BossDrops.Drop warden;
    private final BossDrops.Drop wither;
    private final BossDrops.DragonDrop dragon;
    /** Damage per player against each living dragon. */
    private final Map<UUID, Map<UUID, Double>> dragonDamage = new HashMap<>();

    public BossDropListener(SiftItems items, ConfigurationSection config) {
        this.items = items;
        ConfigurationSection c = config == null ? new org.bukkit.configuration.MemoryConfiguration() : config;
        warden = drop(c, "warden-heart", "warden_heart");
        wither = drop(c, "wither-core", "wither_core");
        dragon = new BossDrops.DragonDrop(c.getBoolean("dragon-scale.enabled", true), c.getString("dragon-scale.item", "dragon_scale"),
                Math.max(0, c.getInt("dragon-scale.amount-per-player", 1)), Math.max(0, c.getDouble("dragon-scale.min-damage-share", 0.0)));
    }

    private static BossDrops.Drop drop(ConfigurationSection c, String key, String item) {
        return new BossDrops.Drop(c.getBoolean(key + ".enabled", true), c.getString(key + ".item", item),
                Math.clamp(c.getDouble(key + ".chance", 1.0), 0.0, 1.0), Math.max(0, c.getInt(key + ".amount", 1)),
                (long) (Math.max(0, c.getDouble(key + ".cooldown-hours", 0)) * 3_600_000L));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity target = event.getEntity() instanceof ComplexEntityPart part ? part.getParent() : event.getEntity();
        if (!(target instanceof EnderDragon)) {
            return;
        }
        Player player = attacker(event.getDamager());
        if (player != null) {
            dragonDamage.computeIfAbsent(target.getUniqueId(), d -> new HashMap<>()).merge(player.getUniqueId(), event.getFinalDamage(), Double::sum);
        }
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player ? player : null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof EnderDragon) {
            Map<UUID, Double> damage = dragonDamage.remove(dead.getUniqueId());
            for (UUID id : BossDrops.dragonRecipients(dragon, damage == null ? Map.of() : damage)) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    give(player, dragon.item(), dragon.amount());
                }
            }
            return;
        }
        BossDrops.Drop drop = dead instanceof Warden ? warden : dead instanceof Wither ? wither : null;
        if (drop == null) {
            return;
        }
        Player killer = dead.getKiller();
        NamespacedKey last = new NamespacedKey("the_sift", "boss_drop_" + drop.item());
        long now = System.currentTimeMillis();
        int amount = BossDrops.amount(drop, killer != null, now,
                killer == null ? null : killer.getPersistentDataContainer().get(last, PersistentDataType.LONG),
                ThreadLocalRandom.current().nextDouble());
        ItemStack stack = amount > 0 ? items.create(drop.item(), amount) : null;
        if (stack != null) {
            event.getDrops().add(stack);
            killer.getPersistentDataContainer().set(last, PersistentDataType.LONG, now);
        }
    }

    private void give(Player player, String item, int amount) {
        ItemStack stack = items.create(item, amount);
        if (stack == null) {
            return;
        }
        player.getInventory().addItem(stack).values().forEach(rest ->
                player.getWorld().dropItem(player.getLocation(), rest, i -> i.setOwner(player.getUniqueId())));
        player.sendActionBar(Component.text("Du erhältst: ", NamedTextColor.LIGHT_PURPLE).append(stack.effectiveName()));
    }

    /** Dragons whose damage is still tracked (for /sift debug). */
    public int trackedDragons() {
        return dragonDamage.size();
    }
}
