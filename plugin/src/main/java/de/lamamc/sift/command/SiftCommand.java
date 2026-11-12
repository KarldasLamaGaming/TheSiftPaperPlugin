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

    private MobCommand mobCommand;
    private AccessCommand accessCommand;

    private AccessCommand accessCommand() {
        if (accessCommand == null) {
            accessCommand = new AccessCommand(plugin, plugin.gate());
        }
        return accessCommand;
    }

    private MobCommand mobCommand() {
        if (mobCommand == null) {
            mobCommand = new MobCommand(plugin.mobs(), plugin.mobSpawner(), plugin.messages());
        }
        return mobCommand;
    }

    public void register(Commands commands) {
        LiteralCommandNode<CommandSourceStack> root = Commands.literal("sift")
                .requires(source -> admin(source) || items(source) || source.getSender().hasPermission("thesift.admin.reset"))
                .then(Commands.literal("admin").requires(source -> source.getSender().hasPermission("thesift.admin.reset"))
                        .then(Commands.literal("reset-players").executes(ctx -> resetPlayers(ctx, false))
                                .then(Commands.literal("confirm").executes(ctx -> resetPlayers(ctx, true)))))
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
                .then(mobCommand().spawnNode().requires(SiftCommand::admin))
                .then(accessCommand().node().requires(SiftCommand::admin))
                .then(new RiftCommand(plugin.rifts()).node().requires(source -> admin(source) && plugin.rifts() != null))
                .then(new TempleCommand(plugin).node().requires(SiftCommand::admin))
                .then(new SiftiteCommand(plugin.siftiteKeeper()).node().requires(source -> admin(source) && plugin.siftiteKeeper() != null))
                .then(accessCommand().openShortcut().requires(SiftCommand::admin))
                .then(accessCommand().closeShortcut().requires(SiftCommand::admin))
                .then(Commands.literal("debug").requires(SiftCommand::admin)
                        .then(mobCommand().debugNode())
                        .then(Commands.literal("infect").executes(ctx -> {
                            var infection = plugin.snifferInfection();
                            var sniffer = infection == null ? null : infection.infectNearest(ctx.getSource().getLocation());
                            ctx.getSource().getSender().sendRichMessage(sniffer == null
                                    ? "<red>Kein normaler Schnüffler in 16 Blöcken."
                                    : "<green>Schnüffler infiziert; er wird in " + infection.conversionSeconds() + " Sekunden zum Dunklen Schnüffler"
                                            + (infection.onlyInSift() ? " (nur in der Sift)." : "."));
                            return sniffer == null ? 0 : 1;
                        }))
                        .then(Commands.literal("singer").executes(ctx -> {
                            var summoner = plugin.singerSummoner();
                            var singer = summoner == null ? null : summoner.summon(ctx.getSource().getLocation());
                            ctx.getSource().getSender().sendRichMessage(singer == null
                                    ? "<red>Kein schlafendes Portal in 40 Blöcken (oder dort singt schon einer)."
                                    : "<green>Der Singer erscheint im Portal.");
                            return singer == null ? 0 : 1;
                        }))
                        .then(Commands.literal("item")
                                .then(Commands.argument("item", StringArgumentType.word()).executes(this::debugItem)))
                        .then(breakNode())
                        .then(Commands.literal("menu").executes(this::debugMenu))
                        .then(Commands.literal("selftest").executes(this::debugSelfTest))
                        .then(Commands.literal("worldgen").executes(this::debugWorldgen))
                        .then(Commands.literal("ichor").executes(this::debugIchor)
                                .then(Commands.literal("marks").executes(this::debugIchorMarks))
                                .then(Commands.literal("scan").executes(ctx -> debugIchorScan(ctx, 4))
                                        .then(Commands.argument("chunks", IntegerArgumentType.integer(0, 8))
                                                .executes(ctx -> debugIchorScan(ctx, IntegerArgumentType.getInteger(ctx, "chunks"))))))
                        .then(Commands.literal("enchanter").executes(this::debugEnchanter))
                        .then(Commands.literal("allays").executes(this::debugAllays))
                        .then(Commands.literal("weather").executes(this::debugWeather))
                        .then(Commands.literal("intro").executes(ctx -> debugIntro(ctx, null))
                                .then(Commands.literal("reset").executes(ctx -> debugIntro(ctx, "reset")))
                                .then(Commands.literal("play").executes(ctx -> debugIntro(ctx, "play"))))
                        .then(Commands.literal("canyon").then(Commands.literal("build").executes(this::debugCanyonBuild)))
                        .then(Commands.literal("golems").executes(ctx -> {
                            var sender = ctx.getSource().getSender();
                            var spawner = plugin.golemSpawner();
                            var light = plugin.golemLight();
                            sender.sendRichMessage("<gray>" + (spawner == null ? "Echo Golems aus" : spawner.describe())
                                    + (light == null ? "" : "; Lichtblöcke " + (light.enabled() ? "an" : "aus") + ", aktiv "
                                    + light.active() + ", entfernt " + light.cleanedUp()));
                            if (light != null && ctx.getSource().getLocation().getWorld() != null) {
                                int[] scan = light.scan(ctx.getSource().getLocation(), 48);
                                sender.sendRichMessage("<gray>Lichtblöcke (Stufe der Golems) in 48 Blöcken: " + scan[0]
                                        + ", davon von tragenden Golems " + scan[1] + ", übrig " + (scan[0] - scan[1]));
                            }
                            if (spawner != null) {
                                spawner.golems().forEach(line -> sender.sendPlainMessage("  " + line));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
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
                        .then(Commands.literal("mushrooms")
                                .then(Commands.argument("dimension", StringArgumentType.string())
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 16))
                                                .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                                        .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                                .executes(this::debugMushrooms))))))
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

    /**
     * /sift admin reset-players [confirm]: go-live reset of all players (intro flag, siftite backups and every other
     * the_sift: entry in the player, the_sift: advancements, Sift items in inventory, ender chest and shulker boxes).
     * Only with admin.reset-players.enabled: true; the first call explains, "confirm" within 30 s carries it out.
     */
    private int resetPlayers(CommandContext<CommandSourceStack> ctx, boolean confirm) {
        var sender = ctx.getSource().getSender();
        var reset = plugin.playerReset();
        if (reset == null || !reset.enabled()) {
            sender.sendRichMessage("<red>Ausgeschaltet.</red> <gray>Erst <white>admin.reset-players.enabled: true</white> in der "
                    + "config.yml setzen und neu starten (Schutz gegen versehentliches Zurücksetzen).");
            return 0;
        }
        if (!confirm) {
            reset.request();
            sender.sendRichMessage("<gold>Achtung:</gold> <gray>Das setzt <white>alle Spieler</white> für die Sift auf null: Intro, "
                    + "Siftit-Sicherungen, Rückkehrpunkte und alle anderen Sift-Daten im Spieler, alle Sift-Advancements, alle "
                    + "Sift-Gegenstände in Inventar, Endertruhe und Shulkerkisten darin. Online-Spieler sofort, alle anderen beim "
                    + "nächsten Login. Nicht rückgängig zu machen (außer mit dem Backup von <white>playerdata</white>). Bisherige "
                    + "Resets: <white>" + reset.serverReset() + "</white>.</gray> "
                    + "<click:suggest_command:'/sift admin reset-players confirm'><red>[Innerhalb von 30 s bestätigen]</red></click>");
            return Command.SINGLE_SUCCESS;
        }
        if (!reset.confirm()) {
            sender.sendRichMessage("<red>Keine Anfrage in den letzten 30 s.</red> <gray>Zuerst <white>/sift admin reset-players</white>.");
            return 0;
        }
        int online = reset.resetOnline();
        sender.sendRichMessage("<green>Reset " + reset.serverReset() + " ausgelöst:</green> <gray><white>" + online
                + "</white> Spieler online zurückgesetzt, alle anderen beim nächsten Login (Details im Server-Log).");
        plugin.getLogger().warning("Sift player reset " + reset.serverReset() + " ordered by " + sender.getName());
        return Command.SINGLE_SUCCESS;
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
    /**
     * Mushroom blocks of any world (generated if needed): which states occur and whether any of them is a Sift state.
     * Shows that vanilla huge mushrooms and the Sift's repurposed states do not collide (alpha.41.2).
     */
    private int debugMushrooms(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        World world = Bukkit.getWorld(org.bukkit.NamespacedKey.fromString(StringArgumentType.getString(ctx, "dimension")));
        if (world == null) {
            sender.sendRichMessage("<red>Unbekannte Welt (z. B. \"minecraft:overworld\").");
            return 0;
        }
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        int cx = IntegerArgumentType.getInteger(ctx, "chunkX");
        int cz = IntegerArgumentType.getInteger(ctx, "chunkZ");
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                futures.add(world.getChunkAtAsync(x, z));
            }
        }
        sender.sendRichMessage("<gray>Scanne " + futures.size() + " Chunks nach Pilzblöcken ...");
        java.util.Set<Material> mushrooms = java.util.Set.of(Material.RED_MUSHROOM_BLOCK, Material.BROWN_MUSHROOM_BLOCK,
                Material.MUSHROOM_STEM);
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    List<ChunkSnapshot> snapshots = futures.stream().map(f -> f.join().getChunkSnapshot()).toList();
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        Map<String, Integer> states = new java.util.TreeMap<>();
                        for (ChunkSnapshot snapshot : snapshots) {
                            for (int x = 0; x < 16; x++) {
                                for (int z = 0; z < 16; z++) {
                                    for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                                        if (mushrooms.contains(snapshot.getBlockType(x, y, z))) {
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
                                var custom = plugin.blocks().of(Bukkit.createBlockData(e.getKey()));
                                if (custom != null) {
                                    reserved += e.getValue();
                                }
                                sender.sendPlainMessage("  " + e.getValue() + " x " + e.getKey()
                                        + (custom != null ? "  <- SIFT: " + custom.id() : ""));
                            }
                            sender.sendPlainMessage("Pilzblöcke: " + total + " in " + states.size() + " Zuständen, davon in "
                                    + "Sift-Zuständen: " + reserved);
                        });
                    });
                }));
        return Command.SINGLE_SUCCESS;
    }

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
        long candidates = s.canyonCandidates.get();
        sender.sendRichMessage(String.format(java.util.Locale.ROOT,
                "<gray>Seelenschluchten: <white>%d</white> von <white>%d</white> Kandidaten gebaut%s, Zeit je Kandidat "
                        + "<white>%.1f ms</white> im Mittel, <white>%.1f ms</white> höchstens",
                s.canyons.get(), candidates, s.canyonRejects.isEmpty() ? "" : " (nicht gebaut: " + s.canyonRejects + ")",
                candidates == 0 ? 0.0 : s.canyonNanos.get() / 1e6 / candidates, s.canyonMaxNanos.get() / 1e6));
        if (populator.landforms() != null) {
            var l = populator.landforms().stats();
            long mc = l.monolithCandidates.get();
            long ac = l.archCandidates.get();
            sender.sendRichMessage(String.format(java.util.Locale.ROOT,
                    "<gray>Monolithen: <white>%d</white> üppig + <white>%d</white> kahl (+ <white>%d</white> Nebensäulen) von "
                            + "<white>%d</white> Kandidaten%s, Zeit je Kandidat <white>%.1f ms</white> im Mittel, <white>%.1f ms</white> "
                            + "höchstens; Bögen: <white>%d</white> (davon <white>%d</white> Sculk) von <white>%d</white> Kandidaten%s, "
                            + "<white>%.1f ms</white> im Mittel, <white>%.1f ms</white> höchstens",
                    l.lush.get(), l.bare.get(), l.satellites.get(), mc,
                    l.monolithRejects.isEmpty() ? "" : " (nicht gebaut: " + l.monolithRejects + ")",
                    mc == 0 ? 0.0 : l.monolithNanos.get() / 1e6 / mc, l.monolithMaxNanos.get() / 1e6,
                    l.arches.get(), l.healthyArches.get(), ac, l.archRejects.isEmpty() ? "" : " (nicht gebaut: " + l.archRejects + ")",
                    ac == 0 ? 0.0 : l.archNanos.get() / 1e6 / ac, l.archMaxNanos.get() / 1e6));
            long sc = l.spikeCandidates.get();
            sender.sendRichMessage(String.format(java.util.Locale.ROOT,
                    "<gray>Trockene Sculk-Spitzen: <white>%d</white> von <white>%d</white> Kandidaten%s, <white>%.1f ms</white> im Mittel, "
                            + "<white>%.1f ms</white> höchstens",
                    l.spikes.get(), sc, l.spikeRejects.isEmpty() ? "" : " (nicht gebaut: " + l.spikeRejects + ")",
                    sc == 0 ? 0.0 : l.spikeNanos.get() / 1e6 / sc, l.spikeMaxNanos.get() / 1e6));
        }
        if (populator.snifferCaves() != null) {
            var c = populator.snifferCaves().stats();
            long cc = c.candidates.get();
            sender.sendRichMessage(String.format(java.util.Locale.ROOT,
                    "<gray>Schnüffler-Höhlen: <white>%d</white> von <white>%d</white> Kandidaten%s, Zeit je Kandidat "
                            + "<white>%.1f ms</white> im Mittel, <white>%.1f ms</white> höchstens; Eier <white>%d</white>, Pflanzen "
                            + "<white>%d</white>; Schnüffler erzeugt <white>%d</white> von <white>%d</white> (wartend <white>%d</white>, "
                            + "aufgegeben <white>%d</white>); Pflanzenflecken <white>%d</white>",
                    c.caves.get(), cc, c.rejects.isEmpty() ? "" : " (nicht gebaut: " + c.rejects + ")",
                    cc == 0 ? 0.0 : c.nanos.get() / 1e6 / cc, c.maxNanos.get() / 1e6, c.eggs.get(), c.plants.get(),
                    c.sniffersSpawned.get(), c.sniffersQueued.get(), populator.snifferCaves().pendingSniffers(), c.sniffersGivenUp.get(),
                    c.plantPatches.get()));
        }
        if (populator.topLayer() != null) {
            var t = populator.topLayer();
            sender.sendRichMessage(String.format(java.util.Locale.ROOT,
                    "<gray>Oberste Schicht: Bewuchs aufgeräumt <white>%d</white>, Pflanzen über Ichor entfernt <white>%d</white>, "
                            + "Lavablöcke <white>%d</white>; <white>%.2f ms</white> je Chunk (davon Lavaboden <white>%.2f ms</white>)",
                    t.growth.get(), t.plants.get(), t.lava.get(), s.chunks.get() == 0 ? 0.0 : t.nanos.get() / 1e6 / s.chunks.get(),
                    s.chunks.get() == 0 ? 0.0 : t.lavaNanos.get() / 1e6 / s.chunks.get()));
        }
        if (populator.snow() != null) {
            var snow = populator.snow();
            sender.sendRichMessage(String.format(java.util.Locale.ROOT,
                    "<gray>Ichor-Schnee: <white>%d</white> Schichten in <white>%d</white> Chunks, <white>%.2f ms</white> je Chunk",
                    snow.layers.get(), snow.chunks.get(), s.chunks.get() == 0 ? 0.0 : snow.nanos.get() / 1e6 / s.chunks.get()));
        }
        if (populator.waters() != null) {
            var w = populator.waters().stats();
            long lakeCandidates = w.lakeCandidates.get();
            long springChunks = w.springChunks.get();
            sender.sendRichMessage(String.format(java.util.Locale.ROOT,
                    "<gray>Ichor-Seen: <white>%d</white> von <white>%d</white> Kandidaten gebaut %s%s, Zeit je Kandidat "
                            + "<white>%.1f ms</white> im Mittel, <white>%.1f ms</white> höchstens; Quellen: <white>%d</white> "
                            + "(<white>%.2f ms</white> je Chunk); Lichtblöcke <white>%d</white>, wieder entfernt <white>%d</white>; Weideninseln <white>%d</white>",
                    w.lakes.get(), lakeCandidates, w.lakeVariants, w.lakeRejects.isEmpty() ? "" : " (nicht gebaut: " + w.lakeRejects + ")",
                    lakeCandidates == 0 ? 0.0 : w.lakeNanos.get() / 1e6 / lakeCandidates, w.lakeMaxNanos.get() / 1e6,
                    w.springs.get(), springChunks == 0 ? 0.0 : w.springNanos.get() / 1e6 / springChunks,
                    w.lights.get(), w.lightsRemoved.get(), w.islands.get()));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** /sift debug weather: weather of the main world and of the Sift, snow and ice counters (alpha.32). */
    private int debugWeather(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        World main = Bukkit.getWorlds().get(0);
        for (World w : sift == null ? java.util.List.of(main) : java.util.List.of(main, sift)) {
            sender.sendRichMessage("<gray>" + w.getKey() + ": " + (w.isThundering() ? "<white>Gewitter</white>" : w.hasStorm()
                    ? "<white>Regen</white>" : "<white>klar</white>") + ", noch <white>" + w.getWeatherDuration() / 20 + " s</white>, "
                    + "klar für <white>" + w.getClearWeatherDuration() / 20 + " s</white>");
        }
        var weather = plugin.weather();
        if (weather != null) {
            sender.sendRichMessage("<gray>Ichor-Schneefall " + (weather.snowfall() ? "<green>an</green>" : "<red>aus</red>")
                    + " (höchstens <white>" + weather.maxLayers() + "</white> Schichten, Ichor friert " + (weather.ichorFreezes() ? "" : "nicht ")
                    + "zu); seit dem Start Schnee gebildet <white>" + weather.snowFormed.get() + "</white>, abgelehnt <white>"
                    + weather.snowRefused.get() + "</white>" + (weather.refusals.isEmpty() ? "" : " " + weather.refusals)
                    + ", Eis abgelehnt <white>" + weather.iceRefused.get() + "</white>");
        }
        return Command.SINGLE_SUCCESS;
    }

    /** /sift debug allays: allays in the Sift, the spawner's counters and the nearest allays with teleport (alpha.31). */
    private int debugAllays(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        var spawner = plugin.allays();
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (sift == null || spawner == null) {
            sender.sendRichMessage("<red>Die Sift-Welt ist nicht geladen.");
            return 0;
        }
        var all = sift.getEntitiesByClass(org.bukkit.entity.Allay.class);
        sender.sendRichMessage("<gray>Allays: Spawner " + (spawner.enabled() ? "<green>an</green>" : "<red>aus</red>") + ", seit dem Start <white>"
                + spawner.groups() + "</white> Gruppen mit <white>" + spawner.allays() + "</white> Allays; jetzt geladen in der Sift <white>"
                + all.size() + "</white>. Biome: <white>" + spawner.profiles().keySet() + "</white>");
        var at = ctx.getSource().getLocation();
        boolean inSift = SiftWorlds.isSift(at.getWorld());
        all.stream().sorted(java.util.Comparator.comparingDouble(a -> inSift ? a.getLocation().distanceSquared(at) : 0)).limit(5).forEach(a -> {
            var l = a.getLocation();
            sender.sendRichMessage(String.format(java.util.Locale.ROOT,
                    "  <gray>Allay bei %d %d %d%s</gray> <click:run_command:'/execute in the_sift:the_sift run tp @s %d %d %d'>"
                            + "<aqua>[Teleport]</aqua></click>",
                    l.getBlockX(), l.getBlockY(), l.getBlockZ(), inSift ? ", " + Math.round(l.distance(at)) + " Blöcke" : "",
                    l.getBlockX(), l.getBlockY(), l.getBlockZ()));
        });
        return Command.SINGLE_SUCCESS;
    }

    /** /sift debug intro [reset|play]: whether the sender heard the intro sound of the Sift (alpha.29). */
    private int debugIntro(CommandContext<CommandSourceStack> ctx, String action) {
        var sender = ctx.getSource().getSender();
        var intro = plugin.intro();
        if (!(ctx.getSource().getExecutor() instanceof org.bukkit.entity.Player player)) {
            sender.sendRichMessage("<red>Nur für Spieler.");
            return 0;
        }
        if ("reset".equals(action)) {
            de.lamamc.sift.world.atmosphere.SiftIntro.reset(player);
            sender.sendRichMessage("<green>Intro vergessen</green><gray>: Es spielt beim nächsten Aufenthalt in der Sift erneut"
                    + (intro.enabled() ? "." : " (atmosphere.intro-sound.enabled ist aus)."));
        } else if ("play".equals(action)) {
            intro.play(player);
            sender.sendRichMessage("<green>Intro abgespielt</green> <gray>(" + de.lamamc.sift.world.atmosphere.SiftIntro.SOUND + ").");
        } else {
            sender.sendRichMessage("<gray>Intro-Klang: " + (intro.enabled() ? "<green>an</green>" : "<red>aus</red>") + ", für dich "
                    + (de.lamamc.sift.world.atmosphere.SiftIntro.hasPlayed(player) ? "<white>schon gespielt</white>" : "<white>noch nicht gespielt</white>")
                    + ", seit dem Start <white>" + intro.playedSinceStart() + "</white>× gespielt. "
                    + "<click:run_command:'/sift debug intro reset'><aqua>[zurücksetzen]</aqua></click> "
                    + "<click:run_command:'/sift debug intro play'><aqua>[jetzt abspielen]</aqua></click>");
        }
        return Command.SINGLE_SUCCESS;
    }

    /** /sift debug ichor: the nearest generated ichor lakes and springs, with teleport (alpha.25). */
    private int debugIchor(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        var populator = plugin.siftPopulator();
        if (populator == null || populator.waters() == null) {
            sender.sendRichMessage("<red>Die Sift-Weltgenerierung ist nicht aktiv.");
            return 0;
        }
        var waters = populator.waters();
        var at = ctx.getSource().getLocation();
        boolean inSift = SiftWorlds.isSift(at.getWorld());
        double x = inSift ? at.getX() : 0;
        double z = inSift ? at.getZ() : 0;
        sender.sendRichMessage("<gold>Ichor in der Sift: <white>" + waters.lakeCount() + "</white> Seen, <white>" + waters.springCount()
                + "</white> Quellen bekannt (nur seit alpha.25 generierte Chunks), nächste " + (inSift ? "zu dir" : "zu 0/0") + ":");
        for (boolean lakes : new boolean[] {true, false}) {
            for (var p : waters.nearest(lakes, x, z, 5)) {
                int distance = (int) Math.sqrt(p.horizontalDistanceSquared(x, z));
                String tp = "/execute in the_sift:the_sift run tp @s " + p.x() + " " + (p.y() + (lakes ? 2 : 1)) + " " + p.z();
                sender.sendRichMessage("<gray>" + (lakes ? "See" : "Quelle") + " bei <white>" + p.x() + " " + p.y() + " " + p.z()
                        + "</white> (" + distance + " Blöcke) <click:run_command:'" + tp + "'><aqua>[Teleport]</aqua></click>");
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    /** /sift debug enchanter (alpha.40): a Sculk Enchanter at the position (own block, or enchanting table in table style). */
    private int debugEnchanter(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        var enchanters = plugin.enchanters();
        var block = ctx.getSource().getLocation().getBlock();
        if (enchanters == null) {
            sender.sendRichMessage("<red>Der Sculk Enchanter ist aus (phase4.enchanter.enabled).");
            return 0;
        }
        boolean created = enchanters.create(block);
        sender.sendRichMessage(created ? "<green>Sculk Enchanter gesetzt (" + block.getX() + " " + block.getY() + " " + block.getZ() + ", "
                + (enchanters.blockStyle() ? "eigener Block" : "Zaubertisch") + ")." : "<gray>Das ist schon ein Sculk Enchanter.");
        return Command.SINGLE_SUCCESS;
    }

    /**
     * /sift debug ichor marks (alpha.38): the ichor identity marks per world, the timing of the flow handler and what
     * the water at the sender's position is.
     */
    private int debugIchorMarks(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        var marks = plugin.fluidMarks();
        if (marks == null) {
            sender.sendRichMessage("<red>Ichor-Identität ist aus (ichor.identity.enabled).");
            return 0;
        }
        for (var world : org.bukkit.Bukkit.getWorlds()) {
            if (marks.count(world) > 0 || SiftWorlds.isSift(world)) {
                sender.sendRichMessage("<gray>" + world.getKey() + ": <white>" + marks.count(world) + "</white> Markierungen in <white>"
                        + marks.chunkCount(world) + "</white> geladenen Chunks");
            }
        }
        var s = plugin.fluidIdentity().stats();
        long flows = s.flows.get();
        sender.sendRichMessage(String.format(java.util.Locale.ROOT,
                "<gray>Fließen (BlockFromToEvent): <white>%d</white> Ereignisse, im Mittel <white>%.2f µs</white>, höchstens "
                        + "<white>%.1f µs</white>; geerbt %d, entfernt %d; Eimer %d, Werfer %d, Kolben %d",
                flows, flows == 0 ? 0 : s.flowNanos.get() / 1000.0 / flows, s.flowMaxNanos.get() / 1000.0,
                s.inherited.get(), s.cleared.get(), s.buckets.get(), s.dispensers.get(), s.moved.get()));
        var block = ctx.getSource().getLocation().getBlock();
        sender.sendRichMessage("<gray>Hier (" + block.getX() + " " + block.getY() + " " + block.getZ() + ", " + block.getType().getKey().getKey()
                + "): " + (de.lamamc.sift.world.ichor.FluidMarks.holdsWater(block)
                ? (marks.ichor(block) ? "<light_purple>Ichor" : "<aqua>Wasser") + "<gray>" + (marks.marked(block) ? " (Ausnahme, markiert)" : " (Standard)")
                : "kein Wasser" + (marks.marked(block) ? " (Markierung bleibt, z. B. Eis)" : "")));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * /sift debug ichor scan [chunks]: counts in the loaded chunks around the sender the waterlogged light blocks
     * (hidden ichor sources, generated before alpha.36) and the dry glow lights above the ichor (alpha.36).
     */
    private int debugIchorScan(CommandContext<CommandSourceStack> ctx, int radius) {
        var sender = ctx.getSource().getSender();
        var at = ctx.getSource().getLocation();
        if (!SiftWorlds.isSift(at.getWorld())) {
            sender.sendRichMessage("<red>Nur in der Sift.");
            return 0;
        }
        var world = at.getWorld();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;
        int chunks = 0;
        int hidden = 0;
        int glow = 0;
        int[] first = null;
        long start = System.nanoTime();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                if (!world.isChunkLoaded(x, z)) {
                    continue;
                }
                chunks++;
                var snapshot = world.getChunkAt(x, z).getChunkSnapshot(false, false, false);
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    for (int bx = 0; bx < 16; bx++) {
                        for (int bz = 0; bz < 16; bz++) {
                            if (snapshot.getBlockType(bx, y, bz) != org.bukkit.Material.LIGHT) {
                                continue;
                            }
                            var light = (org.bukkit.block.data.type.Light) snapshot.getBlockData(bx, y, bz);
                            if (light.isWaterlogged()) {
                                hidden++;
                                if (first == null) {
                                    first = new int[] {(x << 4) + bx, y, (z << 4) + bz};
                                }
                            } else if (light.getLevel() == de.lamamc.sift.world.gen.IchorWaters.LIGHT_LEVEL) {
                                glow++;
                            }
                        }
                    }
                }
            }
        }
        double ms = (System.nanoTime() - start) / 1e6;
        sender.sendRichMessage(String.format(java.util.Locale.ROOT,
                "<gold>Ichor-Scan</gold> <gray>(%d geladene Chunks, Radius %d, %.0f ms):</gray> <white>%d</white> <gray>wassergeloggte "
                        + "Lichtblöcke (versteckte Quellen, vor alpha.36 generiert),</gray> <white>%d</white> <gray>Leuchtlichter über dem Ichor.",
                chunks, radius, ms, hidden, glow));
        if (first != null) {
            sender.sendRichMessage("<gray>Erste versteckte Quelle: <click:run_command:'/execute in the_sift:the_sift run tp @s "
                    + first[0] + " " + (first[1] + 1) + " " + first[2] + "'><aqua>[" + first[0] + " " + first[1] + " " + first[2]
                    + "]</aqua></click>");
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Builds a soul canyon with the worldgen plan around the sender, on loaded terrain (for tests without new chunks). */
    private int debugCanyonBuild(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        var at = ctx.getSource().getLocation();
        if (!SiftWorlds.isSift(at.getWorld()) || plugin.siftPopulator() == null) {
            sender.sendRichMessage("<red>Schluchten lassen sich nur in der Sift bauen.");
            return 0;
        }
        long start = System.nanoTime();
        var placed = plugin.siftPopulator().canyons().buildNear(at.getWorld(), at.getBlockX(), at.getBlockZ(),
                new java.util.Random());
        var built = placed.built();
        double ms = (System.nanoTime() - start) / 1e6;
        if (built.outcome() != de.lamamc.sift.world.gen.SoulCanyons.Outcome.BUILT) {
            sender.sendRichMessage("<red>Im Umkreis von 32 Blöcken passt keine Schlucht (" + placed.tries() + " Stellen geprüft): "
                    + "Das Gelände ist zu steil, hat keinen Sift-Boden in 32 Blöcken unter der Oberfläche, Bäume, Ichor oder Bauten "
                    + "sind im Weg, oder die Chunks sind nicht geladen. Auf flachem, kahlem Sift-Boden versuchen.");
            return 0;
        }
        var plan = built.plan();
        var e = plan.entrance();
        sender.sendRichMessage(String.format(java.util.Locale.ROOT,
                "<green>Seelenschlucht gebaut</green> <gray>(Mitte %d %d, %d. Stelle, %d Spalten, %d Seelenblöcke, %.1f ms). Eingang</gray> "
                        + "<click:run_command:'/execute in the_sift:the_sift run tp @s %d %d %d'><hover:show_text:'Teleport'>"
                        + "<aqua>[%d %d %d]</aqua></hover></click><gray>; als „Golem ausstehend“ eingetragen.",
                placed.centerX(), placed.centerZ(), placed.tries(), plan.columns().size(), plan.souls().size(), ms,
                e.x(), e.y(), e.z(), e.x(), e.y(), e.z()));
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
        var canyonMarks = plugin.canyonLandmarks();
        if (canyonMarks != null) {
            var from = ctx.getSource().getLocation();
            double fromX = SiftWorlds.isSift(from.getWorld()) && center == null ? from.getX() : centerX;
            double fromZ = SiftWorlds.isSift(from.getWorld()) && center == null ? from.getZ() : centerZ;
            var canyon = canyonMarks.nearestCanyon(fromX, fromZ, 1_000_000);
            sender.sendRichMessage("<gray>Seelenschluchten bekannt: <white>" + canyonMarks.count(
                    de.lamamc.sift.world.gen.SoulCanyonLandmarks.Kind.CLUSTER) + "</white>, Seelenblöcke <white>"
                    + canyonMarks.count(de.lamamc.sift.world.gen.SoulCanyonLandmarks.Kind.SOUL_BLOCK)
                    + "</white>, Golem ausstehend <white>"
                    + canyonMarks.count(de.lamamc.sift.world.gen.SoulCanyonLandmarks.Kind.GOLEM_PENDING) + "</white>");
            if (canyon != null) {
                var e = canyon.entrance() != null ? canyon.entrance() : canyon.cluster();
                sender.sendRichMessage(String.format(java.util.Locale.ROOT,
                        "<gray>Nächste Schlucht: Seelen-Zentrum %d %d %d, %d Blöcke entfernt, %d Seelenblöcke, Eingang</gray> "
                                + "<click:run_command:'/execute in the_sift:the_sift run tp @s %d %d %d'><hover:show_text:'Teleport'>"
                                + "<aqua>[%d %d %d]</aqua></hover></click>",
                        canyon.cluster().x(), canyon.cluster().y(), canyon.cluster().z(),
                        Math.round(Math.sqrt(canyon.cluster().horizontalDistanceSquared(fromX, fromZ))), canyon.souls(),
                        e.x(), e.y(), e.z(), e.x(), e.y(), e.z()));
            }
            if (plugin.canyonParticles() != null) {
                sender.sendRichMessage("<gray>Seelen-Partikel: <white>" + plugin.canyonParticles().describe(from));
            }
        }
        var landforms = plugin.siftPopulator() == null ? null : plugin.siftPopulator().landforms();
        if (landforms != null) {
            var from = ctx.getSource().getLocation();
            double fromX = SiftWorlds.isSift(from.getWorld()) && center == null ? from.getX() : centerX;
            double fromZ = SiftWorlds.isSift(from.getWorld()) && center == null ? from.getZ() : centerZ;
            sender.sendRichMessage("<gray>Monolithen bekannt: <white>" + landforms.count(de.lamamc.sift.world.gen.Landforms.Kind.LUSH)
                    + "</white> üppig, <white>" + landforms.count(de.lamamc.sift.world.gen.Landforms.Kind.BARE)
                    + "</white> kahl; Bögen: <white>" + landforms.count(de.lamamc.sift.world.gen.Landforms.Kind.ARCH)
                    + "</white> (seit alpha.27); trockene Sculk-Spitzen: <white>"
                    + landforms.count(de.lamamc.sift.world.gen.Landforms.Kind.SPIKE)
                    + "</white> (seit alpha.30); Teleport auf die Spitze bzw. den Scheitel:");
            for (var kinds : java.util.List.of(java.util.List.of(de.lamamc.sift.world.gen.Landforms.Kind.LUSH,
                    de.lamamc.sift.world.gen.Landforms.Kind.BARE), java.util.List.of(de.lamamc.sift.world.gen.Landforms.Kind.ARCH),
                    java.util.List.of(de.lamamc.sift.world.gen.Landforms.Kind.SPIKE))) {
                for (var found : landforms.nearest(kinds, fromX, fromZ, 3)) {
                    var p = found.top();
                    sender.sendRichMessage(String.format(java.util.Locale.ROOT,
                            "  <gray>%s bei %d %d %d, %d Blöcke entfernt</gray> <click:run_command:'/execute in the_sift:the_sift run "
                                    + "tp @s %d %d %d'><hover:show_text:'Teleport'><aqua>[Teleport]</aqua></hover></click>",
                            found.kind().label, p.x(), p.y(), p.z(), Math.round(Math.sqrt(p.horizontalDistanceSquared(fromX, fromZ))),
                            p.x(), p.y() + 1, p.z()));
                }
            }
        }
        var snifferCaves = plugin.siftPopulator() == null ? null : plugin.siftPopulator().snifferCaves();
        if (snifferCaves != null) {
            var from = ctx.getSource().getLocation();
            double fromX = SiftWorlds.isSift(from.getWorld()) && center == null ? from.getX() : centerX;
            double fromZ = SiftWorlds.isSift(from.getWorld()) && center == null ? from.getZ() : centerZ;
            sender.sendRichMessage("<gray>Schnüffler-Höhlen bekannt: <white>" + snifferCaves.count()
                    + "</white> (nur seit alpha.28 generierte Chunks); Teleport ins Nest bzw. zum Eingang:");
            for (var found : snifferCaves.nearest(fromX, fromZ, 3)) {
                var n = found.nest();
                var e = found.entrance();
                sender.sendRichMessage(String.format(java.util.Locale.ROOT,
                        "  <gray>Höhle, Nest bei %d %d %d, %d Blöcke entfernt</gray> <click:run_command:'/execute in the_sift:the_sift run "
                                + "tp @s %d %d %d'><hover:show_text:'Teleport ins Nest'><aqua>[Nest]</aqua></hover></click> "
                                + "<click:run_command:'/execute in the_sift:the_sift run tp @s %d %d %d'><hover:show_text:'Teleport zum Eingang'>"
                                + "<aqua>[Eingang]</aqua></hover></click>",
                        n.x(), n.y(), n.z(), Math.round(Math.sqrt(n.horizontalDistanceSquared(fromX, fromZ))), n.x(), n.y(), n.z(),
                        e.x(), e.y() + 1, e.z()));
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
