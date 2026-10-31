package de.lamamc.sift.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.lamamc.sift.Messages;
import de.lamamc.sift.mob.MobKind;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.MobSpawner;
import de.lamamc.sift.mob.SiftMob;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Map;
import java.util.TreeMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;

/** /sift spawn &lt;mob&gt; [count] and /sift debug mobs. */
final class MobCommand {

    private final MobManager mobs;
    private final MobSpawner spawner;
    private final Messages messages;

    MobCommand(MobManager mobs, MobSpawner spawner, Messages messages) {
        this.mobs = mobs;
        this.spawner = spawner;
        this.messages = messages;
    }

    LiteralArgumentBuilder<CommandSourceStack> spawnNode() {
        var mob = Commands.argument("mob", StringArgumentType.word()).suggests((ctx, builder) -> {
            mobs.kinds().keySet().forEach(builder::suggest);
            return builder.buildFuture();
        });
        return Commands.literal("spawn")
                .then(mob.executes(ctx -> spawn(ctx, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 32))
                                .executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "count")))));
    }

    LiteralArgumentBuilder<CommandSourceStack> debugNode() {
        return Commands.literal("mobs").executes(this::debug)
                .then(Commands.literal("walk")
                        .then(Commands.argument("pos", io.papermc.paper.command.brigadier.argument.ArgumentTypes.blockPosition())
                                .executes(this::walk)));
    }

    /** Sends the nearest Sift mob (32 blocks) to a block (pathfinding test without players). */
    private int walk(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var pos = ctx.getArgument("pos", io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver.class)
                .resolve(ctx.getSource());
        SiftMob nearest = nearest(ctx.getSource().getLocation());
        if (nearest == null) {
            ctx.getSource().getSender().sendMessage(Component.text("Kein Sift-Mob in 32 Blöcken."));
            return 0;
        }
        boolean ok = nearest.base().getPathfinder().moveTo(pos.toLocation(nearest.base().getWorld()), 1.0);
        ctx.getSource().getSender().sendMessage(Component.text("Weg zu " + pos.blockX() + " " + pos.blockY() + " " + pos.blockZ()
                + (ok ? " gefunden" : " nicht gefunden")));
        return ok ? 1 : 0;
    }

    private SiftMob nearest(Location at) {
        SiftMob nearest = null;
        double best = 32 * 32;
        for (SiftMob mob : mobs.mobs()) {
            if (mob.base().getWorld().equals(at.getWorld()) && mob.base().getLocation().distanceSquared(at) < best) {
                best = mob.base().getLocation().distanceSquared(at);
                nearest = mob;
            }
        }
        return nearest;
    }

    private String name(String id) {
        return messages.has("mobs.names." + id) ? messages.raw("mobs.names." + id) : id;
    }

    private int spawn(CommandContext<CommandSourceStack> ctx, int count) {
        CommandSender sender = ctx.getSource().getSender();
        String id = StringArgumentType.getString(ctx, "mob");
        MobKind kind = mobs.kind(id);
        if (kind == null) {
            sender.sendMessage(messages.get("mobs.unknown", "mob", id));
            return 0;
        }
        if (!kind.enabled()) {
            sender.sendMessage(messages.get("mobs.disabled", "mob", name(id), "id", id));
            return 0;
        }
        Location at = ctx.getSource().getLocation();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            if (mobs.spawn(kind, at, false, null) != null) {
                spawned++;
            }
        }
        if (spawned == 0) {
            sender.sendMessage(messages.get("mobs.spawn-failed", "mob", name(id),
                    "reason", messages.raw("mobs.limit-world").replace("<limit>", String.valueOf(mobs.worldLimit()))));
            return 0;
        }
        sender.sendMessage(messages.get("mobs.spawned", "count", String.valueOf(spawned), "mob", name(id)));
        return spawned;
    }

    private int debug(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Map<String, Integer> perWorld = new TreeMap<>();
        Map<String, Integer> perKind = new TreeMap<>();
        for (SiftMob mob : mobs.mobs()) {
            perWorld.merge(mob.base().getWorld().getName(), 1, Integer::sum);
            perKind.merge(mob.kind().id(), 1, Integer::sum);
        }
        MobManager.Stats stats = mobs.stats();
        sender.sendMessage(Component.text("Sift-Mobs: " + mobs.mobs().size() + " (Grenze " + mobs.worldLimit() + " je Welt, "
                + mobs.perPlayer() + " je Spieler im Umkreis " + mobs.playerRadius() + ")"));
        sender.sendMessage(Component.text("  je Welt: " + perWorld + ", je Art: " + perKind));
        sender.sendMessage(Component.text("  Displays erzeugt: " + stats.spawnedDisplays + ", Updates: " + stats.updates
                + ", Transformationen: " + stats.transformations + String.format(" (%.1f/s)", stats.transformationsPerSecond)
                + ", Fehler: " + stats.errors));
        sender.sendMessage(Component.text("  natürlich erschienen: " + spawner.spawned()));
        for (MobKind kind : mobs.kinds().values()) {
            sender.sendMessage(Component.text("  " + kind.id() + ": " + (kind.enabled() ? "an" : "aus") + ", Basis "
                    + kind.baseClass().getSimpleName() + ", " + kind.assets().rig().cubes().size() + " Würfel, Spawn "
                    + (kind.spawnRules() == null ? "aus" : "an")));
        }
        SiftMob nearest = nearest(ctx.getSource().getLocation());
        if (nearest != null) {
            Mob base = nearest.base();
            Entity seat = base.getPassengers().isEmpty() ? null : base.getPassengers().getFirst();
            sender.sendMessage(Component.text(String.format(
                    "  nächster: %s bei %.1f %.1f %.1f, Hitbox %.2f × %.2f, HP %.1f, Displays %d, Sitzhöhe %s, Animation %s / %s",
                    nearest.kind().id(), base.getX(), base.getY(), base.getZ(), base.getWidth(), base.getHeight(), base.getHealth(),
                    base.getPassengers().size(), seat == null ? "-" : String.format("%.3f", seat.getY() - base.getY()),
                    nearest.animations().current(nearest.kind().layers()[0]),
                    nearest.kind().layers().length > 1 ? nearest.animations().current(nearest.kind().layers()[1]) : "-")));
        }
        return 1;
    }
}
