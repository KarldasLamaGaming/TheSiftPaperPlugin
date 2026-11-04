package de.lamamc.sift.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.lamamc.sift.world.rift.RiftManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Comparator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /sift rift open|close|list (alpha.22). */
final class RiftCommand {

    private final RiftManager rifts;

    RiftCommand(RiftManager rifts) {
        this.rifts = rifts;
    }

    LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("rift")
                .then(Commands.literal("open").executes(this::open)
                        .then(Commands.argument("pos", io.papermc.paper.command.brigadier.argument.ArgumentTypes.blockPosition())
                                .executes(this::openAt)))
                .then(Commands.literal("close").executes(this::close))
                .then(Commands.literal("list").executes(this::list));
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(ctx.getSource().getExecutor() instanceof Player player)) {
            sender.sendMessage(Component.text("Nur für Spieler (Riss öffnet vor dir).", NamedTextColor.RED));
            return 0;
        }
        RiftManager.Rift rift = rifts.openFor(player, player.getLocation().getBlock().getRelative(player.getFacing(), 5));
        if (rift == null) {
            sender.sendMessage(Component.text("Kein Platz für den Riss in der Nähe (9 × 5 Blöcke Luft, Suchradius rifts.admin.search-radius).", NamedTextColor.RED));
            return 0;
        }
        sender.sendMessage(Component.text("Riss #" + rift.id() + " geöffnet, führt " + (rift.intoSift() ? "in die Sift." : "hinaus."),
                NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    /** At a block position (also from the console: /execute in <world> run sift rift open x y z). */
    private int openAt(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var pos = ctx.getArgument("pos", io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver.class)
                .resolve(ctx.getSource());
        Location at = ctx.getSource().getLocation();
        RiftManager.Rift rift = rifts.openNear(at.getWorld(), pos.blockX(), pos.blockY(), pos.blockZ(), true);
        if (rift == null) {
            ctx.getSource().getSender().sendMessage(Component.text("Kein Platz für den Riss in der Nähe (9 × 5 Blöcke Luft, Suchradius rifts.admin.search-radius).", NamedTextColor.RED));
            return 0;
        }
        ctx.getSource().getSender().sendMessage(Component.text("Riss #" + rift.id() + " geöffnet, führt "
                + (rift.intoSift() ? "in die Sift." : "hinaus."), NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int close(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Location at = ctx.getSource().getLocation();
        RiftManager.Rift nearest = rifts.rifts().stream()
                .filter(r -> !r.closing() && r.world().equals(at.getWorld()) && r.location().distanceSquared(at) <= 32 * 32)
                .min(Comparator.comparingDouble(r -> r.location().distanceSquared(at))).orElse(null);
        if (nearest == null) {
            sender.sendMessage(Component.text("Kein offener Riss in 32 Blöcken.", NamedTextColor.RED));
            return 0;
        }
        rifts.close(nearest);
        sender.sendMessage(Component.text("Riss #" + nearest.id() + " schließt.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        var all = rifts.rifts();
        sender.sendMessage(Component.text(all.size() + " Risse offen", NamedTextColor.GOLD));
        long[] n = rifts.naturalStats();
        sender.sendMessage(Component.text("Natürliche Platzsuche seit Start: " + n[0] + " Suchen, " + n[1] + " geöffnet, "
                + n[2] + " ohne Platz aufgegeben; " + n[3] + " Versuche in ungeladenen Chunks", NamedTextColor.GRAY));
        for (RiftManager.Rift rift : all) {
            Location l = rift.location();
            String where = l.getWorld().getKey() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
            sender.sendMessage(Component.text("#" + rift.id() + " " + where + (rift.intoSift() ? " → Sift" : " → hinaus")
                            + (rift.closing() ? " (schließt)" : ""), NamedTextColor.GRAY)
                    .clickEvent(ClickEvent.runCommand("/execute in " + l.getWorld().getKey() + " run tp @s "
                            + l.getBlockX() + " " + (l.getBlockY() + 6) + " " + l.getBlockZ())));
        }
        return Command.SINGLE_SUCCESS;
    }
}
