package de.lamamc.sift.world.temple;

import de.lamamc.sift.core.boss.GearTable;
import de.lamamc.sift.core.boss.GolemBoss;
import de.lamamc.sift.core.lang.Lang;
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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
                           boolean sealGates, boolean refundOffering, boolean platesDrop, int helpers, int lootNightCrystals,
                           GearTable gear, int gearRolls, boolean advancement, int lootExperience, int experienceOrb) {

        public static Settings of(ConfigurationSection c) {
            ConfigurationSection start = sub(c, "start");
            ConfigurationSection reset = sub(c, "reset");
            ConfigurationSection arena = sub(c, "arena");
            return new Settings(c.getBoolean("enabled", true), start.getString("offering", "the_sift:soul_block").replace("the_sift:", ""),
                    Math.max(0, start.getInt("amount", 8)), Math.max(0, start.getInt("countdown-seconds", 5)) * 20,
                    Math.max(1, reset.getInt("leave-seconds", 30)) * 20, arena.getDouble("height", 10),
                    arena.getBoolean("seal-gates", true), reset.getBoolean("refund-offering", false),
                    sub(c, "phases").getBoolean("plates-drop", true), Math.max(0, sub(c, "phases").getInt("helpers", 2)),
                    Math.max(0, sub(c, "loot").getInt("night-crystal", 1)), gear(sub(c, "loot.gear")),
                    sub(c, "loot.gear").getBoolean("enabled", true) ? Math.max(0, sub(c, "loot.gear").getInt("rolls", 1)) : 0,
                    c.getBoolean("advancement", true), Math.max(0, sub(c, "loot").getInt("experience", 500)),
                    Math.max(1, sub(c, "loot").getInt("experience-orb", 20)));
        }

        /** loot.gear.table: list of {item, weight, enchantments: {id: level | [min, max]}}; broken entries are skipped. */
        private static GearTable gear(ConfigurationSection c) {
            List<GearTable.Entry> entries = new ArrayList<>();
            for (Map<?, ?> m : c.getMapList("table")) {
                Object item = m.get("item");
                if (!(item instanceof String id) || Material.matchMaterial(id) == null) {
                    Bukkit.getLogger().warning("[TheSift] phase4.bosses.great-echo-golem.loot.gear.table: unknown item " + item);
                    continue;
                }
                int weight = m.get("weight") instanceof Number n ? n.intValue() : 1;
                Map<String, int[]> enchantments = new LinkedHashMap<>();
                if (m.get("enchantments") instanceof Map<?, ?> en) {
                    en.forEach((k, v) -> {
                        int[] range = GearTable.range(v);
                        String key = String.valueOf(k).contains(":") ? String.valueOf(k) : "minecraft:" + k;
                        if (range == null || enchantment(key) == null) {
                            Bukkit.getLogger().warning("[TheSift] phase4.bosses.great-echo-golem.loot.gear.table: " + id
                                    + ": skipped enchantment " + k + " = " + v);
                        } else {
                            enchantments.put(key, range);
                        }
                    });
                }
                entries.add(new GearTable.Entry(id, weight, enchantments));
            }
            return new GearTable(entries);
        }

        private static ConfigurationSection sub(ConfigurationSection c, String path) {
            ConfigurationSection s = c == null ? null : c.getConfigurationSection(path);
            return s != null ? s : new org.bukkit.configuration.MemoryConfiguration();
        }
    }

    private enum Stage { COUNTDOWN, DESCENT, FIGHT, ENDED }

    /** Advancement for everyone who earned loot (datapack the_sift:the_sift/great_echo_golem, alpha.42b.2). */
    public static final NamespacedKey ADVANCEMENT = new NamespacedKey("the_sift", "the_sift/great_echo_golem");

    static org.bukkit.enchantments.Enchantment enchantment(String key) {
        NamespacedKey k = NamespacedKey.fromString(key);
        return k == null ? null : io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT).get(k);
    }

    /** One drawn piece as an item: enchantments above the vanilla maximum are capped to it. */
    static ItemStack gearItem(GearTable.Roll roll) {
        Material material = Material.matchMaterial(roll.item());
        if (material == null) {
            return null;
        }
        ItemStack stack = ItemStack.of(material);
        roll.enchantments().forEach((key, level) -> {
            org.bukkit.enchantments.Enchantment e = enchantment(key);
            if (e != null) {
                stack.addUnsafeEnchantment(e, Math.min(level, e.getMaxLevel()));
            }
        });
        return stack;
    }

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

    /** Test aid (/sift temple boss loot): draws pieces from the gear table as the victory would, as text. */
    public List<String> previewLoot(int count) {
        List<String> out = new ArrayList<>();
        java.util.SplittableRandom random = new java.util.SplittableRandom();
        for (int i = 0; i < count; i++) {
            GearTable.Roll roll = settings.gear().roll(random);
            ItemStack piece = roll == null ? null : gearItem(roll);
            if (piece == null) {
                out.add("(nichts – Tabelle leer)");
                continue;
            }
            StringBuilder line = new StringBuilder(piece.getType().getKey().getKey());
            piece.getEnchantments().forEach((e, level) -> line.append(", ").append(e.getKey().getKey()).append(' ').append(level));
            out.add(line.toString());
        }
        return out;
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
            player.sendActionBar(msg().get(player, "boss.altar-off"));
            return;
        }
        if (battles.containsKey(temple.id())) {
            player.sendActionBar(msg().get(player, "boss.already-awake"));
            return;
        }
        // alpha.42f: one altar per player (before the offering is taken)
        var bindings = registry.bindings();
        var verdict = bindings.check(player.getUniqueId(), temple.id(), registry.altarOnePerPlayer(),
                player.hasPermission(TempleRegistry.BYPASS));
        var bound = bindings.get(player.getUniqueId());
        switch (verdict) {
            case OTHER_TEMPLE -> {
                player.sendActionBar(msg().get(player, "temple.altar-chosen", "temple", bound.temple()));
                return;
            }
            case DEFEATED -> {
                player.sendActionBar(bound.temple().equals(temple.id()) ? msg().get(player, "temple.altar-won-here")
                        : msg().get(player, "temple.altar-won", "temple", bound.temple()));
                return;
            }
            default -> {
            }
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (settings.offeringAmount() > 0 && !creative
                && (!settings.offeringItem().equals(SiftItems.idOf(hand)) || hand.getAmount() < settings.offeringAmount())) {
            player.sendActionBar(msg().get(player, "boss.offering", "amount", String.valueOf(settings.offeringAmount())));
            return;
        }
        if (settings.offeringAmount() > 0 && !creative) {
            hand.setAmount(hand.getAmount() - settings.offeringAmount());
            player.getInventory().setItemInMainHand(hand.getAmount() > 0 ? hand : null);
        }
        if (verdict == de.lamamc.sift.core.temple.AltarBindings.Verdict.ALLOW && bindings.woke(player.getUniqueId(), temple.id())) {
            registry.saveBindings();
            plugin.getLogger().info("Temple " + temple.id() + ": altar bound to " + player.getName());
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
        b.say("boss.awakening");
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
                b.end(false, "boss.error");
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
            notice(attacker, "boss.outside");
            return;
        }
        GolemBoss.Result r = b.hit(attacker, hit, event.getFinalDamage());
        if (r.bounced()) {
            event.setCancelled(true);
            notice(attacker, "boss.bounced");
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
                        b.end(false, "boss.vanished");
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
                notice(player, "boss.bars-hold");
            } else {
                b.leave(player.getUniqueId());
            }
        }
    }

    /** A messages key in the player's language, at most every 2 s. */
    private void notice(Player player, String key) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(player.getUniqueId());
        if (last == null || now - last > 2000) {
            lastNotice.put(player.getUniqueId(), now);
            player.sendActionBar(msg().get(player, key));
        }
    }

    private static de.lamamc.sift.Messages msg() {
        return de.lamamc.sift.Messages.current();
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
        /** Players the fight started with (health, damage, helpers and pace, alpha.42f). */
        int players = 1;
        final Set<UUID> fighters = new LinkedHashSet<>();
        final Map<UUID, Location> lastInside = new HashMap<>();
        /** One boss bar per language (alpha.42c): each viewer sees the one of their client language. */
        final Map<Lang, BossBar> bars = new java.util.EnumMap<>(Lang.class);
        final Map<UUID, Lang> viewers = new LinkedHashMap<>();
        final List<UUID> helpers = new ArrayList<>();
        Stage stage = Stage.COUNTDOWN;
        int ticks;
        int empty;
        GolemBoss.Fight fight;
        GreatEchoGolem golem;
        Mob base;
        private GolemBoss.Phase barPhase;
        private int barPlates = -1;

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
            for (Lang lang : Lang.values()) {
                bars.put(lang, BossBar.bossBar(msg().get(lang, "boss.title"), 1f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10));
            }
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
                        p.sendActionBar(msg().get(p, "boss.enter"));
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
                        notice(p, "boss.bars-hold");
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
                actionBar("boss.countdown", "seconds", String.valueOf(left / 20));
                world.spawnParticle(Particle.SOUL, registry.altarBlock(temple).getLocation().add(0.5, 1.2, 0.5), 20, 0.4, 0.6, 0.4, 0.03);
            }
            if (left > 0) {
                return;
            }
            List<Player> present = fighters();
            int players = simulated > 0 ? simulated : present.size();
            if (players == 0) {
                end(false, "boss.nobody-at-start");
                return;
            }
            fight = new GolemBoss.Fight(kind.settings(), players);
            this.players = players;
            Location spawn = landing.clone();
            spawn.setY(registry.keepTop(temple) + 3);
            spawn.setYaw(0); // facing south, towards the altar
            Mob spawned = mobs.spawn(kind, spawn, false, m -> m.getPersistentDataContainer().set(GreatEchoGolemKind.TEMPLE,
                    PersistentDataType.STRING, temple.id()));
            if (spawned == null) {
                end(false, "boss.no-spawn");
                return;
            }
            base = spawned;
            byBoss.put(spawned.getUniqueId(), this);
            stage = Stage.DESCENT;
            ticks = 0;
            title("boss.title", "boss.subtitle", Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(600)),
                    "health", String.format(java.util.Locale.ROOT, "%.0f", fight.maxHealth()), "players", String.valueOf(players));
            sound(Key.key("minecraft", "entity.warden.emerge"), 2.5f, 0.6f);
            plugin.getLogger().info(String.format(java.util.Locale.ROOT, "Temple %s: the Great Echo Golem falls (%d players, %.0f health)",
                    temple.id(), players, fight.maxHealth()));
        }

        private void fightTick() {
            if (base == null || !base.isValid()) {
                if (base == null || !base.isDead()) {
                    end(false, "boss.vanished");
                }
                return;
            }
            if (golem == null) {
                SiftMob mob = mobs.mob(base);
                golem = mob instanceof GreatEchoGolem g ? g : null;
                if (golem != null) {
                    golem.attacks(kind.attacks(players));
                }
            }
            if (stage == Stage.DESCENT && ticks > 400) {
                end(false, "boss.no-landing");
                return;
            }
            List<Player> present = fighters();
            if (present.isEmpty() && !practice) {
                if (++empty >= settings.leaveTicks()) {
                    end(false, "boss.nobody-left");
                    return;
                }
            } else {
                empty = 0;
            }
            float progress = (float) Math.max(0, Math.min(1, fight.fraction()));
            bars.values().forEach(bar -> bar.progress(progress));
            if (fight.phase() != barPhase || fight.plates() != barPlates) {
                boolean phaseChanged = fight.phase() != barPhase;
                barPhase = fight.phase();
                barPlates = fight.plates();
                String key = switch (barPhase) {
                    case ARMOR -> "boss.bar.armor";
                    case SOUND -> barPlates > 0 ? "boss.bar.sound-plates" : "boss.bar.sound";
                    case RAGE -> "boss.bar.rage";
                    case DEAD -> "boss.bar.defeated";
                };
                for (Map.Entry<Lang, BossBar> e : bars.entrySet()) {
                    String plates = msg().raw(e.getKey(), barPlates == 1 ? "boss.bar.plate-one" : "boss.bar.plate-many")
                            .replace("<count>", String.valueOf(barPlates));
                    e.getValue().name(msg().get(e.getKey(), key, "plates", plates));
                }
                if (phaseChanged) {
                    BossBar.Color color = switch (barPhase) {
                        case ARMOR -> BossBar.Color.BLUE;
                        case SOUND -> BossBar.Color.YELLOW;
                        case RAGE, DEAD -> BossBar.Color.RED;
                    };
                    bars.values().forEach(bar -> bar.color(color));
                    if (barPhase == GolemBoss.Phase.RAGE) {
                        removeHelpers();
                        actionBar("boss.rage");
                    } else if (barPhase == GolemBoss.Phase.SOUND) {
                        actionBar("boss.armor-broken");
                    }
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
            if (offerer != null) {
                // alpha.42f: whoever woke this temple's altar has used up the place (not the fellow fighters)
                int used = registry.bindings().defeated(temple.id());
                if (used > 0) {
                    registry.saveBindings();
                }
            }
            int given = 0;
            java.util.SplittableRandom random = new java.util.SplittableRandom();
            for (UUID id : eligible) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) {
                    continue;
                }
                List<Component> got = new ArrayList<>();
                if (settings.lootNightCrystals() > 0) {
                    give(p, items.create("golem_trophy", settings.lootNightCrystals()));
                    got.add(msg().get(p, settings.lootNightCrystals() == 1 ? "boss.night-crystal-one" : "boss.night-crystal-many",
                            "count", String.valueOf(settings.lootNightCrystals())));
                }
                for (int i = 0; i < settings.gearRolls(); i++) {
                    GearTable.Roll roll = settings.gear().roll(random);
                    ItemStack piece = roll == null ? null : gearItem(roll);
                    if (piece != null) {
                        give(p, piece);
                        got.add(Component.translatable(piece.translationKey()).append(msg().get(p, "boss.enchanted")));
                    }
                }
                if (!got.isEmpty()) {
                    p.sendMessage(msg().get(p, "general.you-receive-list", net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component(
                            "items", Component.join(net.kyori.adventure.text.JoinConfiguration.commas(true), got))));
                }
                if (settings.advancement()) {
                    org.bukkit.advancement.Advancement adv = Bukkit.getAdvancement(ADVANCEMENT);
                    if (adv != null) {
                        var progress = p.getAdvancementProgress(adv);
                        progress.getRemainingCriteria().forEach(progress::awardCriteria);
                    }
                }
                given++;
            }
            // alpha.42i: experience shared among those who earn loot, as orbs at their feet (Mending repairs with them)
            List<Player> earners = eligible.stream().map(Bukkit::getPlayer).filter(p -> p != null && p.getWorld().equals(world)).toList();
            List<List<Integer>> shares = GolemBoss.experienceShares(settings.lootExperience(), earners.size(), settings.experienceOrb());
            for (int i = 0; i < shares.size(); i++) {
                Location feet = earners.get(i).getLocation();
                for (int orb : shares.get(i)) {
                    world.spawn(feet, org.bukkit.entity.ExperienceOrb.class, o -> o.setExperience(orb));
                }
            }
            if (base != null) {
                Location at = base.getLocation().add(0, base.getHeight() * 0.6, 0);
                world.spawnParticle(Particle.SOUL, at, 120, 1.6, 2.0, 1.6, 0.06);
                world.spawnParticle(Particle.BLOCK, at, 80, 1.4, 1.8, 1.4, 0, Material.AMETHYST_BLOCK.createBlockData());
                world.playSound(Sound.sound(Key.key("minecraft", "entity.warden.death"), Sound.Source.HOSTILE, 2.5f, 0.6f),
                        at.getX(), at.getY(), at.getZ());
            }
            title("boss.defeated-title", "boss.defeated-subtitle",
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800)));
            sound(Key.key("minecraft", "ui.toast.challenge_complete"), 1f, 1f);
            plugin.getLogger().info("Temple " + temple.id() + ": the Great Echo Golem is defeated; loot for " + given + " of "
                    + eligible.size() + " eligible players");
            end(true, null);
        }

        /** Ends the fight: {@code won} = the temple is DEFEATED (already recorded), else it sleeps again; {@code message}: messages key. */
        void end(boolean won, String message) {
            if (stage == Stage.ENDED) {
                return;
            }
            stage = Stage.ENDED;
            if (message != null) {
                say(message);
            }
            removeHelpers();
            if (base != null) {
                byBoss.remove(base.getUniqueId());
                if (base.isValid() && !base.isDead()) {
                    world.spawnParticle(Particle.SOUL, base.getLocation().add(0, 2, 0), 60, 1.5, 2, 1.5, 0.03);
                    base.remove();
                }
            }
            viewers.forEach((id, lang) -> {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.hideBossBar(bars.get(lang));
                }
            });
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
            Lang lang = msg().lang(p);
            Lang before = viewers.put(p.getUniqueId(), lang);
            if (before != lang) {
                if (before != null) {
                    p.hideBossBar(bars.get(before));
                }
                p.showBossBar(bars.get(lang));
            }
        }

        void hide(Player p) {
            Lang lang = viewers.remove(p.getUniqueId());
            if (lang != null) {
                p.hideBossBar(bars.get(lang));
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

        /** A messages key to everyone around, each in their language. */
        private void say(String key, String... placeholders) {
            for (Player p : nearby()) {
                p.sendMessage(msg().get(p, key, placeholders));
            }
        }

        private void actionBar(String key, String... placeholders) {
            for (Player p : nearby()) {
                p.sendActionBar(msg().get(p, key, placeholders));
            }
        }

        private void title(String titleKey, String subtitleKey, Title.Times times, String... placeholders) {
            for (Player p : nearby()) {
                p.showTitle(Title.title(msg().get(p, titleKey, placeholders), msg().get(p, subtitleKey, placeholders), times));
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
                actionBar("boss.bars-up");
            }
        }

        @Override
        public void soulCall(GreatEchoGolem golem) {
            int count = settings.helpers() + kind.scaling().extraHelpers(players);
            for (int i = 0; i < count; i++) {
                double a = Math.PI / 2 + i * 2 * Math.PI / count; // east and west, more spread around the arena
                Location at = new Location(world, bounds.cx() + Math.cos(a) * 9, bounds.floorY(), bounds.cz() + Math.sin(a) * 9);
                Mob helper = mobs.spawn(helperKind, at, false, m -> m.getPersistentDataContainer().set(SoulHelperKind.BOSS,
                        PersistentDataType.STRING, golem.sheep().getUniqueId().toString()));
                if (helper != null) {
                    helpers.add(helper.getUniqueId());
                    world.spawnParticle(Particle.SOUL, at.clone().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0.02);
                }
            }
            if (!helpers.isEmpty()) {
                actionBar("boss.soul-call");
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
