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

/** /sift temple list|info|sites|reset|reset-all|rebuild|inspect|defeat (phase 4, alpha.41a, rebuild/inspect alpha.41.1). */
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
                // test aid until the boss exists (alpha.42): the temple counts as defeated
                .then(Commands.literal("defeat").then(id().executes(ctx -> withTemple(ctx, this::defeat))));
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
        var plan = temples.spawners();
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
        temples.reset(t.id());
        sender.sendRichMessage("<green>Tempel <white>" + t.id() + "</white> schläft wieder (Wächter kann erneut besiegt werden).");
        return Command.SINGLE_SUCCESS;
    }

    private int defeat(CommandSender sender, TempleRegistry temples, TempleRegistry.Temple t) {
        temples.defeat(t.id());
        sender.sendRichMessage("<green>Tempel <white>" + t.id() + "</white> gilt als besiegt (Testhilfe bis zum Boss in alpha.42).");
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
