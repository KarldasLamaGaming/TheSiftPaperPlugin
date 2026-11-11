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

/** /sift temple list|info|tp|sites|reset|reset-all|defeat (phase 4, alpha.41a). */
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
            sender.sendRichMessage("<gray>  <white>" + t.id() + "</white> " + state(t.state()) + " " + tp(t.x(), t.y() + 1, t.z() + 19));
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
        long built = stats.built.get();
        sender.sendRichMessage(String.format(Locale.ROOT, "<gold>Tempel-Plätze (Ring um 0/0):</gold> <gray>gebaut seit dem Start %d, "
                        + "Zeit je Tempel im Mittel %.1f ms, höchstens %.1f ms", built, built == 0 ? 0 : stats.nanos.get() / 1e6 / built,
                stats.maxNanos.get() / 1e6));
        for (TempleSites.Site site : temples.builder().sites()) {
            boolean generated = sift.isChunkGenerated(site.x() >> 4, site.z() >> 4);
            var temple = temples.get(site.id());
            int distance = (int) Math.sqrt((double) site.x() * site.x() + (double) site.z() * site.z());
            sender.sendRichMessage("<gray>  <white>" + site.id() + "</white> bei " + site.x() + " " + site.z() + " (" + distance + " Blöcke): "
                    + (temple.isPresent() ? "gebaut, " + state(temple.get().state())
                    : generated ? "<red>Chunk schon generiert, aber kein Tempel (vor alpha.41a erkundet)</red>" : "noch nicht generiert")
                    + " " + tp(site.x(), temple.map(t -> t.y() + 1).orElse(200), site.z() + 19));
        }
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
