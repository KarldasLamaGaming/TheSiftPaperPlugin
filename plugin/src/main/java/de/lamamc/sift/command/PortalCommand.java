package de.lamamc.sift.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.lamamc.sift.core.portal.PortalFrame;
import de.lamamc.sift.core.portal.PortalState;
import de.lamamc.sift.world.SiftWorlds;
import de.lamamc.sift.world.portal.PortalService;
import de.lamamc.sift.world.portal.PortalSurface;
import de.lamamc.sift.world.portal.SiftPortal;
import de.lamamc.sift.world.portal.SonorousConsoles;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;

/**
 * /sift portal: register a frame as portal (create), wake its consoles (unlock), open or close it, list and
 * inspect portals. Portals exist only where an admin or (step 7b) a structure registered them.
 */
final class PortalCommand {

    private final PortalService portals;
    private final SonorousConsoles consoles;
    private final int frameRadius;
    private final java.util.function.Function<World, de.lamamc.sift.core.portal.PortalSites> sites;

    PortalCommand(PortalService portals, SonorousConsoles consoles, int frameRadius,
                  java.util.function.Function<World, de.lamamc.sift.core.portal.PortalSites> sites) {
        this.portals = portals;
        this.consoles = consoles;
        this.frameRadius = frameRadius;
        this.sites = sites;
    }

    LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("portal")
                .then(Commands.literal("create")
                        .then(Commands.argument("pos", ArgumentTypes.blockPosition()).executes(this::create)))
                .then(byId("unlock", this::unlock))
                .then(byId("activate", this::activate))
                .then(byId("deactivate", this::deactivate))
                .then(byId("info", this::info))
                .then(Commands.literal("remove").then(idArgument().executes(ctx -> run(ctx, this::remove))))
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("locate").executes(this::locate));
    }

    /** "name" acts on the nearest portal (64 blocks), "name <id>" on that one. */
    private LiteralArgumentBuilder<CommandSourceStack> byId(String name, BiFunction<CommandSender, Target, Integer> action) {
        return Commands.literal(name)
                .executes(ctx -> nearest(ctx).map(t -> action.apply(ctx.getSource().getSender(), t)).orElseGet(() -> none(ctx)))
                .then(idArgument().executes(ctx -> run(ctx, action)));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> idArgument() {
        return Commands.argument("id", StringArgumentType.word()).suggests((ctx, builder) -> {
            portals.registry().all(world(ctx)).forEach(p -> builder.suggest(p.id()));
            return builder.buildFuture();
        });
    }

    private record Target(World world, SiftPortal portal) {
    }

    private int run(CommandContext<CommandSourceStack> ctx, BiFunction<CommandSender, Target, Integer> action) {
        World world = world(ctx);
        String id = StringArgumentType.getString(ctx, "id");
        Optional<SiftPortal> portal = portals.registry().get(world, id);
        if (portal.isEmpty()) {
            ctx.getSource().getSender().sendRichMessage("<red>Kein Portal „" + id + "“ in " + world.getKey() + ".");
            return 0;
        }
        return action.apply(ctx.getSource().getSender(), new Target(world, portal.get()));
    }

    private static World world(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getLocation().getWorld();
    }

    private Optional<Target> nearest(CommandContext<CommandSourceStack> ctx) {
        Location here = ctx.getSource().getLocation();
        return portals.registry().all(here.getWorld()).stream()
                .filter(p -> distance(p, here) <= 64)
                .min(Comparator.comparingDouble(p -> distance(p, here)))
                .map(p -> new Target(here.getWorld(), p));
    }

    private static double distance(SiftPortal portal, Location here) {
        double[] c = portal.frame().bottomCenter();
        return Math.sqrt(Math.pow(c[0] - here.getX(), 2) + Math.pow(c[1] - here.getY(), 2) + Math.pow(c[2] - here.getZ(), 2));
    }

    private static int none(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendRichMessage("<red>Kein Portal im Umkreis von 64 Blöcken. <gray>/sift portal list");
        return 0;
    }

    private int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        World world = world(ctx);
        if (SiftWorlds.isSift(world)) {
            sender.sendRichMessage("<red>In der Sift gibt es nur das Haupt-Portal als Ausgang.");
            return 0;
        }
        BlockPosition pos = ctx.getArgument("pos", BlockPositionResolver.class).resolve(ctx.getSource());
        Optional<PortalFrame> frame = scanAround(world, pos.blockX(), pos.blockY(), pos.blockZ());
        if (frame.isEmpty()) {
            sender.sendRichMessage("<red>Dort ist kein Rahmen aus Verstärktem Tiefenschiefer (3–23 Blöcke, Innenraum frei).");
            return 0;
        }
        PortalFrame f = frame.get();
        if (portals.registry().all(world).stream().anyMatch(p -> p.frame().equals(f))) {
            sender.sendRichMessage("<red>Dieser Rahmen ist schon ein Portal.");
            return 0;
        }
        List<int[]> found = consolesNear(world, f);
        boolean awake = !found.isEmpty() && found.stream().allMatch(c ->
                consoles.mode(world.getBlockAt(c[0], c[1], c[2])) == SonorousConsoles.Mode.NOTE);
        SiftPortal portal = new SiftPortal(portals.registry().newId(world, "portal"), SiftPortal.Kind.ENTRANCE, f,
                awake ? PortalState.UNLOCKED : PortalState.LOCKED, found);
        portals.registry().put(world, portal);
        sender.sendRichMessage("<green>Portal <white>" + portal.id() + "</white> angelegt: " + f.width() + " × " + f.height()
                + ", " + found.size() + " Konsolen, Zustand " + portal.state() + ".");
        return Command.SINGLE_SUCCESS;
    }

    /** The frame through this block or one of the frame blocks next to it (the scan starts low and at the sides). */
    private static Optional<PortalFrame> scanAround(World world, int x, int y, int z) {
        PortalFrame.Grid grid = new PortalFrame.Grid() {
            @Override
            public boolean isFrame(int bx, int by, int bz) {
                return world.getBlockAt(bx, by, bz).getType() == Material.REINFORCED_DEEPSLATE;
            }

            @Override
            public boolean isOpen(int bx, int by, int bz) {
                return PortalSurface.isOpenInterior(world.getBlockAt(bx, by, bz));
            }
        };
        for (int dy = 0; dy >= -24; dy--) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (grid.isFrame(x + dx, y + dy, z + dz)) {
                        Optional<PortalFrame> frame = PortalFrame.scan(grid, x + dx, y + dy, z + dz);
                        if (frame.isPresent()) {
                            return frame;
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Sonorous deepslate within the frame radius of the frame. */
    private List<int[]> consolesNear(World world, PortalFrame f) {
        int maxX = f.minX() + (f.axis() == PortalFrame.Axis.X ? f.width() - 1 : 0);
        int maxZ = f.minZ() + (f.axis() == PortalFrame.Axis.Z ? f.width() - 1 : 0);
        List<int[]> found = new ArrayList<>();
        for (int x = f.minX() - frameRadius; x <= maxX + frameRadius; x++) {
            for (int y = f.minY() - frameRadius; y <= f.minY() + f.height() - 1 + frameRadius; y++) {
                for (int z = f.minZ() - frameRadius; z <= maxZ + frameRadius; z++) {
                    if (y >= world.getMinHeight() && y < world.getMaxHeight()
                            && consoles.mode(world.getBlockAt(x, y, z)) != SonorousConsoles.Mode.NONE) {
                        found.add(new int[] {x, y, z});
                    }
                }
            }
        }
        return found;
    }

    private int unlock(CommandSender sender, Target target) {
        int count = consoles.unlock(target.world(), target.portal());
        sender.sendRichMessage("<green>Konsolen von <white>" + target.portal().id() + "</white> geweckt (" + count
                + "). Die Melodie öffnet das Portal jetzt.");
        return Command.SINGLE_SUCCESS;
    }

    private int activate(CommandSender sender, Target target) {
        if (target.portal().state() == PortalState.OPEN) {
            portals.surface().repair(target.world(), target.portal());
            sender.sendRichMessage("<gray>Portal <white>" + target.portal().id() + "</white> war schon offen; Fläche erneuert.");
            return Command.SINGLE_SUCCESS;
        }
        portals.open(target.world(), target.portal());
        sender.sendRichMessage("<green>Portal <white>" + target.portal().id() + "</white> geöffnet.");
        return Command.SINGLE_SUCCESS;
    }

    private int deactivate(CommandSender sender, Target target) {
        if (target.portal().kind() == SiftPortal.Kind.SIFT_EXIT) {
            sender.sendRichMessage("<red>Das Haupt-Portal der Sift bleibt immer offen (sonst kommt niemand zurück).");
            return 0;
        }
        portals.close(target.world(), target.portal());
        sender.sendRichMessage("<green>Portal <white>" + target.portal().id() + "</white> geschlossen.");
        return Command.SINGLE_SUCCESS;
    }

    private int remove(CommandSender sender, Target target) {
        if (target.portal().kind() == SiftPortal.Kind.SIFT_EXIT) {
            sender.sendRichMessage("<red>Das Haupt-Portal der Sift lässt sich nicht entfernen.");
            return 0;
        }
        if (target.portal().state() == PortalState.OPEN) {
            portals.surface().close(target.world(), target.portal());
        }
        portals.registry().remove(target.world(), target.portal().id());
        sender.sendRichMessage("<green>Portal <white>" + target.portal().id() + "</white> entfernt (Blöcke bleiben stehen).");
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandSender sender, Target target) {
        SiftPortal p = target.portal();
        double[] c = p.frame().bottomCenter();
        long awake = p.consoles().stream()
                .filter(k -> consoles.mode(target.world().getBlockAt(k[0], k[1], k[2])) == SonorousConsoles.Mode.NOTE).count();
        sender.sendRichMessage("<gray>Portal <white>" + p.id() + "</white> in " + target.world().getKey() + ": "
                + (p.kind() == SiftPortal.Kind.SIFT_EXIT ? "Ausgang der Sift" : "Eingang in die Sift") + ", Zustand <white>"
                + p.state() + "</white>, Rahmen " + p.frame().width() + " × " + p.frame().height() + " (" + p.frame().axis()
                + ") bei " + (int) Math.floor(c[0]) + " " + (int) c[1] + " " + (int) Math.floor(c[2]) + ", Konsolen "
                + awake + "/" + p.consoles().size() + " wach.");
        return Command.SINGLE_SUCCESS;
    }

    /** The nearest shrine site of this world (computed, also where nothing is generated yet); the main portal in the Sift. */
    private int locate(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Location here = ctx.getSource().getLocation();
        World world = here.getWorld();
        Location target;
        if (SiftWorlds.isSift(world)) {
            target = de.lamamc.sift.world.MainPortalBuilder.anchor(world);
        } else {
            de.lamamc.sift.core.portal.PortalSites worldSites = sites.apply(world);
            var site = worldSites == null ? null : worldSites.nearest(world.getSeed(), here.getX(), here.getZ(), 64);
            // the height is known once the shrine is generated; before that the teleport keeps the current height
            Optional<SiftPortal> built = site == null ? Optional.empty()
                    : portals.registry().get(world, "shrine-" + site.chunkX() + "-" + site.chunkZ());
            target = site == null ? null : built.map(p -> {
                double[] c = p.frame().bottomCenter();
                return new Location(world, c[0], c[1], c[2] - 3);
            }).orElse(new Location(world, site.blockX(), Double.NaN, site.blockZ()));
        }
        if (target == null) {
            sender.sendRichMessage("<red>In dieser Welt gibt es keine Portal-Schreine.");
            return 0;
        }
        int distance = (int) Math.round(Math.hypot(target.getX() - here.getX(), target.getZ() - here.getZ()));
        String y = Double.isNaN(target.getY()) ? "~" : String.valueOf(target.getBlockY());
        String pos = target.getBlockX() + " " + y + " " + target.getBlockZ();
        sender.sendRichMessage("<gray>Nächstes Portal: <click:run_command:'/execute in " + world.getKey() + " run tp @s " + pos
                + "'><aqua>[" + pos + "]</aqua></click> <gray>(" + distance + " Blöcke" + (y.equals("~") ? ", noch nicht generiert" : "")
                + ")");
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (SiftPortal p : portals.registry().all(world)) {
                double[] c = p.frame().bottomCenter();
                String pos = (int) Math.floor(c[0]) + " " + (int) c[1] + " " + (int) Math.floor(c[2]);
                sender.sendRichMessage("  <white>" + p.id() + "</white> <gray>" + world.getKey() + " " + p.state() + "</gray> "
                        + "<click:run_command:'/execute in " + world.getKey() + " run tp @s " + pos + "'><aqua>[" + pos + "]</aqua></click>");
                count++;
            }
        }
        sender.sendRichMessage("<gray>" + count + " Portale.");
        return Command.SINGLE_SUCCESS;
    }
}
