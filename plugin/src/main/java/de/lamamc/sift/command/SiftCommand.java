package de.lamamc.sift.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.lamamc.sift.SiftPlugin;
import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.block.HeldTools;
import de.lamamc.sift.core.BlockProfile;
import de.lamamc.sift.core.BreakTimeCalculator;
import de.lamamc.sift.core.BlockMining;
import de.lamamc.sift.core.HeldTool;
import de.lamamc.sift.item.ItemCategory;
import de.lamamc.sift.item.ItemSelfTest;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.menu.ItemMenu;
import de.lamamc.sift.world.SiftWorlds;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.Locale;
import java.util.Random;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /sift}: admin commands. */
public final class SiftCommand {

    /** All /sift commands; includes {@link #ITEMS_PERMISSION}. */
    public static final String PERMISSION = "thesift.admin";
    /** /sift items and /sift give only, for team members who just need the items. */
    public static final String ITEMS_PERMISSION = "thesift.admin.items";
    /** Blocks that stand in for mod blocks (see pack-src/block-mapping.json) plus the debug marker. */
    private static final Set<Material> SCANNED = Set.of(Material.MUSHROOM_STEM, Material.BROWN_MUSHROOM_BLOCK,
            Material.RED_MUSHROOM_BLOCK, Material.TRIPWIRE, Material.CHORUS_PLANT, Material.GLOWSTONE,
            Material.WATER, Material.LAVA, Material.SCULK, Material.SCULK_SENSOR, Material.SCULK_SHRIEKER,
            Material.SCULK_CATALYST, Material.SCULK_VEIN, Material.VINE, Material.CHEST);

    private final SiftPlugin plugin;

