package de.lamamc.sift.world.temple;

import de.lamamc.sift.core.boss.GolemBoss;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import de.lamamc.sift.mob.boss.BossArena;
import de.lamamc.sift.mob.boss.GreatEchoGolem;
import de.lamamc.sift.mob.boss.GreatEchoGolemKind;
import de.lamamc.sift.mob.boss.SoulHelperKind;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The Great Echo Golem fights in the temple arenas (alpha.42b, phase4.bosses.great-echo-golem).
 *
 * <p>A player right-clicks the Seelen-Altar of a sleeping temple (its brood spawners exhausted) holding the offering (8
 * soul blocks): they are used up, a countdown runs, then the golem falls down the shaft and lands in front of the altar.
 * The players in the arena at that moment set its health. Players who enter later fight too; the soul bars at the gates
 * keep fighters in until the fight ends (they may die, or an admin teleports them). Only fighters standing in the arena
 * hurt the golem. The fight ends with its death (temple DEFEATED, loot for everyone eligible), or is called off when no
 * fighter has been in the arena for a while, the golem is unloaded, or the server stops (temple sleeps again).
 */
public final class TempleBosses implements Listener {

    /** Who an admin hit is credited to (/sift temple boss damage). */
    public static final UUID ADMIN = new UUID(0, 0);

    public record Settings(boolean enabled, String offeringItem, int offeringAmount, int countdownTicks, int leaveTicks, double arenaHeight,
                           boolean sealGates, boolean refundOffering, boolean platesDrop, int helpers, int lootNightCrystals) {

        public static Settings of(ConfigurationSection c) {
            ConfigurationSection start = sub(c, "start");
            ConfigurationSection reset = sub(c, "reset");
            ConfigurationSection arena = sub(c, "arena");
            return new Settings(c.getBoolean("enabled", true), start.getString("offering", "the_sift:soul_block").replace("the_sift:", ""),
                    Math.max(0, start.getInt("amount", 8)), Math.max(0, start.getInt("countdown-seconds", 5)) * 20,
                    Math.max(1, reset.getInt("leave-seconds", 30)) * 20, arena.getDouble("height", 10),
                    arena.getBoolean("seal-gates", true), reset.getBoolean("refund-offering", false),
                    sub(c, "phases").getBoolean("plates-drop", true), Math.max(0, sub(c, "phases").getInt("helpers", 2)),
                    Math.max(0, sub(c, "loot").getInt("night-crystal", 1)));
        }

        private static ConfigurationSection sub(ConfigurationSection c, String path) {
            ConfigurationSection s = c == null ? null : c.getConfigurationSection(path);
            return s != null ? s : new org.bukkit.configuration.MemoryConfiguration();
        }
    }

    private enum Stage { COUNTDOWN, DESCENT, FIGHT, ENDED }

    private final Plugin plugin;
    private final TempleRegistry registry;
    private final MobManager mobs;
    private final GreatEchoGolemKind kind;
    private final SoulHelperKind helperKind;
    private final SiftItems items;
    private final Settings settings;
    private final Map<String, Battle> battles = new LinkedHashMap<>();
    private final Map<UUID, Battle> byBoss = new HashMap<>();
    private final Map<UUID, Long> lastNotice = new HashMap<>();
    private boolean settingBack;

    public TempleBosses(Plugin plugin, TempleRegistry registry, MobManager mobs, GreatEchoGolemKind kind, SoulHelperKind helperKind,
                        SiftItems items, Settings settings) {
        this.plugin = plugin;
        this.registry = registry;
        this.mobs = mobs;
        this.kind = kind;
        this.helperKind = helperKind;
        this.items = items;
        this.settings = settings;
        kind.arenas(byBoss::get);
        helperKind.arenas(byBoss::get);
    }

    public Settings settings() {
        return settings;
    }

