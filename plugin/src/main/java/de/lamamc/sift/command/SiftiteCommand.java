package de.lamamc.sift.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.world.siftite.SiftiteKeeper;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** /sift siftite list|restore <player> (alpha.23): Siftite kept on death that has not come back yet. */
final class SiftiteCommand {

    private final SiftiteKeeper keeper;

    SiftiteCommand(SiftiteKeeper keeper) {
        this.keeper = keeper;
    }

    LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("siftite")
                .then(Commands.literal("list").then(playerArgument().executes(this::list)))
                .then(Commands.literal("restore").then(playerArgument().executes(this::restore)));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> playerArgument() {
        return Commands.argument("player", StringArgumentType.word()).suggests((ctx, builder) -> {
            Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
            return builder.buildFuture();
        });
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "player");
        Player online = Bukkit.getPlayerExact(name);
        List<byte[]> pending;
        if (online != null) {
            pending = SiftiteKeeper.pending(online.getPersistentDataContainer());
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
            if (offline == null || !offline.hasPlayedBefore()) {
                sender.sendMessage(Component.text("Spieler " + name + " ist unbekannt.", NamedTextColor.RED));
                return 0;
            }
            pending = SiftiteKeeper.pending(offline.getPersistentDataContainer());
        }
        sender.sendMessage(Component.text(name + ": " + pending.size() + " Siftit-Stapel warten auf die Rückgabe"
                + (online == null ? " (offline, kommen beim nächsten Login)" : ""), NamedTextColor.GOLD));
        for (byte[] entry : pending) {
            ItemStack stack = ItemStack.deserializeBytes(entry);
            String id = SiftItems.idOf(stack);
            sender.sendMessage(Component.text("- " + stack.getAmount() + " × " + (id == null ? stack.getType().getKey() : id), NamedTextColor.GRAY));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int restore(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "player");
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            sender.sendMessage(Component.text(name + " ist nicht online; die Gegenstände kommen beim nächsten Login von selbst.", NamedTextColor.RED));
            return 0;
        }
        int count = keeper.restore(player);
        sender.sendMessage(Component.text(count + " Siftit-Stapel direkt an " + name + " gegeben (Rest vor die Füße).", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }
}
