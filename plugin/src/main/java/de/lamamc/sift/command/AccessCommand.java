package de.lamamc.sift.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.lamamc.sift.core.access.SiftAccess;
import de.lamamc.sift.world.access.SiftGate;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.File;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * /sift access status|open|close|reset|set dragon-kill|reload, plus the shortcuts /sift open and /sift close
 * (alpha.20, time lock of the Sift).
 */
final class AccessCommand {

    private final Plugin plugin;
    private final SiftGate gate;

    AccessCommand(Plugin plugin, SiftGate gate) {
        this.plugin = plugin;
        this.gate = gate;
    }

    LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("access")
                .executes(this::status)
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("open").executes(ctx -> override(ctx, SiftAccess.Override.OPEN)))
                .then(Commands.literal("close").executes(ctx -> override(ctx, SiftAccess.Override.CLOSED)))
                .then(Commands.literal("reset").executes(ctx -> override(ctx, SiftAccess.Override.NONE)))
                .then(Commands.literal("set").then(Commands.literal("dragon-kill")
                        .then(Commands.argument("when", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("now");
                                    builder.suggest("clear");
                                    builder.suggest(java.time.LocalDate.now(gate.zone()).toString());
                                    return builder.buildFuture();
                                })
                                .executes(this::setDragonKill))))
                .then(Commands.literal("reload").executes(this::reload));
    }

    LiteralArgumentBuilder<CommandSourceStack> openShortcut() {
        return Commands.literal("open").executes(ctx -> override(ctx, SiftAccess.Override.OPEN));
    }

    LiteralArgumentBuilder<CommandSourceStack> closeShortcut() {
        return Commands.literal("close").executes(ctx -> override(ctx, SiftAccess.Override.CLOSED));
    }

    private static void line(CommandSender sender, String label, String value) {
        sender.sendMessage(Component.text(label + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE)));
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        SiftAccess.State state = gate.state();
        long now = System.currentTimeMillis();
        sender.sendMessage(Component.text("Die Sift ist " + (state.open() ? "offen" : "geschlossen"),
                state.open() ? NamedTextColor.GREEN : NamedTextColor.RED));
        line(sender, "Grund", state.reason());
        line(sender, "Modus", gate.settings().mode().key());
        SiftAccess.Override override = gate.override();
        line(sender, "Befehl", switch (override) {
            case OPEN -> "geöffnet (/sift access reset kehrt zum Modus zurück)";
            case CLOSED -> "geschlossen (/sift access reset kehrt zum Modus zurück)";
            case NONE -> "keiner, es gilt der Modus";
        });
        if (state.opensAt() != null) {
            line(sender, "Öffnet", SiftAccess.format(state.opensAt(), gate.zone()) + " (in " + SiftAccess.remaining(state.opensAt() - now) + ")");
        }
        switch (gate.settings().mode()) {
            case AFTER_DRAGON -> {
                line(sender, "Wartezeit", SiftAccess.remaining(gate.settings().delayMillis()));
                Long kill = gate.dragonKill();
                String world = gate.dragonWorld();
                line(sender, "Erster Drachenkill", kill == null ? "noch keiner bekannt"
                        : SiftAccess.format(kill, gate.zone()) + (world == null ? ""
                        : SiftGate.BY_COMMAND.equals(world) ? " (per Befehl gesetzt)" : " in " + world));
                line(sender, "Zählende Welten", gate.dragonWorlds().isEmpty() ? "jede End-Welt" : String.join(", ", gate.dragonWorlds()));
                if (kill == null && gate.dragonKilledBefore()) {
                    sender.sendMessage(Component.text("Der Drache wurde schon vor dem Plugin besiegt, der Zeitpunkt ist unbekannt. "
                            + "Setze ihn mit /sift access set dragon-kill <Datum|now>.", NamedTextColor.GOLD));
                }
            }
            case FIXED_DATE -> line(sender, "Datum", gate.settings().date() == null ? "fehlt oder ungültig (access.date)"
                    : SiftAccess.format(gate.settings().date(), gate.zone()));
            default -> {
            }
        }
        if (gate.countdown() >= 0) {
            line(sender, "Schließt in", gate.countdown() + " s");
        }
        line(sender, "Zeitzone", gate.zone().getId());
        return Command.SINGLE_SUCCESS;
    }

    private int override(CommandContext<CommandSourceStack> ctx, SiftAccess.Override override) {
        gate.setOverride(override);
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(Component.text(switch (override) {
            case OPEN -> "Die Sift ist per Befehl geöffnet (bleibt nach Neustart).";
            case CLOSED -> "Die Sift ist per Befehl geschlossen (bleibt nach Neustart).";
            case NONE -> "Befehlszustand gelöscht, es gilt wieder der Modus " + gate.settings().mode().key() + ".";
        }, NamedTextColor.GREEN));
        return status(ctx);
    }

    private int setDragonKill(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String text = StringArgumentType.getString(ctx, "when").trim();
        if (text.equalsIgnoreCase("clear")) {
            gate.setDragonKill(null, null);
            sender.sendMessage(Component.text("Drachenkill gelöscht.", NamedTextColor.GREEN));
            return status(ctx);
        }
        Long when = text.equalsIgnoreCase("now") ? Long.valueOf(System.currentTimeMillis()) : SiftAccess.parseDate(text, gate.zone());
        if (when == null) {
            sender.sendMessage(Component.text("Kein gültiges Datum: " + text + " (z. B. 2026-11-03, 2026-11-03T18:00 oder now)", NamedTextColor.RED));
            return 0;
        }
        gate.setDragonKill(when, SiftGate.BY_COMMAND);
        sender.sendMessage(Component.text("Drachenkill gesetzt: " + SiftAccess.format(when, gate.zone()), NamedTextColor.GREEN));
        return status(ctx);
    }

    /** Reads the access section of config.yml off the main thread and applies it on the main thread. */
    private int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        File file = new File(plugin.getDataFolder(), "config.yml");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            Bukkit.getScheduler().runTask(plugin, () -> {
                ConfigurationSection section = config.getConfigurationSection("access");
                gate.apply(section);
                sender.sendMessage(Component.text("access-Abschnitt neu geladen.", NamedTextColor.GREEN));
                status(ctx);
            });
        });
        return Command.SINGLE_SUCCESS;
    }
}
