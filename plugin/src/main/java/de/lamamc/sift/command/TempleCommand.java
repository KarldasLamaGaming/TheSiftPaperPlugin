package de.lamamc.sift.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.lamamc.sift.SiftPlugin;
import de.lamamc.sift.core.temple.TempleSites;
import de.lamamc.sift.world.SiftWorlds;
import de.lamamc.sift.world.temple.TempleRegistry;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

/**
 * /sift temple list|info|sites|reset|reset-all|rebuild|inspect|validate|defeat|spawners|boss (phase 4, alpha.41a; rebuild/inspect
 * alpha.41.1, spawners alpha.42a, boss alpha.42b, validate alpha.42e, altar alpha.42f).
 */
final class TempleCommand {

    private final SiftPlugin plugin;

    TempleCommand(SiftPlugin plugin) {
        this.plugin = plugin;
    }

    LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("temple")
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("sites").executes(this::sites))
                .then(Commands.literal("info").then(id().executes(ctx -> withTemple(ctx, this::info))))
                .then(Commands.literal("reset").then(id().executes(ctx -> withTemple(ctx, this::reset))))
                .then(Commands.literal("reset-all").executes(ctx -> resetAll(ctx, false))
                        .then(Commands.literal("confirm").executes(ctx -> resetAll(ctx, true))))
                .then(Commands.literal("rebuild").then(id().executes(ctx -> withTemple(ctx, this::rebuild))))
                .then(Commands.literal("spawners")
                        .then(id().executes(ctx -> withTemple(ctx, this::spawners)))
                        .then(Commands.literal("clear").then(id().executes(ctx -> withTemple(ctx, (s, r, t) -> setSpawners(s, r, t, true)))))
                        .then(Commands.literal("reset").then(id().executes(ctx -> withTemple(ctx, (s, r, t) -> setSpawners(s, r, t, false)))))
                        // test aid without players: counts as N players at every spawner of the temple for a while
                        .then(Commands.literal("simulate").then(id()
                                .then(Commands.argument("players", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 8))
                                        .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3600))
                                                .executes(ctx -> withTemple(ctx, (s, r, t) -> {
                                                    var brood = plugin.brood();
                                                    if (brood == null) {
                                                        s.sendRichMessage("<red>Brut-Spawner sind aus.");
                                                        return 0;
                                                    }
                                                    brood.simulate(t, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "players"),
                                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds"));
                                                    s.sendRichMessage("<yellow>Testhilfe: " + t.id() + " zählt so viele Spieler an jedem Spawner.");
                                                    return Command.SINGLE_SUCCESS;
                                                })))))))
                .then(Commands.literal("inspect").then(id().executes(ctx -> withTemple(ctx, this::inspect))))
                // alpha.42f: one altar per player
                .then(Commands.literal("altar")
                        .then(Commands.literal("status").then(playerName().executes(ctx -> altar(ctx, false))))
                        .then(Commands.literal("reset").then(playerName().executes(ctx -> altar(ctx, true)))))
                // alpha.42e: can a 3 x 3 x 3 body walk the built temple (from the gate to every spawner, the altar, chests)?
                .then(Commands.literal("validate").executes(this::validateAll).then(id().executes(ctx -> withTemple(ctx, this::validate))))
                // test aid: the temple counts as defeated (no fight, no loot)
                .then(Commands.literal("defeat").then(id().executes(ctx -> withTemple(ctx, this::defeat))))
                // the Great Echo Golem (alpha.42b): start without offering and gate, call off, state, damage as a test aid
                .then(Commands.literal("boss")
                        .then(Commands.literal("info").executes(this::bossInfo).then(id().executes(ctx -> withTemple(ctx, this::bossInfo))))
                        .then(Commands.literal("start").then(id().executes(ctx -> withTemple(ctx, (s, r, t) -> bossStart(s, r, t, 0)))
                                .then(Commands.argument("players", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 8))
                                        .executes(ctx -> withTemple(ctx, (s, r, t) -> bossStart(s, r, t,
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "players")))))))
                        .then(Commands.literal("stop").then(id().executes(ctx -> withTemple(ctx, this::bossStop))))
                        .then(Commands.literal("loot").then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    var bosses = bosses(sender);
                                    if (bosses == null) {
                                        return 0;
                                    }
                                    sender.sendRichMessage("<gold>Beute-Vorschau (Netherit-Tabelle, ohne Nachtkristall):");
                                    bosses.previewLoot(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count"))
                                            .forEach(line -> sender.sendRichMessage("<gray>  " + line));
                                    return Command.SINGLE_SUCCESS;
                                })))
                        .then(Commands.literal("damage").then(id()
                                .then(Commands.argument("amount", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 100_000))
                                        .executes(ctx -> withTemple(ctx, (s, r, t) -> bossDamage(s, t,
                                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "amount"), false)))
                                        .then(Commands.literal("ranged").executes(ctx -> withTemple(ctx, (s, r, t) -> bossDamage(s, t,
                                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "amount"), true))))))));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> id() {
        return Commands.argument("id", StringArgumentType.word()).suggests((ctx, builder) -> {
            TempleRegistry temples = plugin.temples();
            if (temples != null) {
                temples.all().forEach(t -> builder.suggest(t.id()));
            }
            return builder.buildFuture();
        });
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> playerName() {
        return Commands.argument("player", StringArgumentType.word()).suggests((ctx, builder) -> {
            Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
            return builder.buildFuture();
        });
    }

    /** /sift temple altar status|reset <player> (alpha.42f); offline players by their cached name (no web lookup). */
    private int altar(CommandContext<CommandSourceStack> ctx, boolean reset) {
        CommandSender sender = ctx.getSource().getSender();
        TempleRegistry temples = plugin.temples();
        if (temples == null) {
            sender.sendRichMessage("<red>Tempel sind aus (phase4.temples.enabled) oder die Sift ist nicht geladen.");
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "player");
        org.bukkit.OfflinePlayer target = Bukkit.getPlayerExact(name);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(name);
        }
        if (target == null) {
            sender.sendRichMessage("<red>Spieler „" + name + "“ ist diesem Server unbekannt.");
            return 0;
        }
        String shown = target.getName() != null ? target.getName() : name;
        var bindings = temples.bindings();
        var b = bindings.get(target.getUniqueId());
        String rule = temples.altarOnePerPlayer() ? "" : " <yellow>(Regel aus: altar-one-per-player.enabled)</yellow>";
        if (!reset) {
            sender.sendRichMessage(b == null ? "<gray>" + shown + " ist an keinen Tempel gebunden (darf jeden Altar wecken)." + rule
                    : "<gray>" + shown + " ist an <white>" + b.temple() + "</white> gebunden"
                            + (b.defeated() ? ", Boss besiegt: <red>kein Altar mehr</red>" : ": nur dieser Altar") + "." + rule);
            return Command.SINGLE_SUCCESS;
        }
        if (bindings.reset(target.getUniqueId())) {
            temples.saveBindings();
            plugin.getLogger().info("Altar binding of " + shown + " (" + b.temple() + ") lifted by " + sender.getName());
            sender.sendRichMessage("<green>Bindung von " + shown + " an " + b.temple() + " aufgehoben.");
        } else {
            sender.sendRichMessage("<gray>" + shown + " war an keinen Tempel gebunden.");
        }
        return Command.SINGLE_SUCCESS;
    }

    private interface Action {
        int run(CommandSender sender, TempleRegistry temples, TempleRegistry.Temple temple);
    }

    private int withTemple(CommandContext<CommandSourceStack> ctx, Action action) {
        CommandSender sender = ctx.getSource().getSender();
        TempleRegistry temples = plugin.temples();
        if (temples == null) {
            sender.sendRichMessage("<red>Tempel sind aus (phase4.temples.enabled) oder die Sift ist nicht geladen.");
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id");
        var temple = temples.get(id);
        if (temple.isEmpty()) {
            sender.sendRichMessage("<red>Kein gebauter Tempel „" + id + "“. <gray>/sift temple sites zeigt alle Plätze.");
            return 0;
        }
        return action.run(sender, temples, temple.get());
    }

    private static String tp(int x, int y, int z) {
        return "<click:run_command:'/execute in the_sift:the_sift run tp @s " + x + " " + y + " " + z + "'><aqua>[" + x + " " + y + " "
                + z + "]</aqua></click>";
    }

    private static String state(TempleRegistry.State state) {
        return switch (state) {
            case SLEEPING -> "<gray>schläft</gray>";
            case FIGHT -> "<red>Kampf</red>";
            case DEFEATED -> "<green>besiegt</green>";
        };
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        TempleRegistry temples = plugin.temples();
        if (temples == null) {
            sender.sendRichMessage("<red>Tempel sind aus (phase4.temples.enabled) oder die Sift ist nicht geladen.");
            return 0;
        }
        var all = temples.all();
        sender.sendRichMessage("<gold>Gebaute Tempel: <white>" + all.size() + "</white> von <white>" + temples.builder().sites().size()
                + "</white> Plätzen");
        for (var t : all) {
            sender.sendRichMessage("<gray>  <white>" + t.id() + "</white> " + state(t.state()) + " " + tp(t.x(), t.y(), t.z() + gate(temples))
                    + (t.version() < TempleRegistry.CURRENT ? " <yellow>(älterer Bau, rebuild)</yellow>" : ""));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** All computed sites with whether their chunk exists and whether the temple is registered. */
    private int sites(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        TempleRegistry temples = plugin.temples();
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (temples == null || sift == null) {
            sender.sendRichMessage("<red>Tempel sind aus (phase4.temples.enabled) oder die Sift ist nicht geladen.");
            return 0;
        }
        var stats = temples.builder().stats();
        long slices = stats.slices.get();
        long late = stats.deferred.get();
        sender.sendRichMessage(String.format(Locale.ROOT, "<gold>Tempel-Plätze (Ring um 0/0):</gold> <gray>seit dem Start %d Chunk-Teile "
                        + "in der Weltgenerierung (im Mittel %.2f ms, höchstens %.2f ms), %d nachgebaut auf dem Main-Thread (im Mittel "
                        + "%.2f ms, höchstens %.2f ms)", slices, slices == 0 ? 0 : stats.nanos.get() / 1e6 / slices, stats.maxNanos.get() / 1e6,
                late, late == 0 ? 0 : stats.deferredNanos.get() / 1e6 / late, stats.deferredMaxNanos.get() / 1e6));
        for (TempleSites.Site site : temples.builder().sites()) {
            boolean generated = sift.isChunkGenerated(site.x() >> 4, site.z() >> 4);
            var temple = temples.get(site.id());
            int distance = (int) Math.sqrt((double) site.x() * site.x() + (double) site.z() * site.z());
            sender.sendRichMessage("<gray>  <white>" + site.id() + "</white> bei " + site.x() + " " + site.z() + " (" + distance + " Blöcke): "
                    + (temple.isPresent() ? "gebaut, " + state(temple.get().state())
                    : generated ? "<red>Chunk schon generiert, aber kein Tempel (vor alpha.41a erkundet)</red>" : "noch nicht generiert")
                    + " " + tp(site.x(), temple.map(TempleRegistry.Temple::y).orElse(200), site.z() + gate(temples)));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** In front of the gate (south). */
    private static int gate(TempleRegistry temples) {
        return temples.builder().params().radius() + 3;
    }

    private int rebuild(CommandSender sender, TempleRegistry temples, TempleRegistry.Temple t) {
        if (!temples.rebuild(t.id(), message -> sender.sendRichMessage("<green>" + message))) {
            sender.sendRichMessage("<red>Gerade läuft schon ein Neubau, oder der Platz ist unbekannt.");
            return 0;
        }
        sender.sendRichMessage("<yellow>Baue " + t.id() + " neu (alten Bau entfernen, dann Chunk für Chunk, je einer pro Tick) …");
        return Command.SINGLE_SUCCESS;
    }

    private int spawners(CommandSender sender, TempleRegistry temples, TempleRegistry.Temple t) {
        if (!temples.hasSpawners(t)) {
            sender.sendRichMessage("<yellow>" + t.id() + " hat keine Brut-Spawner (alter Bau: /sift temple rebuild " + t.id() + ").");
            return 0;
        }
        var states = temples.spawnerStates(t);
        var plan = temples.spawners(t);
        var brood = plugin.brood();
        sender.sendRichMessage("<gold>Brut-Spawner von " + t.id() + ": <white>" + temples.spawnersAwake(t) + "</white> wach, lebende Brut "
                + (brood == null ? "– (aus)" : brood.alive(t)));
        for (int i = 0; i < states.length; i++) {
            var b = temples.spawnerBlock(t, i);
            var s = states[i];
            String until = s.phase() == de.lamamc.sift.core.temple.BroodSpawner.Phase.EXHAUSTED
                    ? (s.until() == Long.MAX_VALUE ? " (bis zum Reset)" : " (bis " + java.time.Instant.ofEpochMilli(s.until()) + ")") : "";
            sender.sendRichMessage("<gray>  #" + (i + 1) + " Zone " + plan.get(i).zone() + " " + tp(b.getX(), b.getY() + 1, b.getZ()) + " "
                    + s.phase() + (s.wave() > 0 ? ", Welle " + s.wave() : "") + until);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** clear: all exhausted for good (the altar gate opens), reset: all waiting again; both remove the living brood. */
    private int setSpawners(CommandSender sender, TempleRegistry temples, TempleRegistry.Temple t, boolean clear) {
        if (!temples.hasSpawners(t)) {
            sender.sendRichMessage("<yellow>" + t.id() + " hat keine Brut-Spawner.");
            return 0;
        }
        var brood = plugin.brood();
        int removed = brood == null ? 0 : brood.clearBrood(t);
        temples.allSpawners(t, clear ? new de.lamamc.sift.core.temple.BroodSpawner.State(de.lamamc.sift.core.temple.BroodSpawner.Phase.EXHAUSTED,
                0, 0, Long.MAX_VALUE) : de.lamamc.sift.core.temple.BroodSpawner.State.FRESH);
        if (brood != null) {
            brood.refresh(t);
        }
        sender.sendRichMessage("<green>Brut-Spawner von " + t.id() + (clear ? " beendet (Altar frei)" : " zurückgesetzt") + ", " + removed
                + " Brut-Mobs entfernt.");
        return Command.SINGLE_SUCCESS;
    }

    private int validate(CommandSender sender, TempleRegistry temples, TempleRegistry.Temple t) {
        boolean started = temples.validate(t.id(), lines -> {
            sender.sendRichMessage("<gold>Begehbarkeit " + t.id() + ": " + lines.get(0));
            lines.subList(1, lines.size()).forEach(l -> sender.sendRichMessage("  " + l));
        });
        if (!started) {
            sender.sendRichMessage("<yellow>" + t.id() + " ist ein Bau von alpha.41a: /sift temple rebuild " + t.id() + ".");
            return 0;
        }
        sender.sendRichMessage("<gray>Prüfe " + t.id() + " (3 x 3 x 3 vom Tor aus, Zone 3 und Arena 4 hoch) …");
        return Command.SINGLE_SUCCESS;
    }

    /** Without an id: the temple the sender stands in, else the nearest built one. */
    private int validateAll(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        TempleRegistry temples = plugin.temples();
        if (temples == null) {
            sender.sendRichMessage("<red>Tempel sind aus (phase4.temples.enabled) oder die Sift ist nicht geladen.");
            return 0;
        }
        var all = temples.all();
        if (all.isEmpty()) {
            sender.sendRichMessage("<yellow>Noch kein Tempel gebaut.");
            return 0;
        }
        TempleRegistry.Temple pick = all.iterator().next();
        if (sender instanceof org.bukkit.entity.Entity e) {
            var at = e.getLocation();
            pick = all.stream().min(java.util.Comparator.comparingDouble(t -> Math.hypot(t.x() - at.getX(), t.z() - at.getZ()))).orElse(pick);
        }
        return validate(sender, temples, pick);
    }

    private int inspect(CommandSender sender, TempleRegistry temples, TempleRegistry.Temple t) {
        sender.sendRichMessage("<gold>Tempel " + t.id() + ":");
        temples.inspect(t.id()).forEach(line -> sender.sendRichMessage("<gray>  " + line));
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandSender sender, TempleRegistry temples, TempleRegistry.Temple t) {
        sender.sendRichMessage("<gray>Tempel <white>" + t.id() + "</white>: " + state(t.state()) + ", Mitte " + tp(t.x(), t.y(), t.z())
                + (t.defeatedAt() > 0 ? ", besiegt am " + java.time.Instant.ofEpochMilli(t.defeatedAt()) : ""));
        var e = temples.enchanterBlock(t);
        var a = temples.altarBlock(t);
        sender.sendRichMessage("<gray>  Altar " + tp(a.getX(), a.getY(), a.getZ()) + ", Sculk Enchanter " + tp(e.getX(), e.getY(), e.getZ())
                + (temples.lockReason(e) != null ? " (gesperrt bis zum Sieg)" : ""));
        return Command.SINGLE_SUCCESS;
    }

    private int reset(CommandSender sender, TempleRegistry temples, TempleRegistry.Temple t) {
        if (plugin.bosses() != null) {
            plugin.bosses().stop(t.id(), "boss.reset-by-admin");
        }
        temples.reset(t.id());
        sender.sendRichMessage("<green>Tempel <white>" + t.id() + "</white> schläft wieder (Wächter kann erneut besiegt werden).");
        return Command.SINGLE_SUCCESS;
    }

    private int defeat(CommandSender sender, TempleRegistry temples, TempleRegistry.Temple t) {
        if (plugin.bosses() != null) {
            plugin.bosses().stop(t.id(), "boss.defeat-by-admin");
        }
        temples.defeat(t.id());
        sender.sendRichMessage("<green>Tempel <white>" + t.id() + "</white> gilt als besiegt (Testhilfe, ohne Kampf und Beute).");
        return Command.SINGLE_SUCCESS;
    }

    private de.lamamc.sift.world.temple.TempleBosses bosses(CommandSender sender) {
        var bosses = plugin.bosses();
        if (bosses == null) {
            sender.sendRichMessage("<red>Der Tempel-Boss ist aus (phase4.bosses.great-echo-golem.enabled oder mobs.enabled).");
        }
        return bosses;
    }

    private int bossInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        var bosses = bosses(sender);
        if (bosses == null) {
            return 0;
        }
        if (bosses.battles().isEmpty()) {
            sender.sendRichMessage("<gray>Kein Bosskampf läuft.");
        }
        bosses.battles().forEach(b -> sender.sendRichMessage("<gray>" + b.describe()));
        return Command.SINGLE_SUCCESS;
    }

    private int bossInfo(CommandSender sender, TempleRegistry temples, TempleRegistry.Temple t) {
        var bosses = bosses(sender);
        if (bosses == null) {
            return 0;
        }
        var b = bosses.battle(t.id());
        sender.sendRichMessage("<gray>" + (b == null ? t.id() + ": kein Kampf, Tempel " + state(t.state()) : b.describe()));
        return Command.SINGLE_SUCCESS;
    }

    private int bossStart(CommandSender sender, TempleRegistry temples, TempleRegistry.Temple t, int players) {
        var bosses = bosses(sender);
        if (bosses == null) {
            return 0;
        }
        if (t.state() == TempleRegistry.State.DEFEATED) {
            sender.sendRichMessage("<red>Tempel " + t.id() + " ist besiegt. Erst <white>/sift temple reset " + t.id());
            return 0;
        }
        if (!temples.hasArena(t)) {
            sender.sendRichMessage("<red>Tempel " + t.id() + " ist zu alt für die Arena. Erst <white>/sift temple rebuild " + t.id());
            return 0;
        }
        if (bosses.battle(t.id()) != null) {
            sender.sendRichMessage("<red>In " + t.id() + " läuft schon ein Kampf.");
            return 0;
        }
        bosses.begin(t, null, players, players > 0);
        sender.sendRichMessage("<green>Bosskampf in <white>" + t.id() + "</white> beginnt" + (players > 0
                ? " (Testhilfe: Leben wie für " + players + " Spieler, kein Abbruch bei leerer Arena)." : "."));
        return Command.SINGLE_SUCCESS;
    }

    private int bossStop(CommandSender sender, TempleRegistry temples, TempleRegistry.Temple t) {
        var bosses = bosses(sender);
        if (bosses == null) {
            return 0;
        }
        boolean stopped = bosses.stop(t.id(), "boss.stopped-by-admin");
        sender.sendRichMessage(stopped ? "<green>Kampf in " + t.id() + " abgebrochen; der Tempel schläft wieder." : "<gray>Kein Kampf in " + t.id() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private int bossDamage(CommandSender sender, TempleRegistry.Temple t, double amount, boolean ranged) {
        var bosses = bosses(sender);
        if (bosses == null) {
            return 0;
        }
        var b = bosses.battle(t.id());
        var r = b == null ? null : b.adminHit(ranged ? de.lamamc.sift.core.boss.GolemBoss.Hit.RANGED
                : de.lamamc.sift.core.boss.GolemBoss.Hit.MELEE, amount);
        if (r == null) {
            sender.sendRichMessage("<red>In " + t.id() + " kämpft gerade kein gelandeter Golem.");
            return 0;
        }
        sender.sendRichMessage(String.format(Locale.ROOT, "<gray>Treffer: %.1f abgezogen%s%s, Phase %s → %s. %s", r.dealt(),
                r.plateBroken() ? ", Platte gebrochen" : "", r.bounced() ? ", abgeprallt" : "", r.from(), r.to(), b.describe()));
        return Command.SINGLE_SUCCESS;
    }

    private int resetAll(CommandContext<CommandSourceStack> ctx, boolean confirmed) {
        CommandSender sender = ctx.getSource().getSender();
        TempleRegistry temples = plugin.temples();
        if (temples == null) {
            sender.sendRichMessage("<red>Tempel sind aus (phase4.temples.enabled) oder die Sift ist nicht geladen.");
            return 0;
        }
        if (!confirmed) {
            sender.sendRichMessage("<yellow>Setzt alle " + temples.all().size() + " Tempel auf „schläft“ zurück. Bestätigen: "
                    + "<white>/sift temple reset-all confirm");
            return Command.SINGLE_SUCCESS;
        }
        temples.all().forEach(t -> temples.reset(t.id()));
        sender.sendRichMessage("<green>Alle Tempel schlafen wieder.");
        return Command.SINGLE_SUCCESS;
    }
}