    public void start() {
        registry.altarUse(this::useAltar);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Server stop: every fight is called off (the golem is not saved), the temples sleep again. */
    public void stop() {
        for (Battle b : List.copyOf(battles.values())) {
            b.end(false, null);
        }
    }

    public Collection<Battle> battles() {
        return battles.values();
    }

    public Battle battle(String templeId) {
        return battles.get(templeId);
    }

    /** Calls a fight off (admin); false when none runs there. */
    public boolean stop(String templeId, String message) {
        Battle b = battles.get(templeId);
        if (b == null) {
            return false;
        }
        b.end(false, message);
        return true;
    }

    // ---- start ---------------------------------------------------------------------------------------

    private void useAltar(Player player, TempleRegistry.Temple temple) {
        if (!settings.enabled() || !kind.enabled()) {
            player.sendActionBar(Component.text("Der Seelen-Altar schweigt noch.", NamedTextColor.DARK_AQUA));
            return;
        }
        if (battles.containsKey(temple.id())) {
            player.sendActionBar(Component.text("Der Wächter ist schon erwacht.", NamedTextColor.DARK_AQUA));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (settings.offeringAmount() > 0 && !creative
                && (!settings.offeringItem().equals(SiftItems.idOf(hand)) || hand.getAmount() < settings.offeringAmount())) {
            player.sendActionBar(Component.text("Der Seelen-Altar verlangt " + settings.offeringAmount() + " Seelenblöcke in deiner Hand.",
                    NamedTextColor.DARK_AQUA));
            return;
        }
        if (settings.offeringAmount() > 0 && !creative) {
            hand.setAmount(hand.getAmount() - settings.offeringAmount());
            player.getInventory().setItemInMainHand(hand.getAmount() > 0 ? hand : null);
        }
        begin(temple, player.getUniqueId(), 0, false);
    }

    /**
     * Starts a fight (also /sift temple boss start). {@code simulatedPlayers} > 0: health as for that many players and no
     * call-off while the arena is empty (smoke tests without players).
     */
    public Battle begin(TempleRegistry.Temple temple, UUID offerer, int simulatedPlayers, boolean practice) {
        Battle b = new Battle(temple, offerer, simulatedPlayers, practice);
        battles.put(temple.id(), b);
        registry.fighting(temple.id(), true);
        b.say(Component.text("Der Seelen-Altar erwacht …", NamedTextColor.DARK_AQUA));
        b.sound(Key.key("minecraft", "block.bell.use"), 2f, 0.5f);
        plugin.getLogger().info("Temple " + temple.id() + ": boss fight begins");
        return b;
    }

    // ---- ticking -------------------------------------------------------------------------------------

    private void tick() {
        for (Battle b : List.copyOf(battles.values())) {
            try {
                b.tick();
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Boss fight " + b.temple.id() + " failed; called off", e);
                b.end(false, "Der Kampf wurde abgebrochen (Fehler).");
            }
        }
    }

    // ---- events --------------------------------------------------------------------------------------

    private Battle battleOf(Entity entity) {
        return entity == null ? null : byBoss.get(entity.getUniqueId());
    }

    /** Only fighters in the arena hurt the golem; everything else (fall, fire, suffocation, mobs) does nothing. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!GreatEchoGolemKind.ID.equals(MobManager.kindOf(entity))) {
            return;
        }
        Battle b = battleOf(entity);
        if (b == null) {
            if (event.getCause() != EntityDamageEvent.DamageCause.KILL) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.KILL || event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            return; // /kill: the fight is called off when the golem dies without its health being gone
        }
        Player attacker = null;
        GolemBoss.Hit hit = GolemBoss.Hit.MELEE;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Player p) {
                attacker = p;
            } else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
                attacker = p;
                hit = GolemBoss.Hit.RANGED;
            }
        }
        if (attacker == null || b.stage != Stage.FIGHT) {
            event.setCancelled(true);
            return;
        }
        if (!b.inArena(attacker)) {
            event.setCancelled(true);
            notice(attacker, "Der Golem spürt nur Treffer aus der Arena.");
            return;
        }
        GolemBoss.Result r = b.hit(attacker, hit, event.getFinalDamage());
        if (r.bounced()) {
            event.setCancelled(true);
            notice(attacker, "Die Seelenplatten lassen Geschosse abprallen.");
            return;
        }
        if (r.to() == GolemBoss.Phase.DEAD) {
            event.setDamage(100_000); // the base dies by this hit: killer and experience like any mob
        } else {
            event.setDamage(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossDeath(EntityDeathEvent event) {
        Battle b = battleOf(event.getEntity());
        if (b != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (b.stage != Stage.ENDED) {
                    if (b.fight != null && b.fight.phase() == GolemBoss.Phase.DEAD) {
                        b.victory();
                    } else {
                        b.end(false, "Der Golem ist verschwunden; der Kampf ist abgebrochen.");
                    }
                }
            });
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        for (Battle b : battles.values()) {
            b.leave(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        for (Battle b : battles.values()) {
            b.leave(event.getPlayer().getUniqueId());
            b.hide(event.getPlayer());
        }
    }

    /** The soul bars stop ender pearls and chorus fruit out of the arena; an admin teleport takes the player out of the fight. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (settingBack) {
            return;
        }
        Player player = event.getPlayer();
        for (Battle b : battles.values()) {
            if (!b.fighters.contains(player.getUniqueId())) {
                continue;
            }
            Location to = event.getTo();
            boolean outside = to.getWorld() != b.world || !b.bounds.inside(to.getX(), to.getY(), to.getZ());
            if (!outside) {
                continue;
            }
            PlayerTeleportEvent.TeleportCause cause = event.getCause();
            if (b.sealed() && (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                    || cause == PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT)) {
                event.setCancelled(true);
                notice(player, "Das Seelengitter hält dich in der Arena.");
            } else {
                b.leave(player.getUniqueId());
            }
        }
    }

    private void notice(Player player, String text) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(player.getUniqueId());
        if (last == null || now - last > 2000) {
            lastNotice.put(player.getUniqueId(), now);
            player.sendActionBar(Component.text(text, NamedTextColor.DARK_AQUA));
        }
    }

    // ---- one fight -----------------------------------------------------------------------------------

    /** One fight in one temple. */
    public final class Battle implements BossArena {

        final TempleRegistry.Temple temple;
        final World world;
        final GolemBoss.Arena bounds;
        final Location landing;
        final UUID offerer;
        final int simulated;
        final boolean practice;
        final Set<UUID> fighters = new LinkedHashSet<>();
        final Map<UUID, Location> lastInside = new HashMap<>();
        final BossBar bar;
        final Set<UUID> viewers = new LinkedHashSet<>();
        final List<UUID> helpers = new ArrayList<>();
        Stage stage = Stage.COUNTDOWN;
        int ticks;
        int empty;
        GolemBoss.Fight fight;
        GreatEchoGolem golem;
        Mob base;
        private GolemBoss.Phase barPhase;

        Battle(TempleRegistry.Temple temple, UUID offerer, int simulated, boolean practice) {
            this.temple = temple;
            Location c = registry.arenaCenter(temple);
            this.world = c.getWorld();
            this.bounds = new GolemBoss.Arena(c.getX(), c.getY(), c.getZ(), de.lamamc.sift.core.temple.TempleLayout.ARENA_RADIUS + 0.5,
                    settings.arenaHeight());
            this.landing = c.clone().add(0, 0, -6);
            this.offerer = offerer;
            this.simulated = simulated;
            this.practice = practice;
            this.bar = BossBar.bossBar(Component.text("Großer Echo-Golem", NamedTextColor.LIGHT_PURPLE), 1f, BossBar.Color.PURPLE,
                    BossBar.Overlay.NOTCHED_10);
        }

        public String id() {
            return temple.id();
        }

        boolean sealed() {
            return settings.sealGates() && (stage == Stage.DESCENT || stage == Stage.FIGHT);
        }

        boolean inArena(Player p) {
            Location l = p.getLocation();
            return l.getWorld() == world && bounds.inside(l.getX(), l.getY(), l.getZ());
        }

        private boolean canFight(Player p) {
            return !p.isDead() && p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE;
        }

        void tick() {
            ticks++;
            track();
            switch (stage) {
                case COUNTDOWN -> countdown();
                case DESCENT, FIGHT -> fightTick();
                case ENDED -> { }
            }
        }

        /** Players entering join the fight; fighters leaving through a gate are set back while the bars are up. */
        private void track() {
            for (Player p : world.getPlayers()) {
                Location l = p.getLocation();
                if (bounds.distance(l.getX(), l.getZ()) > bounds.radius() + 24) {
                    continue;
                }
                boolean inside = bounds.inside(l.getX(), l.getY(), l.getZ());
                UUID id = p.getUniqueId();
                if (inside && canFight(p)) {
                    if (fighters.add(id) && stage != Stage.COUNTDOWN) {
                        p.sendActionBar(Component.text("Du betrittst die Arena des Großen Echo-Golems.", NamedTextColor.LIGHT_PURPLE));
                    }
                    lastInside.put(id, l.clone());
                    show(p);
                } else if (!inside && fighters.contains(id) && sealed() && canFight(p)) {
                    Location back = lastInside.get(id);
                    if (back != null) {
                        settingBack = true;
                        try {
                            p.teleport(back.clone().setDirection(l.getDirection()));
                        } finally {
                            settingBack = false;
                        }
                        p.setVelocity(new org.bukkit.util.Vector());
                        p.playSound(Sound.sound(Key.key("minecraft", "block.soul_sand.break"), Sound.Source.PLAYER, 1f, 0.6f));
                        notice(p, "Das Seelengitter hält dich in der Arena.");
                    }
                }
            }
        }

        void leave(UUID id) {
            fighters.remove(id);
            lastInside.remove(id);
        }

        private void countdown() {
            int left = settings.countdownTicks() - ticks;
            if (left > 0 && left % 20 == 0) {
                actionBar(Component.text("Der Große Echo-Golem erwacht in " + left / 20 + " …", NamedTextColor.LIGHT_PURPLE));
                world.spawnParticle(Particle.SOUL, registry.altarBlock(temple).getLocation().add(0.5, 1.2, 0.5), 20, 0.4, 0.6, 0.4, 0.03);
            }
            if (left > 0) {
                return;
            }
            List<Player> present = fighters();
            int players = simulated > 0 ? simulated : present.size();
            if (players == 0) {
                end(false, "Niemand stand in der Arena; der Altar schläft wieder ein.");
                return;
            }
            fight = new GolemBoss.Fight(kind.settings(), players);
            Location spawn = landing.clone();
            spawn.setY(registry.keepTop(temple) + 3);
            spawn.setYaw(0); // facing south, towards the altar
            Mob spawned = mobs.spawn(kind, spawn, false, m -> m.getPersistentDataContainer().set(GreatEchoGolemKind.TEMPLE,
                    PersistentDataType.STRING, temple.id()));
            if (spawned == null) {
                end(false, "Der Golem konnte nicht erscheinen.");
                return;
            }
            base = spawned;
            byBoss.put(spawned.getUniqueId(), this);
            stage = Stage.DESCENT;
            ticks = 0;
            show(Title.title(Component.text("Großer Echo-Golem", NamedTextColor.LIGHT_PURPLE),
                    Component.text(String.format(java.util.Locale.ROOT, "%.0f Leben · %d %s", fight.maxHealth(), players,
                            players == 1 ? "Spieler" : "Spieler"), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(600))));
            sound(Key.key("minecraft", "entity.warden.emerge"), 2.5f, 0.6f);
            plugin.getLogger().info(String.format(java.util.Locale.ROOT, "Temple %s: the Great Echo Golem falls (%d players, %.0f health)",
                    temple.id(), players, fight.maxHealth()));
        }

        private void fightTick() {
            if (base == null || !base.isValid()) {
                if (base == null || !base.isDead()) {
                    end(false, "Der Golem ist verschwunden; der Kampf ist abgebrochen.");
                }
                return;
            }
            if (golem == null) {
                SiftMob mob = mobs.mob(base);
                golem = mob instanceof GreatEchoGolem g ? g : null;
            }
            if (stage == Stage.DESCENT && ticks > 400) {
                end(false, "Der Golem fand den Weg in die Arena nicht.");
                return;
            }
            List<Player> present = fighters();
            if (present.isEmpty() && !practice) {
                if (++empty >= settings.leaveTicks()) {
                    end(false, "Niemand mehr in der Arena: der Golem zieht sich zurück, der Altar schläft wieder.");
                    return;
                }
            } else {
                empty = 0;
            }
            bar.progress((float) Math.max(0, Math.min(1, fight.fraction())));
            if (fight.phase() != barPhase) {
                barPhase = fight.phase();
                bar.name(Component.text("Großer Echo-Golem · " + switch (barPhase) {
                    case ARMOR -> "Panzer";
                    case SOUND -> "Schall";
                    case RAGE -> "Wut";
                    case DEAD -> "besiegt";
                }, NamedTextColor.LIGHT_PURPLE));
                bar.color(switch (barPhase) {
                    case ARMOR -> BossBar.Color.BLUE;
                    case SOUND -> BossBar.Color.YELLOW;
                    case RAGE, DEAD -> BossBar.Color.RED;
                });
                if (barPhase == GolemBoss.Phase.RAGE) {
                    removeHelpers();
                    actionBar(Component.text("Der Golem rast vor Wut!", NamedTextColor.RED));
                } else if (barPhase == GolemBoss.Phase.SOUND) {
                    actionBar(Component.text("Der Panzer ist gebrochen!", NamedTextColor.YELLOW));
                }
            }
            if (sealed() && ticks % 10 == 0) {
                gates();
            }
        }

        /** Soul fire in the four gate openings while the bars are up. */
        private void gates() {
            double r = bounds.radius() + 0.6;
            for (int[] axis : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                for (int w = -1; w <= 1; w++) {
                    double x = bounds.cx() + axis[0] * r + (axis[1] != 0 ? w : 0);
                    double z = bounds.cz() + axis[1] * r + (axis[0] != 0 ? w : 0);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, x, bounds.floorY() + 1.5, z, 3, 0.1, 1.2, 0.1, 0.0);
                }
            }
        }

        GolemBoss.Result hit(Player attacker, GolemBoss.Hit hit, double amount) {
            GolemBoss.Result r = fight.hit(attacker == null ? ADMIN : attacker.getUniqueId(), hit, amount);
            if (golem != null) {
                if (r.plateBroken()) {
                    golem.plateBroken();
                } else if (r.bounced()) {
                    golem.bounced();
                } else if (r.dealt() > 0) {
                    golem.wounded();
                }
            }
            if (r.plateBroken() && settings.platesDrop() && attacker != null) {
                give(attacker, items.create("soul_block", 1));
            }
            return r;
        }

        /** Admin damage (/sift temple boss damage): the same path as a fighter's hit. */
        public GolemBoss.Result adminHit(GolemBoss.Hit hit, double amount) {
            if (fight == null || stage != Stage.FIGHT) {
                return null;
            }
            GolemBoss.Result r = hit(null, hit, amount);
            if (r.to() == GolemBoss.Phase.DEAD && base != null) {
                base.setHealth(0);
            }
            return r;
        }

        void victory() {
            List<UUID> present = fighters().stream().map(Player::getUniqueId).toList();
            Set<UUID> eligible = fight.eligible(present);
            registry.defeat(temple.id());
            int given = 0;
            for (UUID id : eligible) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && settings.lootNightCrystals() > 0) {
                    give(p, items.create("golem_trophy", settings.lootNightCrystals()));
                    p.sendMessage(Component.text("Du erhältst " + (settings.lootNightCrystals() == 1 ? "einen Nachtkristall."
                            : settings.lootNightCrystals() + " Nachtkristalle."), NamedTextColor.LIGHT_PURPLE));
                    given++;
                }
            }
            show(Title.title(Component.text("Der Große Echo-Golem ist besiegt", NamedTextColor.LIGHT_PURPLE),
                    Component.text("Der Tempel ist befreit.", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
            sound(Key.key("minecraft", "ui.toast.challenge_complete"), 1f, 1f);
            plugin.getLogger().info("Temple " + temple.id() + ": the Great Echo Golem is defeated; loot for " + given + " of "
                    + eligible.size() + " eligible players");
            end(true, null);
        }

        /** Ends the fight: {@code won} = the temple is DEFEATED (already recorded), else it sleeps again. */
        void end(boolean won, String message) {
            if (stage == Stage.ENDED) {
                return;
            }
            stage = Stage.ENDED;
            if (message != null) {
                say(Component.text(message, NamedTextColor.DARK_AQUA));
            }
            removeHelpers();
            if (base != null) {
                byBoss.remove(base.getUniqueId());
                if (base.isValid() && !base.isDead()) {
                    world.spawnParticle(Particle.SOUL, base.getLocation().add(0, 2, 0), 60, 1.5, 2, 1.5, 0.03);
                    base.remove();
                }
            }
            for (UUID id : viewers) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.hideBossBar(bar);
                }
            }
            viewers.clear();
            if (!won) {
                registry.fighting(temple.id(), false);
                if (settings.refundOffering() && offerer != null && settings.offeringAmount() > 0) {
                    Player p = Bukkit.getPlayer(offerer);
                    if (p != null) {
                        give(p, items.create(settings.offeringItem(), settings.offeringAmount()));
                    }
                }
                plugin.getLogger().info("Temple " + temple.id() + ": boss fight called off" + (message == null ? "" : " (" + message + ")"));
            }
            battles.remove(temple.id());
        }

        void show(Player p) {
            if (viewers.add(p.getUniqueId())) {
                p.showBossBar(bar);
            }
        }

        void hide(Player p) {
            if (viewers.remove(p.getUniqueId())) {
                p.hideBossBar(bar);
            }
        }

        private void removeHelpers() {
            for (UUID id : helpers) {
                Entity e = Bukkit.getEntity(id);
                if (e != null && e.isValid()) {
                    world.spawnParticle(Particle.SOUL, e.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.02);
                    e.remove();
                }
            }
            helpers.clear();
        }

        private void give(Player p, ItemStack stack) {
            if (stack == null) {
                return;
            }
            for (ItemStack rest : p.getInventory().addItem(stack).values()) {
                p.getWorld().dropItem(p.getLocation(), rest, item -> item.setOwner(p.getUniqueId()));
            }
        }

        private void say(Component text) {
            for (Player p : nearby()) {
                p.sendMessage(text);
            }
        }

        private void actionBar(Component text) {
            for (Player p : nearby()) {
                p.sendActionBar(text);
            }
        }

        private void show(Title title) {
            for (Player p : nearby()) {
                p.showTitle(title);
            }
        }

        private void sound(Key key, float volume, float pitch) {
            world.playSound(Sound.sound(key, Sound.Source.HOSTILE, volume, pitch), bounds.cx(), bounds.floorY() + 2, bounds.cz());
        }

        /** Players in or around the arena (fighters and those watching from the gates or the keep). */
        private List<Player> nearby() {
            List<Player> out = new ArrayList<>();
            for (Player p : world.getPlayers()) {
                Location l = p.getLocation();
                if (bounds.distance(l.getX(), l.getZ()) <= bounds.radius() + 12 && Math.abs(l.getY() - bounds.floorY()) <= 32) {
                    out.add(p);
                }
            }
            return out;
        }

        // ---- BossArena ----

        @Override
        public GolemBoss.Fight fight() {
            return fight;
        }

        @Override
        public GolemBoss.Arena bounds() {
            return bounds;
        }

        @Override
        public List<Player> fighters() {
            List<Player> out = new ArrayList<>();
            for (UUID id : fighters) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && canFight(p) && inArena(p)) {
                    out.add(p);
                }
            }
            return out;
        }

        @Override
        public Location landing() {
            return landing.clone();
        }

        @Override
        public void landed(GreatEchoGolem golem) {
            this.golem = golem;
            stage = Stage.FIGHT;
            ticks = 0;
            for (Player p : fighters()) {
                show(p);
            }
            if (settings.sealGates()) {
                actionBar(Component.text("Seelengitter versperren die Tore.", NamedTextColor.DARK_AQUA));
            }
        }

        @Override
        public void soulCall(GreatEchoGolem golem) {
            for (int i = 0; i < settings.helpers(); i++) {
                double a = Math.PI / 2 + i * Math.PI; // east and west
                Location at = new Location(world, bounds.cx() + Math.cos(a) * 9, bounds.floorY(), bounds.cz() + Math.sin(a) * 9);
                Mob helper = mobs.spawn(helperKind, at, false, m -> m.getPersistentDataContainer().set(SoulHelperKind.BOSS,
                        PersistentDataType.STRING, golem.sheep().getUniqueId().toString()));
                if (helper != null) {
                    helpers.add(helper.getUniqueId());
                    world.spawnParticle(Particle.SOUL, at.clone().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0.02);
                }
            }
            if (!helpers.isEmpty()) {
                actionBar(Component.text("Der Golem ruft nach Seelen – haltet seine Helfer auf!", NamedTextColor.YELLOW));
            }
        }

        @Override
        public boolean bringSoul() {
            if (fight == null || !fight.addPlate()) {
                return false;
            }
            sound(Key.key("minecraft", "block.respawn_anchor.charge"), 1.5f, 0.7f);
            return true;
        }

        @Override
        public List<Location> soulRing() {
            return registry.soulRing(temple);
        }

        /** For /sift temple boss info. */
        public String describe() {
            return String.format(java.util.Locale.ROOT, "%s: %s, %d Kämpfer (%d in der Arena)%s%s", temple.id(), stage, fighters.size(),
                    fighters().size(), fight == null ? "" : String.format(java.util.Locale.ROOT, ", Phase %s, Leben %.0f/%.0f, Platten %d",
                    fight.phase(), fight.health(), fight.maxHealth(), fight.plates()),
                    golem == null ? "" : ", " + golem.debugState());
        }
    }
}
