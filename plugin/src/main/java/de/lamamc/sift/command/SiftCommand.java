package de.lamamc.sift.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.lamamc.sift.SiftPlugin;
import de.lamamc.sift.world.DebugPopulator;
import de.lamamc.sift.world.SiftWorlds;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** {@code /sift}: admin commands. Only travel and diagnostics so far. */
public final class SiftCommand {

    private static final String PERMISSION = "thesift.admin";
    /** Blocks that stand in for mod blocks (see pack-src/block-mapping.json) plus the debug marker. */
    private static final Set<Material> SCANNED = Set.of(Material.MUSHROOM_STEM, Material.BROWN_MUSHROOM_BLOCK,
            Material.RED_MUSHROOM_BLOCK, Material.TRIPWIRE, Material.CHORUS_PLANT, Material.GLOWSTONE,
            Material.WATER, Material.LAVA);

    private final SiftPlugin plugin;

    public SiftCommand(SiftPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        LiteralCommandNode<CommandSourceStack> root = Commands.literal("sift")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("tp").executes(this::teleport))
                .then(Commands.literal("debug")
                        .then(Commands.literal("populator").executes(this::populatorStats))
                        .then(Commands.literal("scan")
                                .executes(ctx -> scan(ctx, 2, 0, 0))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 8))
                                        .executes(ctx -> scan(ctx, IntegerArgumentType.getInteger(ctx, "radius"), 0, 0))
                                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                        .executes(ctx -> scan(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "radius"),
                                                                IntegerArgumentType.getInteger(ctx, "chunkX"),
                                                                IntegerArgumentType.getInteger(ctx, "chunkZ"))))))))
                .build();
        commands.register(root, "The Sift: Admin-Befehle");
    }

    private int teleport(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player player)) {
            ctx.getSource().getSender().sendRichMessage("<red>Nur für Spieler.");
            return 0;
        }
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (sift == null) {
            player.sendRichMessage("<red>Die Sift-Welt ist nicht geladen.");
            return 0;
        }
        World target = SiftWorlds.isSift(player.getWorld()) ? Bukkit.getWorlds().getFirst() : sift;
        player.teleportAsync(target.getSpawnLocation()).thenAccept(ok ->
                player.sendRichMessage(ok ? "<gray>Teleportiert nach <white>" + target.getKey() : "<red>Teleport fehlgeschlagen."));
        return Command.SINGLE_SUCCESS;
    }

    private int populatorStats(CommandContext<CommandSourceStack> ctx) {
        DebugPopulator populator = plugin.debugPopulator();
        if (populator == null) {
            ctx.getSource().getSender().sendRichMessage("<yellow>Test-Populator ist aus (debug.populator-marker).");
            return 0;
        }
        ctx.getSource().getSender().sendRichMessage("<gray>Test-Populator: <white>" + populator.chunks()
                + "</white> Chunks, <white>" + populator.markers() + "</white> Marker");
        return Command.SINGLE_SUCCESS;
    }

    /** Generates/loads the chunks around a chunk of the Sift and counts the stand-in block states. */
    private int scan(CommandContext<CommandSourceStack> ctx, int radius, int centerX, int centerZ) {
        var sender = ctx.getSource().getSender();
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (sift == null) {
            sender.sendRichMessage("<red>Die Sift-Welt ist nicht geladen.");
            return 0;
        }
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                futures.add(sift.getChunkAtAsync(x, z));
            }
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    List<ChunkSnapshot> snapshots = futures.stream().map(f -> f.join().getChunkSnapshot()).toList();
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        Map<String, Integer> counts = count(snapshots, sift.getMinHeight(), sift.getMaxHeight());
                        Bukkit.getScheduler().runTask(plugin, () -> report(sender, snapshots.size(), counts));
                    });
                }));
        sender.sendRichMessage("<gray>Scanne " + futures.size() + " Chunks der Sift ...");
        return Command.SINGLE_SUCCESS;
    }

    private static Map<String, Integer> count(List<ChunkSnapshot> snapshots, int minY, int maxY) {
        Map<String, Integer> counts = new HashMap<>();
        for (ChunkSnapshot snapshot : snapshots) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minY; y < maxY; y++) {
                        var data = snapshot.getBlockData(x, y, z);
                        if (SCANNED.contains(data.getMaterial())) {
                            counts.merge(data.getAsString(), 1, Integer::sum);
                        }
                    }
                }
            }
        }
        return counts;
    }

    private static void report(org.bukkit.command.CommandSender sender, int chunks, Map<String, Integer> counts) {
        sender.sendRichMessage("<gray>Scan von <white>" + chunks + "</white> Chunks, Ersatz-Blockzustände:");
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(30)
                .forEach(e -> sender.sendPlainMessage("  " + e.getValue() + "  " + e.getKey()));
    }
}
