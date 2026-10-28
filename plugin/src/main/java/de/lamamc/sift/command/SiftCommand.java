package de.lamamc.sift.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.lamamc.sift.SiftPlugin;
import de.lamamc.sift.block.BlockSettings;
import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.block.HeldTools;
import de.lamamc.sift.core.BlockProfile;
import de.lamamc.sift.core.BreakTimeCalculator;
import de.lamamc.sift.core.DropRoller;
import de.lamamc.sift.core.HeldTool;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.world.DebugPopulator;
import de.lamamc.sift.world.SiftWorlds;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.Random;
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
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /sift}: admin commands. */
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
                .then(Commands.literal("give")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            plugin.items().ids().stream()
                                                    .filter(id -> id.startsWith(builder.getRemainingLowerCase()))
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> give(ctx, 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 6400))
                                                .executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))))
                .then(Commands.literal("pack")
                        .then(Commands.literal("info").executes(ctx -> {
                            ctx.getSource().getSender().sendPlainMessage("Resource Pack: " + plugin.resourcePack().describe());
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("reload").executes(ctx -> {
                            plugin.reloadResourcePack();
                            ctx.getSource().getSender().sendRichMessage(
                                    "<gray>Resource-Pack-Einstellungen neu geladen; die SHA-1 wird neu ermittelt.");
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("send")
                                .then(Commands.argument("player", ArgumentTypes.player()).executes(ctx -> {
                                    Player target = ctx.getArgument("player", PlayerSelectorArgumentResolver.class)
                                            .resolve(ctx.getSource()).getFirst();
                                    boolean sent = plugin.resourcePack().send(target);
                                    ctx.getSource().getSender().sendRichMessage(sent
                                            ? "<gray>Resource Pack an " + target.getName() + " gesendet."
                                            : "<red>Das Resource Pack ist abgeschaltet.");
                                    return Command.SINGLE_SUCCESS;
                                }))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("item")
                                .then(Commands.argument("item", StringArgumentType.word()).executes(this::debugItem)))
                        .then(Commands.literal("break")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .then(Commands.argument("tool", StringArgumentType.word())
                                                                .executes(this::debugBreak))))))
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

    private int give(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        Player target = ctx.getArgument("player", PlayerSelectorArgumentResolver.class)
                .resolve(ctx.getSource()).getFirst();
        String id = StringArgumentType.getString(ctx, "item");
        ItemStack stack = plugin.items().create(id, amount);
        if (stack == null) {
            ctx.getSource().getSender().sendRichMessage("<red>Unbekanntes Sift-Item: <white>" + id);
            return 0;
        }
        target.getInventory().addItem(stack).values()
                .forEach(rest -> target.getWorld().dropItem(target.getLocation(), rest));
        ctx.getSource().getSender().sendRichMessage("<gray>" + amount + "x <white>" + id + "</white> an "
                + target.getName() + " gegeben.");
        return Command.SINGLE_SUCCESS;
    }

    /** Prints the components of a Sift item (for tests without a client). */
    private int debugItem(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "item");
        ItemStack stack = plugin.items().create(id, 1);
        var sender = ctx.getSource().getSender();
        if (stack == null) {
            sender.sendRichMessage("<red>Unbekanntes Sift-Item: <white>" + id);
            return 0;
        }
        sender.sendPlainMessage("Item " + id + ": base " + stack.getType().getKey()
                + ", item_model " + stack.getData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL)
                + ", item_name " + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(stack.getData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_NAME))
                + ", id " + SiftItems.idOf(stack));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Shows how a custom block in the Sift would behave when mined with a tool (vanilla item id),
     * without a client: break time, speed factor and one roll of the drops.
     */
    private int debugBreak(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (sift == null) {
            sender.sendRichMessage("<red>Die Sift-Welt ist nicht geladen.");
            return 0;
        }
        Block block = sift.getBlockAt(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"),
                IntegerArgumentType.getInteger(ctx, "z"));
        CustomBlock custom = plugin.blocks().at(block);
        if (custom == null) {
            sender.sendPlainMessage("Kein Sift-Block: " + block.getBlockData().getAsString());
            return 0;
        }
        Material toolMaterial = Material.matchMaterial(StringArgumentType.getString(ctx, "tool"));
        ItemStack tool = toolMaterial == null || toolMaterial == Material.AIR ? null : ItemStack.of(toolMaterial);
        HeldTool held = HeldTools.of(tool);
        BlockSettings settings = plugin.blockSettings();
        BlockProfile profile = settings.profile(custom.id());
        Material standIn = custom.data().getMaterial();
        double factor = BreakTimeCalculator.speedFactor(profile, held, 0, CustomBlockRegistry.standInTool(standIn),
                CustomBlockRegistry.standInHardness(standIn));
        double standInProgress = BreakTimeCalculator.progress(
                BreakTimeCalculator.toolSpeed(held, CustomBlockRegistry.standInTool(standIn), 0),
                CustomBlockRegistry.standInHardness(standIn), true);
        int ticks = BreakTimeCalculator.ticks(standInProgress * factor);
        boolean harvested = profile.canHarvest(held);
        var drops = DropRoller.roll(profile.drops(), "the_sift:" + custom.id(), harvested, false, 0, held.type(),
                new Random());
        sender.sendPlainMessage("Block " + custom.id() + custom.properties() + " (" + custom.data().getAsString() + ")");
        sender.sendPlainMessage("  Werkzeug " + held + ", Faktor " + String.format(java.util.Locale.ROOT, "%.4f", factor)
                + ", Abbauzeit " + (ticks == Integer.MAX_VALUE ? "unzerstörbar" : ticks + " Ticks")
                + ", erntbar " + harvested);
        sender.sendPlainMessage("  Drops " + drops + ", XP " + DropRoller.experience(profile, harvested, false, new Random()));
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