    public SiftCommand(SiftPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        LiteralCommandNode<CommandSourceStack> root = Commands.literal("sift")
                .requires(source -> admin(source) || items(source))
                .then(Commands.literal("tp").requires(SiftCommand::admin).executes(this::teleport))
                .then(Commands.literal("items").requires(SiftCommand::items).executes(this::openMenu))
                .then(Commands.literal("give").requires(SiftCommand::items)
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
                .then(Commands.literal("pack").requires(SiftCommand::admin)
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
                .then(new PortalCommand(plugin.portals(), plugin.consoles(), plugin.getConfig().getInt("portals.frame-radius", 10),
                        plugin::shrineSites)
                        .node().requires(SiftCommand::admin))
                .then(Commands.literal("debug").requires(SiftCommand::admin)
                        .then(Commands.literal("item")
                                .then(Commands.argument("item", StringArgumentType.word()).executes(this::debugItem)))
                        .then(breakNode())
                        .then(Commands.literal("menu").executes(this::debugMenu))
                        .then(Commands.literal("selftest").executes(this::debugSelfTest))
                        .then(Commands.literal("worldgen").executes(this::debugWorldgen))
                        .then(Commands.literal("landmarks")
                                .executes(ctx -> debugLandmarks(ctx, null, 1))
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> debugLandmarks(ctx, new int[] {
                                                        IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "z")}, 1))
                                                .then(Commands.argument("cells", IntegerArgumentType.integer(0, 5))
                                                        .executes(ctx -> debugLandmarks(ctx, new int[] {
                                                                IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "z")},
                                                                IntegerArgumentType.getInteger(ctx, "cells")))))))
                        .then(Commands.literal("portals").executes(this::debugPortals))
                        .then(Commands.literal("heights")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 40))
                                                        .then(Commands.argument("step", IntegerArgumentType.integer(1, 8))
                                                                .executes(this::debugHeights))))))
                        .then(Commands.literal("shape")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(this::debugShape)))))
                        .then(Commands.literal("chorus")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 16))
                                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                        .executes(this::debugChorus)))))
                        .then(Commands.literal("find")
                                .then(Commands.argument("block", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            plugin.blocks().ids().stream()
                                                    .filter(id -> id.startsWith(builder.getRemainingLowerCase()))
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> find(ctx, 4))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 12))
                                                .executes(ctx -> find(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))))
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

    private static boolean admin(CommandSourceStack source) {
        return source.getSender().hasPermission(PERMISSION);
    }

    private static boolean items(CommandSourceStack source) {
        return source.getSender().hasPermission(ITEMS_PERMISSION);
    }

    private int openMenu(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player player)) {
            ctx.getSource().getSender().sendRichMessage("<red>Nur für Spieler.");
            return 0;
        }
        ItemMenu.open(player, plugin.items(), ItemCategory.BLOCKS, 0);
        return Command.SINGLE_SUCCESS;
    }

    /** Collision boxes of a block in the Sift, in pixels (16 = one block). */
    private int debugShape(CommandContext<CommandSourceStack> ctx) {
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        Block block = sift.getBlockAt(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"),
                IntegerArgumentType.getInteger(ctx, "z"));
        CustomBlock custom = plugin.blocks().at(block);
        List<String> boxes = new ArrayList<>();
        double volume = 0;
        for (var box : block.getCollisionShape().getBoundingBoxes()) {
            boxes.add(String.format(Locale.ROOT, "[%.0f..%.0f, %.0f..%.0f, %.0f..%.0f]", box.getMinX() * 16, box.getMaxX() * 16,
                    box.getMinY() * 16, box.getMaxY() * 16, box.getMinZ() * 16, box.getMaxZ() * 16));
            volume += box.getVolume();
        }
        ctx.getSource().getSender().sendPlainMessage((custom == null ? block.getType().getKey().toString() : custom.id())
                + ": Kollision " + boxes + String.format(Locale.ROOT, ", Volumen %.0f %%", volume * 100));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Generates/loads End chunks and counts chorus plants, and how many of them are in a state that stands
     * in for a Sift block (should be 0: those states do not occur naturally).
     */
    private int debugChorus(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        World end = Bukkit.getWorld(org.bukkit.NamespacedKey.minecraft("the_end"));
        if (end == null) {
            sender.sendRichMessage("<red>Das End ist nicht geladen.");
            return 0;
        }
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        int cx = IntegerArgumentType.getInteger(ctx, "chunkX");
        int cz = IntegerArgumentType.getInteger(ctx, "chunkZ");
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                futures.add(end.getChunkAtAsync(x, z));
            }
        }
        sender.sendRichMessage("<gray>Scanne " + futures.size() + " End-Chunks nach Chorus ...");
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    List<ChunkSnapshot> snapshots = futures.stream().map(f -> f.join().getChunkSnapshot()).toList();
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        Map<String, Integer> states = new HashMap<>();
                        for (ChunkSnapshot snapshot : snapshots) {
                            for (int x = 0; x < 16; x++) {
                                for (int z = 0; z < 16; z++) {
                                    for (int y = end.getMinHeight(); y < end.getMaxHeight(); y++) {
                                        if (snapshot.getBlockType(x, y, z) == Material.CHORUS_PLANT) {
                                            states.merge(snapshot.getBlockData(x, y, z).getAsString(), 1, Integer::sum);
                                        }
                                    }
                                }
                            }
                        }
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            int total = states.values().stream().mapToInt(Integer::intValue).sum();
                            int reserved = 0;
                            for (var e : states.entrySet()) {
                                if (plugin.blocks().of(Bukkit.createBlockData(e.getKey())) != null) {
                                    reserved += e.getValue();
                                }
                            }
                            sender.sendPlainMessage("Chorus-Pflanzen: " + total + " in " + states.size()
                                    + " Zuständen, davon in Sift-Zuständen: " + reserved);
                        });
                    });
                }));
        return Command.SINGLE_SUCCESS;
    }

    /** Builds every page of the item menu and lists what it shows (tests without a client). */
    private int debugMenu(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        for (ItemCategory category : ItemCategory.values()) {
            ItemMenu menu = ItemMenu.build(plugin.items(), category, 0);
            List<String> ids = new ArrayList<>();
            for (int slot = 0; slot < ItemMenu.ITEM_SLOTS; slot++) {
                String id = menu.itemAt(slot);
                if (id != null) {
                    ids.add(id);
                }
            }
            long filled = java.util.Arrays.stream(menu.getInventory().getContents()).filter(java.util.Objects::nonNull).count();
            sender.sendPlainMessage(category.title() + ": " + ids.size() + " Items, " + filled + " belegte Slots " + ids);
        }
        ItemMenu menu = ItemMenu.build(plugin.items(), ItemCategory.values()[0], 0);
        int tabsWithAttributes = 0;
        for (int i = 0; i < ItemCategory.values().length; i++) {
            var tooltip = menu.getInventory().getItem(ItemMenu.FIRST_TAB + i)
                    .getData(io.papermc.paper.datacomponent.DataComponentTypes.TOOLTIP_DISPLAY);
            if (tooltip == null || !tooltip.hiddenComponents().containsAll(ItemMenu.TAB_HIDDEN)) {
                tabsWithAttributes++;
            }
        }
        sender.sendPlainMessage("Menü-Symbole mit sichtbaren Attributen: " + tabsWithAttributes + " (sollte 0 sein)");
        return Command.SINGLE_SUCCESS;
    }

    /** /sift debug break <x> <y> <z> <tool> [none|silk|fortune1..3] [rolls] */
    private LiteralArgumentBuilder<CommandSourceStack> breakNode() {
        var rolls = Commands.argument("rolls", IntegerArgumentType.integer(1, 100_000))
                .executes(ctx -> debugBreak(ctx, StringArgumentType.getString(ctx, "enchant"),
                        IntegerArgumentType.getInteger(ctx, "rolls")));
        var enchant = Commands.argument("enchant", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    List.of("none", "silk", "fortune1", "fortune2", "fortune3").forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(ctx -> debugBreak(ctx, StringArgumentType.getString(ctx, "enchant"), 1))
                .then(rolls);
        var tool = Commands.argument("tool", StringArgumentType.word())
                .executes(ctx -> debugBreak(ctx, "none", 1))
                .then(enchant);
        return Commands.literal("break")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer()).then(tool))));
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
        Location anchor = target == sift ? de.lamamc.sift.world.MainPortalBuilder.anchor(sift) : null;
        Location destination = anchor != null ? anchor : target.getSpawnLocation();
        player.teleportAsync(destination).thenAccept(ok ->
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
    private int debugWorldgen(CommandContext<CommandSourceStack> ctx) {
        var populator = plugin.siftPopulator();
        var sender = ctx.getSource().getSender();
        if (populator == null) {
            sender.sendRichMessage("<red>Die Sift-Weltgenerierung ist nicht aktiv.");
            return 0;
        }
        var s = populator.stats();
        sender.sendRichMessage("<gray>Sift-Weltgenerierung seit dem Start: <white>" + s.chunks.get() + "</white> Chunks, <white>"
                + s.trees.get() + "</white> Weiden, <white>" + s.flowerPatches.get() + "</white> Blumenflecken, <white>"
                + s.plantPatches.get() + "</white> Pflanzenflecken, <white>" + s.sculkChunks.get() + "</white> Chunks mit Sculk, <white>"
                + s.abandonedPortals.get() + "</white> verlassene Portale (<white>" + populator.lootChests()
                + "</white> Truhen mit Beute), <white>" + s.errors.get() + "</white> Fehler");
        if (!s.portalRejects.isEmpty()) {
            sender.sendRichMessage("<gray>Verlassene Portale nicht gesetzt: <white>" + s.portalRejects);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Top block heights of the Sift around x/z as text (one character per column, '#' = highest band). */
    private int debugHeights(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (sift == null) {
            sender.sendRichMessage("<red>Die Sift-Welt ist nicht geladen.");
            return 0;
        }
        int cx = IntegerArgumentType.getInteger(ctx, "x");
        int cz = IntegerArgumentType.getInteger(ctx, "z");
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        int step = IntegerArgumentType.getInteger(ctx, "step");
        String digits = "0123456789abcdefghijklmnopqrstuvwxyz";
        int base = sift.getHighestBlockYAt(cx, cz, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
        sender.sendPlainMessage("Höhen um " + cx + " " + cz + " relativ zu y " + base + " (x nach rechts, z nach unten; '-' tiefer, '+' höher als 35):");
        for (int z = cz - radius * step; z <= cz + radius * step; z += step) {
            StringBuilder row = new StringBuilder();
            for (int x = cx - radius * step; x <= cx + radius * step; x += step) {
                int d = sift.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES) - base;
                row.append(x == cx && z == cz ? '@' : d < 0 ? '-' : d >= digits.length() ? '+' : digits.charAt(d));
            }
            sender.sendPlainMessage(row.toString());
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Candidate chunks of the abandoned portals in the cells around a position (default 0/0) with what
     * worldgen did there: built, rejected (why), not generated yet.
     */
    private int debugLandmarks(CommandContext<CommandSourceStack> ctx, int[] center, int cells) {
        var sender = ctx.getSource().getSender();
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (sift == null) {
            sender.sendRichMessage("<red>Die Sift-Welt ist nicht geladen.");
            return 0;
        }
        int cell = plugin.getConfig().getInt("worldgen.abandoned-portals.cell-chunks", 20);
        int centerX = center == null ? 0 : center[0];
        int centerZ = center == null ? 0 : center[1];
        long salt = de.lamamc.sift.core.worldgen.LandmarkPlacement.ABANDONED_PORTAL_SALT;
        int cellX = de.lamamc.sift.core.worldgen.LandmarkPlacement.cellOf(Math.floorDiv(centerX, 16), cell, salt);
        int cellZ = de.lamamc.sift.core.worldgen.LandmarkPlacement.cellOf(Math.floorDiv(centerZ, 16), cell, salt);
        sender.sendRichMessage("<gray>Kandidaten für verlassene Portale um " + centerX + " " + centerZ
                + " (Zellen von " + cell + " × " + cell + " Chunks):");
        for (int cx = cellX - cells; cx <= cellX + cells; cx++) {
            for (int cz = cellZ - cells; cz <= cellZ + cells; cz++) {
                var chunk = de.lamamc.sift.core.worldgen.LandmarkPlacement.candidateChunk(sift.getSeed(), cx, cz, cell, salt, 0);
                sender.sendRichMessage("  " + portalStatus(sift, chunk.x(), chunk.z()));
            }
        }
        sender.sendRichMessage("<gray>Sculk-Regionen (Mitte, Durchmesser) in den 512er-Zellen um " + centerX + " " + centerZ + ":");
        int sculkX = Math.floorDiv(centerX, de.lamamc.sift.core.worldgen.SculkRegions.CELL_SIZE);
        int sculkZ = Math.floorDiv(centerZ, de.lamamc.sift.core.worldgen.SculkRegions.CELL_SIZE);
        for (int cx = sculkX - 1; cx <= sculkX + 1; cx++) {
            for (int cz = sculkZ - 1; cz <= sculkZ + 1; cz++) {
                var region = de.lamamc.sift.core.worldgen.SculkRegions.create(sift.getSeed(), cx, cz);
                sender.sendPlainMessage("  sculk " + (int) region.centerX() + " " + (int) region.centerZ() + "  ("
                        + Math.round(region.majorRadius() * 2) + ")");
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Every abandoned portal candidate worldgen has seen: built ones with a teleport, rejected ones with the reason. */
    private int debugPortals(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (sift == null || plugin.siftPopulator() == null) {
            sender.sendRichMessage("<red>Die Sift-Weltgenerierung ist nicht aktiv.");
            return 0;
        }
        var entries = plugin.siftPopulator().log().all();
        long built = entries.stream().filter(e -> e.status() == de.lamamc.sift.world.gen.LandmarkLog.Status.BUILT).count();
        sender.sendRichMessage("<gray>Verlassene Portale: <white>" + built + "</white> gebaut, <white>" + (entries.size() - built)
                + "</white> abgelehnt (seit alpha.6 protokolliert; ältere Chunks fehlen hier)");
        for (var entry : entries) {
            sender.sendRichMessage("  " + portalStatus(sift, entry.chunkX(), entry.chunkZ()));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** One line about the candidate in this chunk, with a clickable teleport to the chest or the deciding block. */
    private String portalStatus(World sift, int chunkX, int chunkZ) {
        var entry = plugin.siftPopulator() == null ? null : plugin.siftPopulator().log().get(chunkX, chunkZ);
        String where = "Chunk " + chunkX + " " + chunkZ + " (" + (chunkX * 16 + 8) + " " + (chunkZ * 16 + 8) + ")";
        if (entry == null) {
            return sift.isChunkGenerated(chunkX, chunkZ)
                    ? "<white>" + where + "</white> <yellow>generiert vor alpha.6, kein Protokoll"
                    : "<white>" + where + "</white> <gray>noch nicht generiert";
        }
        String pos = entry.x() + " " + entry.y() + " " + entry.z();
        String tp = "<click:run_command:'/execute in the_sift:the_sift run tp @s " + entry.x() + " " + (entry.y() + 1) + " "
                + entry.z() + "'><hover:show_text:'Teleport'><aqua>[" + pos + "]</aqua></hover></click>";
        String reason = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(
                entry.reason() == null ? "" : entry.reason());
        return entry.status() == de.lamamc.sift.world.gen.LandmarkLog.Status.BUILT
                ? "<white>" + where + "</white> <green>gebaut</green> <gray>(" + reason + ", Truhe)</gray> " + tp
                : "<white>" + where + "</white> <red>abgelehnt:</red> <gray>" + reason + "</gray> " + tp;
    }

    private int debugSelfTest(CommandContext<CommandSourceStack> ctx) {
        World world = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (world == null) {
            world = Bukkit.getWorlds().getFirst();
        }
        List<String> lines = new ItemSelfTest(plugin.items(), plugin.recipes()).run(world);
        var sender = ctx.getSource().getSender();
        lines.forEach(line -> sender.sendRichMessage((line.startsWith("FEHLER") ? "<red>" : "<gray>")
                + net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(line)));
        return lines.getLast().equals("Selbsttest bestanden") ? Command.SINGLE_SUCCESS : 0;
    }

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
     * Shows how a custom block in the Sift would behave when mined with a tool (vanilla item id, optionally
     * enchanted), without a client: break time, speed factor and the drops of {@code rolls} breaks. The
     * enchantments are put on a real item and read back like the break listener does.
     */
    private int debugBreak(CommandContext<CommandSourceStack> ctx, String enchant, int rolls) {
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
        if (tool != null && enchant.equals("silk")) {
            tool.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        } else if (tool != null && enchant.startsWith("fortune")) {
            tool.addUnsafeEnchantment(Enchantment.FORTUNE, Integer.parseInt(enchant.substring("fortune".length())));
        }
        HeldTool held = HeldTools.of(tool);
        boolean silk = HeldTools.silkTouch(tool);
        int fortune = HeldTools.fortune(tool);
        BlockProfile profile = plugin.blockSettings().profile(custom.id());
        Material standIn = custom.data().getMaterial();
        double factor = BreakTimeCalculator.speedFactor(profile, held, 0, CustomBlockRegistry.standInTool(standIn),
                CustomBlockRegistry.standInHardness(standIn));
        double standInProgress = BreakTimeCalculator.progress(
                BreakTimeCalculator.toolSpeed(held, CustomBlockRegistry.standInTool(standIn), 0),
                CustomBlockRegistry.standInHardness(standIn), true);
        int ticks = BreakTimeCalculator.ticks(standInProgress * factor);
        Map<String, Integer> drops = new TreeMap<>();
        long xp = 0;
        Random random = new Random();
        for (int i = 0; i < rolls; i++) {
            BlockMining.Result result = BlockMining.mine(profile, "the_sift:" + custom.id(), held, silk, fortune, random);
            result.drops().forEach(d -> drops.merge(d.item(), d.amount(), Integer::sum));
            xp += result.experience();
        }
        sender.sendPlainMessage("Block " + custom.id() + custom.properties() + " (" + custom.data().getAsString() + ")");
        sender.sendPlainMessage("  Werkzeug " + held + ", Behutsamkeit " + silk + ", Glück " + fortune
                + ", Faktor " + String.format(Locale.ROOT, "%.4f", factor)
                + ", Abbauzeit " + (ticks == Integer.MAX_VALUE ? "unzerstörbar" : ticks + " Ticks")
                + ", erntbar " + profile.canHarvest(held));
        sender.sendPlainMessage("  " + rolls + " Abbauvorgänge: Drops " + drops + ", XP gesamt " + xp);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Finds the nearest block of a custom block id in the Sift around the executor (or the Sift spawn),
     * loading/generating the chunks within {@code radius} chunks. For testers looking for ores.
     */
    private int find(CommandContext<CommandSourceStack> ctx, int radius) {
        var sender = ctx.getSource().getSender();
        String id = StringArgumentType.getString(ctx, "block");
        Set<String> states = plugin.blocks().statesOf(id);
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (sift == null || states.isEmpty()) {
            sender.sendRichMessage(sift == null ? "<red>Die Sift-Welt ist nicht geladen."
                    : "<red>Unbekannter Sift-Block: <white>" + id);
            return 0;
        }
        org.bukkit.Location origin = ctx.getSource().getLocation();
        if (origin == null || !SiftWorlds.isSift(origin.getWorld())) {
            origin = sift.getSpawnLocation();
        }
        Material material = plugin.blocks().defaultState(id).data().getMaterial();
        int cx = origin.getBlockX() >> 4;
        int cz = origin.getBlockZ() >> 4;
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                futures.add(sift.getChunkAtAsync(x, z));
            }
        }
        sender.sendRichMessage("<gray>Suche <white>" + id + "</white> in " + futures.size() + " Chunks ...");
        org.bukkit.Location center = origin;
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    List<ChunkSnapshot> snapshots = futures.stream().map(f -> f.join().getChunkSnapshot()).toList();
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        int[] best = null;
                        double bestDistance = Double.MAX_VALUE;
                        int found = 0;
                        for (ChunkSnapshot snapshot : snapshots) {
                            for (int x = 0; x < 16; x++) {
                                for (int z = 0; z < 16; z++) {
                                    for (int y = sift.getMinHeight(); y < sift.getMaxHeight(); y++) {
                                        if (snapshot.getBlockType(x, y, z) != material
                                                || !states.contains(snapshot.getBlockData(x, y, z).getAsString())) {
                                            continue;
                                        }
                                        found++;
                                        int bx = (snapshot.getX() << 4) + x;
                                        int bz = (snapshot.getZ() << 4) + z;
                                        double d = center.distanceSquared(new org.bukkit.Location(sift, bx, y, bz));
                                        if (d < bestDistance) {
                                            bestDistance = d;
                                            best = new int[] {bx, y, bz};
                                        }
                                    }
                                }
                            }
                        }
                        int[] result = best;
                        int total = found;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (result == null) {
                                sender.sendRichMessage("<yellow>Kein <white>" + id + "</white> gefunden.");
                                return;
                            }
                            String pos = result[0] + " " + result[1] + " " + result[2];
                            sender.sendRichMessage("<gray>" + total + " Blöcke <white>" + id + "</white>, nächster bei "
                                    + "<click:run_command:'/execute in the_sift:the_sift run tp @s " + pos
                                    + "'><hover:show_text:'Teleport (im Zuschauermodus, der Block liegt im Gestein)'><aqua>" + pos + "</aqua></hover></click>");
                        });
                    });
                }));
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
                        Map<String, Integer> counts = count(plugin.blocks(), snapshots, sift.getMinHeight(), sift.getMaxHeight());
                        Bukkit.getScheduler().runTask(plugin, () -> report(sender, snapshots.size(), counts));
                    });
                }));
        sender.sendRichMessage("<gray>Scanne " + futures.size() + " Chunks der Sift ...");
        return Command.SINGLE_SUCCESS;
    }

    private static Map<String, Integer> count(CustomBlockRegistry blocks, List<ChunkSnapshot> snapshots, int minY, int maxY) {
        Map<String, Integer> counts = new HashMap<>();
        for (ChunkSnapshot snapshot : snapshots) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minY; y < maxY; y++) {
                        var data = snapshot.getBlockData(x, y, z);
                        if (SCANNED.contains(data.getMaterial())) {
                            var custom = blocks.of(data);
                            String name = custom != null ? "the_sift:" + custom.id()
                                    : data.getMaterial().getKey().toString();
                            counts.merge(name, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        return counts;
    }

    private static void report(org.bukkit.command.CommandSender sender, int chunks, Map<String, Integer> counts) {
        sender.sendRichMessage("<gray>Scan von <white>" + chunks + "</white> Chunks, Sift-Blöcke und Vanilla-Marker:");
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(40)
                .forEach(e -> sender.sendPlainMessage("  " + e.getValue() + "  " + e.getKey()));
    }
}
